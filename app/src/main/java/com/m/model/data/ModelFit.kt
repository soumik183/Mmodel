package com.m.model.data

enum class Fit { WORKS, SLOW, WONT_RUN, UNKNOWN }

data class FitResult(
    val fit: Fit,
    val estimatedRamBytes: Long?,
    val ratio: Float,
    val storageBlocked: Boolean = false,
)

data class GgufFile(
    val name: String,
    val bytes: Long,
)

data class GgufVariant(
    val key: String,
    val quant: String,
    val files: List<GgufFile>,
    val bytes: Long,
    val fit: FitResult,
    val sharded: Boolean = false,
) {
    val firstFile: String get() = files.firstOrNull()?.name.orEmpty()
    val fileCount: Int get() = files.size
}

data class ModelCard(
    val model: HfModel,
    val downloadBytes: Long?,
    val parameters: Long?,
    val ggufVariants: Int,
    val fit: FitResult,
    val variants: List<GgufVariant> = emptyList(),
) {
    val id: String get() = model.id
    val name: String get() = model.id.substringAfterLast('/').ifBlank { model.id }
    val author: String get() = model.author ?: model.id.substringBefore('/', "")
    val bestVariant: GgufVariant? get() = variants.minByOrNull { it.bytes }
}

object ModelFit {
    private const val MB = 1_000_000L
    const val STORAGE_RESERVE_BYTES = 300_000_000L
    private val AUXILIARY_GGUF = listOf(
        "mmproj",
        "projector",
        "lora",
        "imatrix",
        "importance",
        "mtp",
        "tokenizer",
        "vae",
        "clip",
        "encoder",
    )

    private val shardPattern =
        Regex("""^(.*)-(\d{5})-of-(\d{5})\.gguf$""", RegexOption.IGNORE_CASE)

    private val quantPattern = Regex(
        """(?:^|[._-])((?:I?Q\d(?:_[A-Z0-9]+)*|BF16|F16|F32|FP16|FP32))(?=$|[._-])""",
        RegexOption.IGNORE_CASE,
    )

    fun analyze(model: HfModel, device: DeviceSpecs): ModelCard {
        val variants = ggufVariants(model, device)
        val isGguf = variants.isNotEmpty()
        val best = variants.minByOrNull { it.bytes }
        val downloadBytes = best?.bytes ?: otherWeightBytes(model)

        val parameters = model.gguf?.total?.takeIf { it > 0 }
            ?: model.safetensors?.total?.takeIf { it > 0 }
            ?: model.safetensors?.parameters?.values?.sum()?.takeIf { it > 0 }

        val fit = best?.fit ?: fitFor(downloadBytes, device, isGguf = false)

        return ModelCard(
            model = model,
            downloadBytes = downloadBytes,
            parameters = parameters,
            ggufVariants = variants.size,
            fit = fit,
            variants = variants,
        )
    }

    /**
     * Fit for a whole variant, including the space the download needs.
     */
    fun fitFor(fileBytes: Long?, device: DeviceSpecs, isGguf: Boolean = true): FitResult {
        if (fileBytes == null || fileBytes <= 0L) {
            return FitResult(Fit.UNKNOWN, null, 0f)
        }

        val needsRam = estimateRamBytes(fileBytes, isGguf)
        val budget = device.inferenceBudgetBytes
        val storageBlocked = storageFits(fileBytes, device.freeStorageBytes) == false
        val ratio = if (budget > 0L) needsRam.toFloat() / budget else 0f
        val fit = if (storageBlocked) Fit.WONT_RUN else memoryFit(needsRam, budget)

        return FitResult(fit, needsRam, ratio, storageBlocked)
    }

    /** The memory part of the verdict, independent of download space. */
    fun memoryFit(estimatedRamBytes: Long?, budgetBytes: Long): Fit {
        if (estimatedRamBytes == null || estimatedRamBytes <= 0L) return Fit.UNKNOWN
        if (budgetBytes <= 0L) return Fit.WONT_RUN
        val ratio = estimatedRamBytes.toFloat() / budgetBytes
        return when {
            ratio <= 0.65f -> Fit.WORKS
            ratio <= 1f -> Fit.SLOW
            else -> Fit.WONT_RUN
        }
    }

