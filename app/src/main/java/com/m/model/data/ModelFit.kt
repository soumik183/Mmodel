package com.m.model.data

import androidx.compose.runtime.Immutable

enum class Fit { WORKS, SLOW, WONT_RUN, UNKNOWN }

@Immutable
data class FitResult(
    val fit: Fit,
    val estimatedRamBytes: Long?,
    val ratio: Float,
    val storageBlocked: Boolean = false,
    /** How much free RAM is missing right now, for the "close apps" hint. */
    val availableRamShortfallBytes: Long = 0L,
    /** Exact storage gap including the reserve, for the detail screen. */
    val storageShortfallBytes: Long = 0L,
)

@Immutable
data class GgufFile(
    val name: String,
    val bytes: Long,
)

@Immutable
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

@Immutable
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
    /** Smallest quant that can actually be loaded, single-file first. */
    val bestVariant: GgufVariant? get() = variants
        .filter { it.bytes > 0L }
        .minWithOrNull(compareBy({ it.sharded }, { it.bytes }))
}

object ModelFit {
    private const val MB = 1_000_000L
    const val STORAGE_RESERVE_BYTES = 300_000_000L

    /** Every model is loaded with this context, so fit assumes it. */
    const val CHAT_CONTEXT_TOKENS = 2_048

    private const val GGUF_WEIGHT_FACTOR = 1.15
    private const val OTHER_WEIGHT_FACTOR = 1.20
    private const val GGUF_RUNTIME_BASE = 350L * MB
    private const val OTHER_RUNTIME_BASE = 600L * MB
    private const val GGUF_KV_PER_K_TOKENS = 125L * MB
    private const val OTHER_KV_PER_K_TOKENS = 150L * MB

    /** Verdicts flip to "slow" below this share of currently free RAM. */
    private const val AVAILABLE_RAM_HEADROOM = 0.9f

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
        val best = variants
            .filter { it.bytes > 0L }
            .minWithOrNull(compareBy({ it.sharded }, { it.bytes }))
        val otherWeights = otherWeightBytes(model)
        val downloadBytes = best?.bytes ?: if (variants.isEmpty()) otherWeights else null

        val parameters = model.gguf?.total?.takeIf { it > 0 }
            ?: model.safetensors?.total?.takeIf { it > 0 }
            ?: model.safetensors?.parameters?.values?.sum()?.takeIf { it > 0 }

        val fit = best?.fit ?: if (variants.isEmpty() && otherWeights != null) {
            fitFor(otherWeights, device, isGguf = false)
        } else {
            FitResult(Fit.UNKNOWN, null, 0f)
        }

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
    fun fitFor(
        fileBytes: Long?,
        device: DeviceSpecs,
        isGguf: Boolean = true,
        contextTokens: Int = CHAT_CONTEXT_TOKENS,
    ): FitResult {
        if (fileBytes == null || fileBytes <= 0L) {
            return FitResult(Fit.UNKNOWN, null, 0f)
        }

        val needsRam = estimateRamBytes(fileBytes, isGguf, contextTokens)
        val budget = device.inferenceBudgetBytes
        val storageShortfall = storageShortfallBytes(fileBytes, device.freeStorageBytes)
        val storageBlocked = storageShortfall > 0L
        val ratio = if (budget > 0L) needsRam.toFloat() / budget else 0f
        val fit = if (storageBlocked) {
            Fit.WONT_RUN
        } else {
            memoryFit(needsRam, budget, device.availableRamBytes)
        }

        return FitResult(
            fit = fit,
            estimatedRamBytes = needsRam,
            ratio = ratio,
            storageBlocked = storageBlocked,
            availableRamShortfallBytes = availableRamShortfall(needsRam, device.availableRamBytes),
            storageShortfallBytes = storageShortfall,
        )
    }

