package com.m.model.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
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
        category: ModelCategory = ModelCategory.GGUF,
        limit: Int = 20,
        skip: Int = 0,
        withGguf: Boolean = false,
    ): List<HfModel> = withContext(Dispatchers.IO) {
        val url = base.newBuilder()
            .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            .addQueryParameter("sort", sort.apiValue)
            .addQueryParameter("direction", "-1")
            .addQueryParameter("full", "true")
            .addQueryParameter("blobs", "true")
            .apply {
                if (query.isNotBlank()) addQueryParameter("search", query.trim())
                if (skip > 0) addQueryParameter("skip", skip.toString())
                category.filters.forEach { addQueryParameter("filter", it) }
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

    /**
     * Retries transient failures (rate limits, 5xx, dropped connections) with
     * backoff. Uses [delay] so a retry never blocks the IO thread pool.
     */
    private suspend fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mmodel/1.5 (Android)")
            .header("Accept", "application/json")
            .build()

        var lastError: IOException? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) {
                            throw IOException("Hugging Face returned an empty response.")
                        }
                        return body
                    }

                    if (isRetryable(response.code) && attempt < MAX_ATTEMPTS - 1) {
                        retryDelay(attempt, response.header("Retry-After"))
                    } else {
                        throw HfHttpException(response.code)
                    }
                }
            } catch (e: HfHttpException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt >= MAX_ATTEMPTS - 1) throw e
                retryDelay(attempt, null)
            }
        }
        throw lastError ?: IOException("Hugging Face request failed.")
    }

    private fun isRetryable(code: Int): Boolean =
        code == 408 || code == 429 || code in 500..599

    private suspend fun retryDelay(attempt: Int, retryAfter: String?) {
        val wait = parseRetryAfterMs(retryAfter) ?: (500L shl attempt)
        delay(wait.coerceIn(300L, 5_000L))
    }

    /** Accepts both the numeric-seconds and HTTP-date forms of Retry-After. */
    private fun parseRetryAfterMs(value: String?): Long? {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) return null
        trimmed.toLongOrNull()?.let { return it * 1_000L }
        return runCatching {
            val parsed = SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss zzz",
                Locale.US,
            ).parse(trimmed)
            if (parsed == null) null
            else (parsed.time - System.currentTimeMillis()).coerceAtLeast(0L)
        }.getOrNull()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
