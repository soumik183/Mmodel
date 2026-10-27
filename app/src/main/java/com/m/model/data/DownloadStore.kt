package com.m.model.data

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

@Serializable
enum class DownloadStatus { DOWNLOADING, PAUSED, DONE, ERROR }

@Serializable
data class DownloadFile(
    val remoteName: String,
    val localName: String,
    val bytesTotal: Long,
    val bytesDone: Long = 0L,
    val done: Boolean = false,
)

@Serializable
data class DownloadRecord(
    val id: String,
    val modelId: String,
    val modelName: String,
    val quant: String,
    val dirPath: String,
    val files: List<DownloadFile>,
    val status: DownloadStatus = DownloadStatus.PAUSED,
    val error: String? = null,
    val architecture: String? = null,
    val contextLength: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
) {
    val totalBytes: Long get() = files.sumOf { it.bytesTotal }
    val doneBytes: Long get() = files.sumOf { if (it.done) it.bytesTotal else it.bytesDone }
    val isComplete: Boolean get() = files.isNotEmpty() && files.all { it.done }
    val firstPath: String? get() = files.firstOrNull()
        ?.let { File(dirPath, it.localName).absolutePath }
    val displayName: String get() = "$modelName · $quant"

    companion object {
        fun idFor(modelId: String, variant: GgufVariant): String = "$modelId|${variant.key}"

        fun create(card: ModelCard, variant: GgufVariant, root: File): DownloadRecord {
            val dir = File(root, safeName(card.id))
            return DownloadRecord(
                id = idFor(card.id, variant),
                modelId = card.id,
                modelName = card.name,
                quant = variant.quant,
                dirPath = dir.absolutePath,
                files = variant.files.map { file ->
                    DownloadFile(
                        remoteName = file.name,
                        localName = safeFileName(file.name),
                        bytesTotal = file.bytes,
                    )
                },
                architecture = card.model.gguf?.architecture
                    ?: card.model.config?.architectures?.firstOrNull(),
                contextLength = card.model.gguf?.contextLength
                    ?: card.model.config?.maxPositionEmbeddings,
            )
        }

        private fun safeName(value: String): String = value
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
            .ifBlank { "model" }

        private fun safeFileName(value: String): String = value
            .replace('\\', '_')
            .replace('/', '_')
            .replace("..", "_")
            .ifBlank { "model.gguf" }
    }
}

