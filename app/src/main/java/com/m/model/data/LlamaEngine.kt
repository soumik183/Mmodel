package com.m.model.data

import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nehuatl.llamacpp.LlamaContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.absoluteValue
import kotlin.random.Random

class LlamaEngine {
    data class Loaded(
        val contextLength: Int,
        val threads: Int,
        val modelDetails: Map<String, Any>,
    )

    data class GenerationResult(
        val tokenCount: Int,
        val durationMs: Long,
    )

    class EngineFailure(message: String, cause: Throwable? = null) : IOException(message, cause)

    private data class Session(
        val context: LlamaContext,
        val descriptor: ParcelFileDescriptor,
        val path: String,
    )

    private val operation = Mutex()
    private val generation = Mutex()
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var session: Session? = null
    @Volatile
    private var tokenSink: ((String) -> Unit)? = null

    val isLoaded: Boolean
        get() = synchronized(lock) { session != null }

    val isSupported: Boolean
        get() = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    suspend fun load(
        file: File,
        contextLength: Int = 2_048,
    ): Loaded = withContext(Dispatchers.IO) {
        operation.withLock {
            unloadLocked()

            if (!isSupported) {
                throw EngineFailure("Local chat needs a 64-bit ARM phone.")
            }
            if (!file.isFile) {
                throw EngineFailure("The model file is missing. Download it again.")
            }
            if (!hasGgufHeader(file)) {
                throw EngineFailure("This file is not a valid GGUF model.")
            }

            val opened = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            createSessionLocked(opened, file.absolutePath, contextLength)
        }
    }

    /** Loads a model from a SAF descriptor without copying it into app storage. */
    suspend fun load(
        descriptor: ParcelFileDescriptor,
        modelName: String,
        contextLength: Int = 2_048,
    ): Loaded = withContext(Dispatchers.IO) {
        operation.withLock {
            unloadLocked()
            createSessionLocked(descriptor, modelName, contextLength)
        }
    }

    private suspend fun createSessionLocked(
        opened: ParcelFileDescriptor,
        label: String,
        contextLength: Int,
    ): Loaded {
        if (!isSupported) {
            runCatching { opened.close() }
            throw EngineFailure("Local chat needs a 64-bit ARM phone.")
        }

        val threads = defaultThreads()
        val chosenContextLength = contextLength.coerceIn(512, 4_096)
        try {
            val context = LlamaContext(
                Random.nextInt().absoluteValue,
                mapOf(
                    "model" to label,
                    "model_fd" to opened.fd,
                    "use_mmap" to false,
                    "use_mlock" to false,
                    "n_ctx" to chosenContextLength,
                    "n_batch" to 256,
                    "n_threads" to threads,
                ),
            )
            context.setTokenCallback { token -> tokenSink?.invoke(token) }

            if (!currentCoroutineContext().isActive) {
                runCatching { context.release() }
                throw CancellationException("Model load cancelled")
            }

            synchronized(lock) {
                session = Session(context, opened, label)
            }
            return Loaded(chosenContextLength, threads, context.modelDetails)
        } catch (t: Throwable) {
            runCatching { opened.close() }
            if (t is CancellationException) throw t
            throw if (t is EngineFailure) t else EngineFailure(failureMessage(t), t)
        }
    }

    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        onToken: (String) -> Unit,
    ): GenerationResult = withContext(Dispatchers.IO) {
        val current = synchronized(lock) { session }
            ?: throw EngineFailure("Load a model before sending a message.")

        generation.withLock {
            var tokens = 0
            tokenSink = { token ->
                tokens++
                onToken(token)
            }
            try {
                val started = System.currentTimeMillis()
                current.context.completion(
                    mapOf(
                        "prompt" to prompt,
                        "emit_partial_completion" to true,
                        "n_predict" to maxTokens.coerceIn(1, 1_024),
                        "temperature" to 0.7,
                        "top_k" to 40,
                        "top_p" to 0.95,
                        "min_p" to 0.05,
                        "penalty_last_n" to 64,
                        "penalty_repeat" to 1.1,
                    ),
                )
                GenerationResult(tokens, System.currentTimeMillis() - started)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                throw EngineFailure(
                    "The model stopped before answering. Try a shorter conversation or a smaller model.",
                    t,
                )
            } finally {
                tokenSink = null
            }
        }
    }

    suspend fun format(messages: List<Map<String, Any>>): String? =
        withContext(Dispatchers.IO) {
            operation.withLock {
                generation.withLock {
                    val current = synchronized(lock) { session } ?: return@withLock null
                    runCatching { current.context.getFormattedChat(messages, "") }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                }
            }
        }

    suspend fun stop() {
        withContext(Dispatchers.IO) {
            operation.withLock {
                val current = synchronized(lock) { session } ?: return@withLock
                runCatching { current.context.stopCompletion() }
            }
        }
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        operation.withLock {
            unloadLocked()
        }
    }

    fun release() {
        releaseScope.launch {
            operation.withLock {
                unloadLocked()
            }
        }
    }

    private suspend fun unloadLocked() {
        val current = synchronized(lock) { session } ?: return
        runCatching { current.context.stopCompletion() }
        generation.withLock {
            var release = false
            synchronized(lock) {
                if (session === current) {
                    session = null
                    release = true
                }
            }
            if (release) {
                runCatching { current.context.release() }
                runCatching { current.descriptor.close() }
            }
        }
    }

    private fun hasGgufHeader(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            val header = ByteArray(4)
            input.read(header) == 4 &&
                header[0] == 'G'.code.toByte() &&
                header[1] == 'G'.code.toByte() &&
                header[2] == 'U'.code.toByte() &&
                header[3] == 'F'.code.toByte()
        }
    }.getOrDefault(false)

    private fun defaultThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            cores <= 2 -> cores.coerceAtLeast(1)
            cores <= 4 -> cores - 1
            else -> 4
        }
    }

    private fun failureMessage(error: Throwable): String {
        val cause = error.cause
        return when {
            error is UnsatisfiedLinkError ||
                cause is UnsatisfiedLinkError ||
                error is ExceptionInInitializerError ->
                "The chat engine is not available for this device build."

            error is OutOfMemoryError || cause is OutOfMemoryError ->
                "Not enough memory to load this model. Try a smaller quantization."

            error is SecurityException ->
                "The app cannot read the downloaded model file."

            error is IOException && !error.message.isNullOrBlank() ->
                error.message.orEmpty()

            error is IllegalArgumentException ->
                "The engine rejected this model file."

            else ->
                error.message?.takeIf { it.isNotBlank() }
                    ?: "The model could not be loaded. Try a smaller quantization."
        }
    }
}
