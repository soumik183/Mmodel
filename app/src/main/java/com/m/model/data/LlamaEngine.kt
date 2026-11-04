package com.m.model.data

import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
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
import java.io.IOException
import kotlin.math.absoluteValue
import kotlin.random.Random

class LlamaEngine {
    data class Loaded(
        val contextLength: Int,
        val threads: Int,
        val modelDetails: Map<String, Any>,
        val profileLabel: String? = null,
        val architecture: String? = null,
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
    ): Loaded = load(file, ResolvedProfile(PerformanceProfile.ADAPTIVE, defaultThreads(), contextLength, 256, true, "Auto"))

    suspend fun load(file: File, profile: ResolvedProfile): Loaded = withContext(Dispatchers.IO) {
        operation.withLock {
            unloadLocked()

            if (!isSupported) {
                throw EngineFailure("Local chat needs a 64-bit ARM phone.")
            }
            if (!file.isFile) {
                throw EngineFailure("The model file is missing. Download it again.")
            }
            val opened = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            createSessionLocked(opened, file.absolutePath, profile)
        }
    }

    /** Loads a model from a SAF descriptor without copying it into app storage. */
    suspend fun load(
        descriptor: ParcelFileDescriptor,
        modelName: String,
        contextLength: Int = 2_048,
    ): Loaded = load(
        descriptor,
        modelName,
        ResolvedProfile(PerformanceProfile.ADAPTIVE, defaultThreads(), contextLength, 256, true, "Auto"),
    )

    suspend fun load(
        descriptor: ParcelFileDescriptor,
        modelName: String,
        profile: ResolvedProfile,
    ): Loaded = withContext(Dispatchers.IO) {
        operation.withLock {
            unloadLocked()
            createSessionLocked(descriptor, modelName, profile)
        }
    }

    private suspend fun createSessionLocked(
        opened: ParcelFileDescriptor,
        label: String,
        profile: ResolvedProfile,
    ): Loaded {
        if (!isSupported) {
            runCatching { opened.close() }
            throw EngineFailure("Local chat needs a 64-bit ARM phone.")
        }

        val threads = profile.threads.coerceAtLeast(1)
        val chosenContextLength = profile.contextLength.coerceIn(512, 4_096)
        validateGgufHeader(opened)?.let { reason ->
            runCatching { opened.close() }
            throw EngineFailure(reason)
        }
        try {
            val context = LlamaContext(
                System.nanoTime().absoluteValue,
                mapOf(
                    "model" to label,
                    "model_fd" to opened.fd,
                    "use_mmap" to profile.useMmap,
                    "use_mlock" to false,
                    "n_ctx" to chosenContextLength,
                    "n_batch" to profile.batchSize,
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
            val details = context.modelDetails
            val arch = details["general.architecture"]?.toString()
                ?: details["architecture"]?.toString()
            return Loaded(chosenContextLength, threads, details, profile.label, arch)
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
    ): GenerationResult = generate(prompt, maxTokens, PerformanceProfile.ADAPTIVE, onToken)

    suspend fun generate(
        prompt: String,
        maxTokens: Int,
        profile: PerformanceProfile,
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
                val sampling = ChatPrompt.samplingParams(profile)
                current.context.completion(
                    mapOf(
                        "prompt" to prompt,
                        "emit_partial_completion" to true,
                        "n_predict" to maxTokens.coerceIn(1, 1_024),
                        "temperature" to sampling["temperature"],
                        "top_k" to sampling["top_k"],
                        "top_p" to sampling["top_p"],
                        "min_p" to sampling["min_p"],
                        "penalty_last_n" to sampling["penalty_last_n"],
                        "penalty_repeat" to sampling["penalty_repeat"],
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

    /**
     * Reads the GGUF magic and little-endian version, or null when the engine
     * should decide. Providers without positioned reads return null so the load
     * path still works for them.
     */
    private fun validateGgufHeader(opened: ParcelFileDescriptor): String? {
        val header = runCatching {
            ByteArray(8).also { buffer ->
                Os.pread(opened.fileDescriptor, buffer, 0, buffer.size, 0L)
            }
        }.getOrNull() ?: return null

        val parsed = parseGgufHeader(header) ?: return "This file is not a valid GGUF model."
        if (parsed.version < 2) {
            return "This GGUF is too old (version ${parsed.version}). Re-export it with a newer tool."
        }
        return null
    }

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
