package com.m.model.data

enum class Fit { WORKS, SLOW, WONT_RUN, UNKNOWN }

data class FitResult(
    val fit: Fit,
    val estimatedRamBytes: Long?,
    val ratio: Float,
    val storageBlocked: Boolean = false,
)

data class ModelCard(
    val model: HfModel,
    val downloadBytes: Long?,
    val parameters: Long?,
    val ggufVariants: Int,
    val fit: FitResult,
) {
    val id: String get() = model.id
    val name: String get() = model.id.substringAfterLast('/').ifBlank { model.id }
    val author: String get() = model.author ?: model.id.substringBefore('/', "")
}

object ModelFit {
    private const val MB = 1_000_000L
    private const val RESERVED_STORAGE = 300L * MB

    private val shardPattern =
        Regex("""^(.*)-\d{5}-of-\d{5}\.gguf$""", RegexOption.IGNORE_CASE)

    fun analyze(model: HfModel, device: DeviceSpecs): ModelCard {
        val ggufSizes = ggufGroups(model)
        val isGguf = ggufSizes.isNotEmpty()
        val downloadBytes = if (isGguf) ggufSizes.minOrNull() else otherWeightBytes(model)

        val parameters = model.gguf?.total?.takeIf { it > 0 }
            ?: model.safetensors?.total?.takeIf { it > 0 }
            ?: model.safetensors?.parameters?.values?.maxOrNull()?.takeIf { it > 0 }

        val needsRam = downloadBytes?.let { bytes ->
            val factor = if (isGguf) 1.15 else 1.2
            val overhead = if (isGguf) 600L * MB else 900L * MB
            (bytes * factor).toLong() + overhead
        }

        val budget = device.inferenceBudgetBytes
        val storageBlocked = downloadBytes != null &&
            downloadBytes + RESERVED_STORAGE > device.freeStorageBytes

        val fit = when {
            downloadBytes == null || needsRam == null -> Fit.UNKNOWN
            storageBlocked -> Fit.WONT_RUN
            budget <= 0L -> Fit.WONT_RUN
            else -> when (needsRam.toFloat() / budget) {
                in 0f..0.65f -> Fit.WORKS
                in 0f..1f -> Fit.SLOW
                else -> Fit.WONT_RUN
            }
        }

        val ratio = if (needsRam != null && budget > 0L) {
            needsRam.toFloat() / budget
        } else {
            0f
        }

        return ModelCard(
            model = model,
            downloadBytes = downloadBytes,
            parameters = parameters,
            ggufVariants = ggufSizes.size,
            fit = FitResult(
                fit = fit,
                estimatedRamBytes = needsRam,
                ratio = ratio,
                storageBlocked = storageBlocked,
            ),
        )
    }

    /**
     * One entry per downloadable quantization. Sharded GGUF files are summed into
     * one entry so we never mistake one shard for the whole model.
     */
    private fun ggufGroups(model: HfModel): List<Long> {
        val groups = linkedMapOf<String, Long>()
        model.siblings.forEach { sibling ->
            val size = sibling.size?.takeIf { it > 0 } ?: return@forEach
            val name = sibling.rfilename
            if (!name.endsWith(".gguf", ignoreCase = true)) return@forEach

            val lowerName = name.lowercase()
            // Projector files and adapters are not the model itself.
            if (lowerName.contains("mmproj") ||
                lowerName.contains("projector") ||
                lowerName.contains("lora")
            ) {
                return@forEach
            }

            val shard = shardPattern.matchEntire(name)
            val key = if (shard != null) {
                "shard:${shard.groupValues[1].lowercase()}"
            } else {
                "file:${name.lowercase()}"
            }
            groups[key] = (groups[key] ?: 0L) + size
        }
        return groups.values.toList()
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