class DownloadStore(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val manifest = File(appContext.filesDir, "downloads.json")
    private val persistLock = Any()

    val modelsRoot: File = File(
        appContext.getExternalFilesDir(null) ?: appContext.filesDir,
        "models",
    )

    private val _records = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val records: StateFlow<List<DownloadRecord>> = _records.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val activeLock = Any()
    private var activeJob: Job? = null
    private var activeRecordId: String? = null
    private var activeCall: Call? = null

    init {
        runCatching { modelsRoot.mkdirs() }
        load()
    }

    fun freeBytes(): Long = runCatching {
        StatFs(modelsRoot.absolutePath).availableBytes
    }.getOrDefault(0L)

    fun startNew(record: DownloadRecord, scope: CoroutineScope): String? {
        val busy = synchronized(activeLock) { activeJob != null }
        if (busy) return "Another download is already running."
        if (record.files.isEmpty()) return "This model has no downloadable files."

        _records.update { current ->
            listOf(record) + current.filterNot { it.id == record.id }
        }
        persist()
        return start(record.id, scope)
    }

    fun start(recordId: String, scope: CoroutineScope): String? {
        lateinit var job: Job
        synchronized(activeLock) {
            val running = activeJob
            if (running != null) {
                return when {
                    activeRecordId != recordId -> "Another download is already running."
                    running.isActive -> null
                    else -> "The previous download is still stopping. Tap Resume again in a moment."
                }
            }

            val current = _records.value.firstOrNull { it.id == recordId }
                ?: return "Download not found."
            if (current.isComplete) {
                markStatus(recordId, DownloadStatus.DONE, null)
                return null
            }

            _records.update { list ->
                list.map { record ->
                    if (record.id == recordId) {
                        record.copy(status = DownloadStatus.DOWNLOADING, error = null)
                    } else {
                        record
                    }
                }
            }
            persist()

            job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    runDownload(recordId)
                } finally {
                    synchronized(activeLock) {
                        if (activeJob === job) {
                            activeRecordId = null
                            activeCall = null
                            activeJob = null
                        }
                    }
                }
            }
            activeRecordId = recordId
            activeJob = job
        }
        job.start()
        return null
    }

    fun pause(recordId: String) {
        synchronized(activeLock) {
            if (activeRecordId == recordId) {
                activeCall?.cancel()
                activeJob?.cancel()
            }
        }
        markStatus(recordId, DownloadStatus.PAUSED, null)
    }

    fun delete(recordId: String, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val job = synchronized(activeLock) {
                if (activeRecordId == recordId) {
                    activeCall?.cancel()
                    activeJob?.cancel()
                    activeJob
                } else {
                    null
                }
            }
            job?.cancelAndJoin()

            val record = _records.value.firstOrNull { it.id == recordId } ?: return@launch
            record.files.forEach { file ->
                runCatching { File(record.dirPath, file.localName).delete() }
                runCatching { File(record.dirPath, file.localName + ".part").delete() }
            }
            runCatching { File(record.dirPath).delete() }

            _records.update { list -> list.filterNot { it.id == recordId } }
            persist()
        }
    }

    fun pauseActive() {
        val id = synchronized(activeLock) {
            val current = activeRecordId ?: return
            activeCall?.cancel()
            activeJob?.cancel()
            current
        }
        markStatus(id, DownloadStatus.PAUSED, "Download was interrupted. Tap Resume.")
    }

    private suspend fun runDownload(recordId: String) {
        try {
            while (true) {
                val record = _records.value.firstOrNull { it.id == recordId } ?: return
                val index = record.files.indexOfFirst { !it.done }
                if (index < 0) {
                    markDone(recordId)
                    return
                }
                downloadFile(recordId, index)
            }
        } catch (e: CancellationException) {
            pauseOrFinish(recordId)
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) {
                pauseOrFinish(recordId)
                throw CancellationException("Download stopped")
            }
            markStatus(recordId, DownloadStatus.ERROR, friendlyError(e))
        }
    }

    private fun pauseOrFinish(recordId: String) {
        val complete = _records.value.firstOrNull { it.id == recordId }?.isComplete == true
        if (complete) {
            markDone(recordId)
        } else {
            markStatus(recordId, DownloadStatus.PAUSED, null)
        }
    }

    private suspend fun downloadFile(recordId: String, index: Int) {
        val record = _records.value.firstOrNull { it.id == recordId } ?: return
        val file = record.files.getOrNull(index) ?: return
        val dir = File(record.dirPath).also { it.mkdirs() }
        val target = File(dir, file.localName)
        val part = File(dir, file.localName + ".part")

        if (target.isFile && file.bytesTotal > 0 && target.length() == file.bytesTotal) {
            finishFile(recordId, index, target.length())
            return
        }
        if (target.isFile) {
            if (!part.exists()) target.renameTo(part) else target.delete()
        }

        var existing = part.takeIf { it.isFile }?.length() ?: 0L
        if (file.bytesTotal > 0) {
            if (existing >= file.bytesTotal) {
                if (part.length() == file.bytesTotal) {
                    finishFile(recordId, index, existing)
                    return
                }
                part.delete()
                existing = 0L
            }
        }
        ensureStorage(dir, file, existing)

        val requestBuilder = Request.Builder()
            .url(resolveUrl(record.modelId, file.remoteName))
            .header("User-Agent", "Mmodel/1.2 (Android)")
            .header("Accept-Encoding", "identity")
        if (existing > 0 && (file.bytesTotal <= 0L || existing < file.bytesTotal)) {
            requestBuilder.header("Range", "bytes=$existing-")
        }
        val request = requestBuilder.build()
        val call = client.newCall(request)
        synchronized(activeLock) {
            if (activeRecordId != recordId) throw CancellationException("Download stopped")
            activeCall = call
            if (activeJob?.isCancelled == true) call.cancel()
        }

        try {
            call.execute().use { response ->
                if (response.code == 416) {
                    part.delete()
                    throw DownloadFailure("The partial download was stale. Tap Resume to start it again.")
                }
                if (!response.isSuccessful) {
                    throw DownloadFailure(httpMessage(response.code))
                }

                val body = response.body
                    ?: throw DownloadFailure("Hugging Face returned an empty file.")
                val appending = response.code == 206 && existing > 0
                if (!appending) existing = 0L

                val expected = when {
                    response.code == 206 && body.contentLength() >= 0L ->
                        existing + body.contentLength()

                    body.contentLength() > 0L -> body.contentLength()
                    file.bytesTotal > 0L -> file.bytesTotal
                    else -> 0L
                }

                if (expected > 0L && file.bytesTotal <= 0L) {
                    updateFileTotal(recordId, index, expected)
                }

                body.byteStream().use { input ->
                    FileOutputStream(part, appending).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = existing
                        var lastUpdate = 0L

                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 100) {
                                updateProgress(recordId, index, written)
                                lastUpdate = now
                            }
                        }
                        output.fd.sync()
                        updateProgress(recordId, index, written)

                        if (expected > 0L && written != expected) {
                            throw DownloadFailure(
                                "Download incomplete ($written of $expected bytes). Tap Resume to continue.",
                            )
                        }
                    }
                }
            }
        } finally {
            synchronized(activeLock) {
                if (activeCall === call) activeCall = null
            }
        }

        finishFile(recordId, index, part.length())
    }

    private fun finishFile(recordId: String, index: Int, bytes: Long) {
        val record = _records.value.firstOrNull { it.id == recordId } ?: return
        val file = record.files.getOrNull(index) ?: return
        val dir = File(record.dirPath).also { it.mkdirs() }
        val target = File(dir, file.localName)
        val part = File(dir, file.localName + ".part")

        if (part.isFile) {
            target.delete()
            if (!part.renameTo(target)) {
                throw DownloadFailure("Could not finish the file on this device. Tap Resume.")
            }
        }

        val actual = target.takeIf { it.isFile }?.length() ?: 0L
        if (actual <= 0L) throw DownloadFailure("The downloaded file is empty. Tap Resume.")

        _records.update { list ->
            list.map { item ->
                if (item.id != recordId) {
                    item
                } else {
                    item.copy(
                        files = item.files.mapIndexed { i, current ->
                            if (i == index) {
                                current.copy(
                                    bytesTotal = if (current.bytesTotal > 0L) {
                                        current.bytesTotal
                                    } else {
                                        actual
                                    },
                                    bytesDone = actual,
                                    done = true,
                                )
                            } else {
                                current
                            }
                        },
                    )
                }
            }
        }
        persist()
    }

    private fun markDone(recordId: String) {
        _records.update { list ->
            list.map { record ->
                if (record.id == recordId) {
                    record.copy(
                        status = DownloadStatus.DONE,
                        error = null,
                        completedAt = record.completedAt ?: System.currentTimeMillis(),
                    )
                } else {
                    record
                }
            }
        }
        persist()
    }

    private fun markStatus(recordId: String, status: DownloadStatus, error: String?) {
        _records.update { list ->
            list.map { record ->
                if (record.id == recordId) {
                    record.copy(status = status, error = error)
                } else {
                    record
                }
            }
        }
        persist()
    }

    private fun updateProgress(recordId: String, index: Int, bytes: Long) {
        _records.update { list ->
            list.map { record ->
                if (record.id != recordId) {
                    record
                } else {
                    record.copy(
                        files = record.files.mapIndexed { i, file ->
                            if (i == index) file.copy(bytesDone = bytes.coerceAtLeast(0L))
                            else file
                        },
                    )
                }
            }
        }
    }

    private fun updateFileTotal(recordId: String, index: Int, total: Long) {
        _records.update { list ->
            list.map { record ->
                if (record.id != recordId) {
                    record
                } else {
                    record.copy(
                        files = record.files.mapIndexed { i, file ->
                            if (i == index) file.copy(bytesTotal = total)
                            else file
                        },
                    )
                }
            }
        }
    }

    private fun ensureStorage(dir: File, file: DownloadFile, existing: Long) {
        val remaining = if (file.bytesTotal > 0L) {
            (file.bytesTotal - existing).coerceAtLeast(0L)
        } else {
            0L
        }
        val free = runCatching { StatFs(dir.absolutePath).availableBytes }
            .getOrDefault(Long.MAX_VALUE)
        if (remaining + RESERVED_STORAGE > free) {
            throw DownloadFailure(
                "Not enough free storage for this file. Free up space, then tap Resume.",
            )
        }
    }

    private fun resolveUrl(modelId: String, remoteName: String) =
        "https://huggingface.co".toHttpUrl().newBuilder()
            .addPathSegments(modelId)
            .addPathSegment("resolve")
            .addPathSegment("main")
            .addPathSegments(remoteName)
            .addQueryParameter("download", "true")
            .build()

    private fun load() {
        val saved = runCatching {
            manifest.takeIf { it.isFile }?.readText()?.let {
                json.decodeFromString<List<DownloadRecord>>(it)
            }
        }.getOrNull() ?: emptyList()

        val normalized = saved.map { normalize(it) }
        _records.value = normalized
        if (normalized != saved) persist()
    }

    private fun normalize(record: DownloadRecord): DownloadRecord {
        val files = record.files.map { file ->
            val target = File(record.dirPath, file.localName)
            val part = File(record.dirPath, file.localName + ".part")
            when {
                target.isFile && (file.bytesTotal <= 0L || target.length() == file.bytesTotal) ->
                    file.copy(bytesDone = target.length(), done = true)

                target.isFile -> file.copy(bytesDone = target.length(), done = false)
                part.isFile -> file.copy(bytesDone = part.length(), done = false)
                else -> file.copy(bytesDone = 0L, done = false)
            }
        }
        val allDone = files.isNotEmpty() && files.all { it.done }
        val status = when {
            allDone -> DownloadStatus.DONE
            record.status == DownloadStatus.DONE -> DownloadStatus.ERROR
            record.status == DownloadStatus.DOWNLOADING -> DownloadStatus.PAUSED
            else -> record.status
        }
        val error = when {
            status == DownloadStatus.ERROR && record.error == null ->
                "The downloaded file is missing or incomplete."

            status == DownloadStatus.PAUSED && record.status == DownloadStatus.DOWNLOADING ->
                "Download was interrupted. Tap Resume."

            else -> record.error
        }
        return record.copy(
            files = files,
            status = status,
            error = error,
            completedAt = if (allDone) {
                record.completedAt ?: System.currentTimeMillis()
            } else {
                record.completedAt
            },
        )
    }

    private fun persist() {
        synchronized(persistLock) {
            runCatching {
                manifest.parentFile?.mkdirs()
                val temp = File(manifest.parentFile, manifest.name + ".tmp")
                temp.writeText(json.encodeToString(_records.value))
                if (!temp.renameTo(manifest)) {
                    manifest.writeText(temp.readText())
                    temp.delete()
                }
            }
        }
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is DownloadFailure -> e.message ?: "Download failed."
        is UnknownHostException -> "No internet connection. Tap Resume when you are back online."
        is SocketTimeoutException -> "The download timed out. Tap Resume to continue."
        is IOException -> e.message?.takeIf { it.isNotBlank() } ?: "Network error. Tap Resume."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Download failed. Tap Resume."
    }

    private fun httpMessage(code: Int): String = when (code) {
        401, 403 -> "This model is gated or private. Accept access on Hugging Face, then retry."
        404 -> "This file is no longer available on Hugging Face."
        429 -> "Hugging Face is rate-limiting this download. Try again later."
        in 500..599 -> "Hugging Face had a server error ($code). Tap Resume."
        else -> "Download failed (HTTP $code)."
    }

    private class DownloadFailure(message: String) : IOException(message)

    private companion object {
        const val RESERVED_STORAGE = 300_000_000L
    }
}
