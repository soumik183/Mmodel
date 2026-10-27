package com.m.model.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class HfHttpException(val code: Int) : IOException("Hugging Face HTTP $code")

class HfApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val base = "https://huggingface.co/api/models".toHttpUrl()

    suspend fun listModels(
        query: String,
        sort: ModelSort,
        ggufOnly: Boolean,
        limit: Int = 50,
    ): List<HfModel> = withContext(Dispatchers.IO) {
        val url = base.newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("sort", sort.apiValue)
            .addQueryParameter("direction", "-1")
            .addQueryParameter("full", "true")
            .addQueryParameter("blobs", "true")
            .apply {
                if (query.isNotBlank()) addQueryParameter("search", query.trim())
                if (ggufOnly) addQueryParameter("filter", "gguf")
            }
            .build()

        json.decodeFromString<List<HfModel>>(fetch(url.toString()))
    }

    suspend fun getModel(id: String): HfModel = withContext(Dispatchers.IO) {
        val url = base.newBuilder()
            .addPathSegments(id)
            .addQueryParameter("full", "true")
            .addQueryParameter("blobs", "true")
            .build()

        json.decodeFromString<HfModel>(fetch(url.toString()))
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mmodel/1.0 (Android)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HfHttpException(response.code)
            return response.body?.string()
                ?: throw IOException("Empty response from Hugging Face")
        }
    }
}
