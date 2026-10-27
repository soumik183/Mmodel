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
)

@Serializable
data class HfSibling(
    val rfilename: String = "",
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
)

enum class ModelSort(val label: String, val apiValue: String) {
    TRENDING("Trending", "downloads"),
    LIKED("Most liked", "likes"),
    RECENT("Recently updated", "lastModified"),
}
