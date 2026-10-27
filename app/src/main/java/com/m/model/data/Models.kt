package com.m.model.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HfModel(
    val id: String = "",
    val author: String? = null,
    val downloads: Long? = null,
    val likes: Long? = null,
    val tags: List<String> = emptyList(),
    @SerialName("pipeline_tag") val pipelineTag: String? = null,
    @SerialName("library_name") val library: String? = null,
    @SerialName("lastModified") val lastModified: String? = null,
    val siblings: List<HfSibling> = emptyList(),
    val safetensors: HfSafetensors? = null,
    val gguf: HfGguf? = null,
    val config: HfConfig? = null,
)

@Serializable
data class HfConfig(
    val architectures: List<String> = emptyList(),
    @SerialName("model_type") val modelType: String? = null,
    @SerialName("max_position_embeddings") val maxPositionEmbeddings: Long? = null,
)

@Serializable
data class HfSibling(
    val rfilename: String = "",
    val size: Long? = null,
    val lfs: HfLfs? = null,
) {
    /** Some gated/Xet repos expose the real size under `lfs` only. */
    val byteSize: Long? get() = size?.takeIf { it > 0L } ?: lfs?.size?.takeIf { it > 0L }
}

@Serializable
data class HfLfs(
    val size: Long? = null,
)

@Serializable
data class HfSafetensors(
    val total: Long? = null,
    val parameters: Map<String, Long> = emptyMap(),
)

@Serializable
data class HfGguf(
    val total: Long? = null,
    val architecture: String? = null,
    @SerialName("context_length") val contextLength: Long? = null,
    @SerialName("chat_template") val chatTemplate: String? = null,
)

enum class ModelSort(val label: String, val apiValue: String) {
    TRENDING("Trending", "downloads"),
    LIKED("Most liked", "likes"),
    RECENT("Recently updated", "lastModified"),
}
