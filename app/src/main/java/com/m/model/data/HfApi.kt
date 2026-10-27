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
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
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
        withGguf: Boolean = false,
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
                if (withGguf) addQueryParameter("expand", "gguf")
            }
            .build()

        val body = fetch(url.toString())
        decode { json.decodeFromString<List<HfModel>>(body) }
    }

    suspend fun getModel(id: String): HfModel = withContext(Dispatchers.IO) {
        val url = base.newBuilder()
            .addPathSegments(id)
            .addQueryParameter("full", "true")
            .addQueryParameter("blobs", "true")
            .build()

        val body = fetch(url.toString())
        decode { json.decodeFromString<HfModel>(body) }
    }

    private inline fun <T> decode(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw IOException("Hugging Face sent an unreadable response. Try again.", e)
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mmodel/1.3 (Android)")
            .header("Accept", "application/json")
            .build()

        var lastError: IOException? = null
        for (attempt in 0 until 3) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return response.body?.string()
                            ?: throw IOException("Hugging Face returned an empty response.")
                    }

                    val retryable = response.code == 429 || response.code in 500..599
                    if (retryable && attempt < 2) {
                        val retryAfterMs = response.header("Retry-After")
                            ?.toLongOrNull()
                            ?.times(1_000L)
                        sleepBeforeRetry(attempt, retryAfterMs)
                    } else {
                        throw HfHttpException(response.code)
                    }
                }
            } catch (e: HfHttpException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt >= 2) throw e
                sleepBeforeRetry(attempt, null)
            }
        }
        throw lastError ?: IOException("Hugging Face request failed.")
    }

    private fun sleepBeforeRetry(attempt: Int, retryAfterMs: Long?) {
        val wait = retryAfterMs ?: (500L shl attempt)
        try {
            Thread.sleep(wait.coerceIn(300L, 5_000L))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