    /** The memory part of the verdict, independent of download space. */
    fun memoryFit(
        estimatedRamBytes: Long?,
        budgetBytes: Long,
        availableRamBytes: Long = 0L,
    ): Fit {
        if (estimatedRamBytes == null || estimatedRamBytes <= 0L) return Fit.UNKNOWN
        if (budgetBytes <= 0L) return Fit.WONT_RUN
        val ratio = estimatedRamBytes.toFloat() / budgetBytes
        val byBudget = when {
            ratio <= 0.65f -> Fit.WORKS
            ratio <= 1f -> Fit.SLOW
            else -> Fit.WONT_RUN
        }
        // The budget is what Android could give us; free RAM right now is what
        // the model actually gets. Never upgrade, only warn when it is tight.
        val tightNow = byBudget == Fit.WORKS &&
            availableRamBytes > 0L &&
            estimatedRamBytes > availableRamBytes * AVAILABLE_RAM_HEADROOM
        return if (tightNow) Fit.SLOW else byBudget
    }

    /** The storage part of the verdict, independent of memory. */
    fun storageFits(fileBytes: Long?, freeStorageBytes: Long): Boolean? {
        if (fileBytes == null || fileBytes <= 0L) return null
        return storageShortfallBytes(fileBytes, freeStorageBytes) == 0L
    }

    /** Bytes still missing before the download plus the reserve fits. */
    fun storageShortfallBytes(fileBytes: Long?, freeStorageBytes: Long): Long {
        if (fileBytes == null || fileBytes <= 0L) return 0L
        return (fileBytes + STORAGE_RESERVE_BYTES - freeStorageBytes).coerceAtLeast(0L)
    }

    private fun availableRamShortfall(needsRam: Long, availableRamBytes: Long): Long =
        if (availableRamBytes <= 0L) 0L else (needsRam - availableRamBytes).coerceAtLeast(0L)

    /**
     * Memory-only fit, used after a model has already been downloaded.
     */
    fun ramFit(fileBytes: Long?, device: DeviceSpecs, contextTokens: Int = CHAT_CONTEXT_TOKENS): FitResult {
        if (fileBytes == null || fileBytes <= 0L) {
            return FitResult(Fit.UNKNOWN, null, 0f)
        }

        val needsRam = estimateRamBytes(fileBytes, isGguf = true, contextTokens = contextTokens)
        val budget = device.inferenceBudgetBytes
        val ratio = if (budget > 0L) needsRam.toFloat() / budget else 0f
        val fit = memoryFit(needsRam, budget, device.availableRamBytes)
        return FitResult(
            fit = fit,
            estimatedRamBytes = needsRam,
            ratio = ratio,
            availableRamShortfallBytes = availableRamShortfall(needsRam, device.availableRamBytes),
        )
    }

    /**
     * Weights plus runtime buffers plus a KV cache that grows with the context.
     * At the 2K chat cap this is the original 1.15x + 600 MB model; asking for
     * a bigger context now scales instead of silently underestimating.
     */
    fun estimateRamBytes(
        fileBytes: Long,
        isGguf: Boolean = true,
        contextTokens: Int = CHAT_CONTEXT_TOKENS,
    ): Long {
        val factor = if (isGguf) GGUF_WEIGHT_FACTOR else OTHER_WEIGHT_FACTOR
        val base = if (isGguf) GGUF_RUNTIME_BASE else OTHER_RUNTIME_BASE
        val perK = if (isGguf) GGUF_KV_PER_K_TOKENS else OTHER_KV_PER_K_TOKENS
        val kv = (perK * (contextTokens.coerceAtLeast(0) / 1_024.0)).toLong()
        return (fileBytes * factor).toLong() + base + kv
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
            val files = sorted.map { GgufFile(it.rfilename, it.byteSize ?: 0L) }
            // A partial size would understate the fit; show unknown instead.
            val bytes = if (files.all { it.bytes > 0L }) files.sumOf { it.bytes } else 0L
            val baseName = shardBases[key.removePrefix("shard:")] ?: files.first().name
            GgufVariant(
                key = key,
                quant = quantLabel(baseName),
                files = files,
                bytes = bytes,
                fit = if (bytes > 0L) {
                    fitFor(bytes, device)
                } else {
                    FitResult(Fit.UNKNOWN, null, 0f)
                },
                sharded = files.size > 1 || key.startsWith("shard:"),
            )
        }.sortedWith(compareBy({ it.bytes <= 0L }, { it.bytes }))
    }

    private fun otherWeightBytes(model: HfModel): Long? {
        fun sum(extension: String): Long = model.siblings
            .filter { it.rfilename.endsWith(extension, ignoreCase = true) }
            .mapNotNull { it.byteSize }
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