    /** The storage part of the verdict, independent of memory. */
    fun storageFits(fileBytes: Long?, freeStorageBytes: Long): Boolean? {
        if (fileBytes == null || fileBytes <= 0L) return null
        return fileBytes + STORAGE_RESERVE_BYTES <= freeStorageBytes
    }

    /**
     * Memory-only fit, used after a model has already been downloaded.
     */
    fun ramFit(fileBytes: Long?, device: DeviceSpecs): FitResult {
        if (fileBytes == null || fileBytes <= 0L) {
            return FitResult(Fit.UNKNOWN, null, 0f)
        }

        val needsRam = estimateRamBytes(fileBytes, isGguf = true)
        val budget = device.inferenceBudgetBytes
        val ratio = if (budget > 0L) needsRam.toFloat() / budget else 0f
        val fit = memoryFit(needsRam, budget)
        return FitResult(fit, needsRam, ratio)
    }

    fun estimateRamBytes(fileBytes: Long, isGguf: Boolean = true): Long {
        val factor = if (isGguf) 1.15 else 1.2
        val overhead = if (isGguf) 600L * MB else 900L * MB
        return (fileBytes * factor).toLong() + overhead
    }

    fun quantLabel(fileName: String): String {
        val base = fileName
            .substringAfterLast('/')
            .removeSuffix(".gguf")
            .removeSuffix(".GGUF")
            .replace(shardPattern, "$1")
        val match = quantPattern.find(base) ?: return "GGUF"
        return match.groupValues[1].uppercase()
    }

    /**
     * One entry per downloadable quantization. Sharded GGUF files are summed
     * into one entry so we never mistake one shard for the whole model.
     */
    private fun ggufVariants(model: HfModel, device: DeviceSpecs): List<GgufVariant> {
        val groups = linkedMapOf<String, MutableList<HfSibling>>()
        val shardBases = mutableMapOf<String, String>()

        model.siblings.forEach { sibling ->
            val size = sibling.size?.takeIf { it > 0 } ?: return@forEach
            val name = sibling.rfilename
            if (!name.endsWith(".gguf", ignoreCase = true)) return@forEach

            val lowerName = name.lowercase()
            // Projectors, adapters, importance matrices and MTP helpers are not the model.
            if (AUXILIARY_GGUF.any { lowerName.contains(it) }) {
                return@forEach
            }

            val shard = shardPattern.matchEntire(name)
            val key = if (shard != null) {
                val base = shard.groupValues[1]
                shardBases[base.lowercase()] = base
                "shard:${base.lowercase()}"
            } else {
                "file:${name.lowercase()}"
            }
            groups.getOrPut(key) { mutableListOf() }.add(sibling)
        }

        return groups.map { (key, siblings) ->
            val sorted = siblings.sortedBy { it.rfilename.lowercase() }
            val files = sorted.map { GgufFile(it.rfilename, it.size ?: 0L) }
            val bytes = files.sumOf { it.bytes }
            val baseName = shardBases[key.removePrefix("shard:")] ?: files.first().name
            GgufVariant(
                key = key,
                quant = quantLabel(baseName),
                files = files,
                bytes = bytes,
                fit = fitFor(bytes, device),
                sharded = files.size > 1 || key.startsWith("shard:"),
            )
        }.sortedBy { it.bytes }
    }

    private fun otherWeightBytes(model: HfModel): Long? {
        fun sum(extension: String): Long = model.siblings
            .filter { it.rfilename.endsWith(extension, ignoreCase = true) }
            .mapNotNull { it.size?.takeIf { size -> size > 0 } }
            .sum()

        val safetensors = sum(".safetensors")
        if (safetensors > 0L) return safetensors

        val pytorch = sum(".bin")
        if (pytorch > 0L) return pytorch

        val onnx = sum(".onnx")
        if (onnx > 0L) return onnx

        return null
    }
}
