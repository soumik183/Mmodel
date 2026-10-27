package com.m.model.ui

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m.model.data.ChatPrompt
import com.m.model.data.ChatTurn
import com.m.model.data.DeviceSpecs
import com.m.model.data.DownloadRecord
import com.m.model.data.DownloadStatus
import com.m.model.data.DownloadStore
import com.m.model.data.Fit
import com.m.model.data.GgufVariant
import com.m.model.data.HfApi
import com.m.model.data.HfHttpException
import com.m.model.data.HfModel
import com.m.model.data.LlamaEngine
import com.m.model.data.ModelCard
import com.m.model.data.ModelFit
import com.m.model.data.ModelSort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import kotlin.math.log10

@Immutable
data class BrowseState(
    val query: String = "",
    val sort: ModelSort = ModelSort.TRENDING,
    val ggufOnly: Boolean = true,
    val items: List<ModelCard> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@Immutable
data class DetailState(
    val id: String = "",
    val card: ModelCard? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

enum class ChatRole { USER, ASSISTANT }

@Immutable
data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val streaming: Boolean = false,
    val meta: String? = null,
)

@Immutable
data class ChatState(
    val recordId: String? = null,
    val modelName: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val loadingModel: Boolean = false,
    val ready: Boolean = false,
    val generating: Boolean = false,
    val stopping: Boolean = false,
    val contextLength: Int = 0,
    val threads: Int = 0,
    val error: String? = null,
    val status: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    var device by mutableStateOf(DeviceSpecs.read(application))
        private set

    private val api = HfApi()
    private val cache = mutableMapOf<String, ModelCard>()
    private var searchJob: Job? = null
    private var recommendationsJob: Job? = null
    private var recommendationsEpoch = 0
    private var browseRequested = false

    var browse by mutableStateOf(BrowseState())
        private set

    var recommendations by mutableStateOf<List<ModelCard>>(emptyList())
        private set

    var recLoading by mutableStateOf(true)
        private set

    var recError by mutableStateOf<String?>(null)
        private set

    var detail by mutableStateOf(DetailState())
        private set

    private val downloadsStore = DownloadStore(application)
    val downloads: StateFlow<List<DownloadRecord>> = downloadsStore.records
    val downloadsRootPath: String get() = downloadsStore.modelsRoot.absolutePath

    var downloadNotice by mutableStateOf<String?>(null)
        private set

    private val engine = LlamaEngine()
    private val chatPreferences = application.getSharedPreferences("chat", 0)
    private val _chat = MutableStateFlow(ChatState())
    val chat: StateFlow<ChatState> = _chat.asStateFlow()

    private var nextMessageId = 0L
    @Volatile
    private var generationId = 0L
    @Volatile
    private var chatLoadEpoch = 0L

    init {
        loadRecommendations()

        val lastId = chatPreferences.getString(KEY_LAST_MODEL, null)
        if (!lastId.isNullOrBlank()) {
            downloadsStore.records.value
                .firstOrNull { it.id == lastId && it.isComplete }
                ?.let { record ->
                    _chat.value = ChatState(
                        recordId = record.id,
                        modelName = record.displayName,
                    )
                }
        }

        viewModelScope.launch {
            downloadsStore.records.collect { records ->
                val selectedId = _chat.value.recordId ?: return@collect
                val selected = records.firstOrNull { it.id == selectedId }
                if (selected == null || !selected.isComplete) {
                    invalidateChat("The downloaded model is no longer available.")
                } else if (_chat.value.modelName != selected.displayName) {
                    _chat.update { it.copy(modelName = selected.displayName) }
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        browse = browse.copy(query = value)
        scheduleSearch(400)
    }

    fun searchNow() {
        scheduleSearch(0)
    }

    fun onSortChange(value: ModelSort) {
        if (browse.sort == value) return
        browse = browse.copy(sort = value)
        scheduleSearch(0)
    }

    fun setGgufOnly(value: Boolean) {
        if (browse.ggufOnly == value) return
        browse = browse.copy(ggufOnly = value)
        scheduleSearch(0)
    }

    fun refreshBrowse() {
        scheduleSearch(0)
    }

    fun ensureBrowseLoaded() {
        if (browseRequested) return
        scheduleSearch(0)
    }

    fun refreshRecommendations() {
        loadRecommendations()
    }

    fun refreshDevice() {
        val updated = DeviceSpecs.read(getApplication())
        device = updated

        val refreshed = cache.mapValues { (_, card) -> ModelFit.analyze(card.model, updated) }
        cache.clear()
        cache.putAll(refreshed)

        browse = browse.copy(
            items = browse.items.map { refreshed[it.id] ?: ModelFit.analyze(it.model, updated) },
        )
        recommendations = recommendations
            .map { refreshed[it.id] ?: ModelFit.analyze(it.model, updated) }
            .filter { it.fit.fit == Fit.WORKS || it.fit.fit == Fit.SLOW }
            .sortedByDescending { score(it) }
            .take(12)
        detail = detail.copy(
            card = detail.card?.let { refreshed[it.id] ?: ModelFit.analyze(it.model, updated) },
        )
    }

    private fun scheduleSearch(delayMs: Long) {
        browseRequested = true
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)

            val query = browse.query
            val sort = browse.sort
            val ggufOnly = browse.ggufOnly

            browse = browse.copy(loading = true, error = null)
            try {
                val models = api.listModels(query, sort, ggufOnly, withGguf = true)
                val cards = models.map { analyze(it) }
                browse = browse.copy(items = cards, loading = false, error = null)

                // Search responses omit file sizes. Enrich a few leading cards so they
                // show a real fit without opening each model.
                val unknown = cards.filter { it.downloadBytes == null }.take(8)
                if (unknown.isNotEmpty()) {
                    fun apply(card: ModelCard) {
                        cache[card.id] = card
                        if (browse.query == query && browse.sort == sort && browse.ggufOnly == ggufOnly) {
                            browse = browse.copy(
                                items = browse.items.map { if (it.id == card.id) card else it },
                            )
                        }
                    }
                    val detailed = hydrate(unknown.map { it.id }, 8, ::apply)
                    if (detailed.isNotEmpty()) {
                        val byId = detailed.associateBy { it.id }
                        cache.putAll(byId)
                        if (browse.query == query && browse.sort == sort && browse.ggufOnly == ggufOnly) {
                            browse = browse.copy(items = browse.items.map { byId[it.id] ?: it })
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                browse = browse.copy(loading = false, error = friendlyError(e))
            }
        }
    }

    private fun loadRecommendations() {
        val epoch = ++recommendationsEpoch
        recommendationsJob?.cancel()
        recLoading = true
        recError = null
        recommendationsJob = viewModelScope.launch {
            try {
                val models = api.listModels(
                    query = "",
                    sort = ModelSort.TRENDING,
                    ggufOnly = true,
                    limit = 100,
                    withGguf = true,
                )
                if (recommendationsEpoch != epoch) return@launch
                val candidates = models
                    .filter { model ->
                        val gguf = model.gguf
                        val total = gguf?.total ?: 0L
                        gguf != null &&
                            !gguf.chatTemplate.isNullOrBlank() &&
                            total > 0L &&
                            total / 1_000_000_000.0 <= maxCandidateParams() &&
                            (model.pipelineTag == null || isChatPipeline(model.pipelineTag))
                    }
                    .map { analyze(it) }
                    .sortedByDescending { score(it) }
                    .take(24)

                val detailed = hydrate(candidates.map { it.id }, 24) { card ->
                    if (recommendationsEpoch == epoch) {
                        cache[card.id] = card
                        recommendations = recommendationPicks(recommendations + card)
                    }
                }
                if (recommendationsEpoch != epoch) return@launch
                cache.putAll(detailed.associateBy { it.id })
                recommendations = recommendationPicks(detailed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (recommendationsEpoch == epoch) recError = friendlyError(e)
            } finally {
                if (recommendationsEpoch == epoch) recLoading = false
            }
        }
    }

    private fun recommendationPicks(cards: List<ModelCard>): List<ModelCard> =
        cards
            .distinctBy { it.id }
            .filter { card ->
                val tag = card.model.pipelineTag
                !card.model.gguf?.chatTemplate.isNullOrBlank() &&
                    (tag == null || isChatPipeline(tag))
            }
            .filter { it.fit.fit == Fit.WORKS || it.fit.fit == Fit.SLOW }
            .sortedByDescending { score(it) }
            .take(12)

    private suspend fun hydrate(
        ids: List<String>,
        limit: Int,
        onCard: ((ModelCard) -> Unit)? = null,
    ): List<ModelCard> =
        withTimeoutOrNull(25_000L) {
            coroutineScope {
                ids.distinct().take(limit).map { id ->
                    async {
                        try {
                            analyze(api.getModel(id)).also { onCard?.invoke(it) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        } ?: emptyList()

    private fun maxCandidateParams(): Double {
        val budgetGb = device.inferenceBudgetBytes / 1_000_000_000.0
        return ((budgetGb - 0.4) / 0.3).coerceIn(1.0, 80.0)
    }

    private fun isChatPipeline(tag: String): Boolean = when (tag.lowercase()) {
        "text-generation", "image-text-to-text", "text2text-generation", "conversational" -> true
        else -> false
    }

    fun openModel(id: String) {
        if (id.isBlank()) return

        val cached = cache[id]
        if (detail.id == id && (cached != null || detail.loading)) {
            if (cached != null && detail.card == null) {
                detail = DetailState(id = id, card = cached)
            }
            return
        }

        if (cached != null) {
            detail = DetailState(id = id, card = cached)
            return
        }

        detail = DetailState(id = id, loading = true)
        viewModelScope.launch {
            try {
                val card = analyze(api.getModel(id))
                cache[id] = card
                if (detail.id == id) detail = DetailState(id = id, card = card)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (detail.id == id) {
                    val fallback = cache[id]?.takeIf {
                        it.model.siblings.isNotEmpty() || it.variants.isNotEmpty()
                    }
                    detail = if (fallback != null) {
                        DetailState(id = id, card = fallback, error = friendlyError(e))
                    } else {
                        DetailState(id = id, error = friendlyError(e))
                    }
                }
            }
        }
    }

    fun retryDetail() {
        val id = detail.id
        if (id.isNotBlank()) {
            cache.remove(id)
            detail = DetailState()
            openModel(id)
        }
    }

    fun closeDetail() {
        detail = DetailState()
    }

    fun downloadRecord(modelId: String, variant: GgufVariant): DownloadRecord? =
        downloadsStore.records.value.firstOrNull {
            it.id == DownloadRecord.idFor(modelId, variant)
        }

    fun startDownload(card: ModelCard, variant: GgufVariant) {
        val existing = downloadRecord(card.id, variant)
        when {
            existing?.status == DownloadStatus.DONE -> {
                downloadNotice = "Already downloaded. Open Downloads to chat with it."
                return
            }

            existing?.status == DownloadStatus.DOWNLOADING -> {
                downloadNotice = "This model is already downloading."
                return
            }

            variant.fit.storageBlocked -> {
                downloadNotice = "Not enough free storage for this download. Free up space first."
                return
            }
        }

        val error = if (existing == null) {
            downloadsStore.startNew(
                DownloadRecord.create(card, variant, downloadsStore.modelsRoot),
                viewModelScope,
            )
        } else {
            downloadsStore.start(existing.id, viewModelScope)
        }
        downloadNotice = error ?: if (existing == null) {
            "Download started. Track it in Downloads."
        } else {
            "Continuing download."
        }
    }

    fun pauseDownload(recordId: String) {
        downloadsStore.pause(recordId)
        downloadNotice = "Download paused. You can resume it later."
    }

    fun resumeDownload(recordId: String) {
        val error = downloadsStore.start(recordId, viewModelScope)
        downloadNotice = error ?: "Continuing download."
    }

    fun deleteDownload(recordId: String) {
        downloadsStore.delete(recordId, viewModelScope)
        downloadNotice = "Download removed from this phone."
    }

    fun clearDownloadNotice() {
        downloadNotice = null
    }

    fun downloadFreeBytes(): Long = downloadsStore.freeBytes()

    fun openChatFor(recordId: String) {
        val record = downloadsStore.records.value.firstOrNull { it.id == recordId }
        if (record == null || !record.isComplete) {
            downloadNotice = "Download this model before starting a chat."
            return
        }
        selectChatRecord(recordId, autoLoad = true)
    }

    fun selectChatRecord(recordId: String, autoLoad: Boolean = true) {
        val record = downloadsStore.records.value.firstOrNull { it.id == recordId }
        if (record == null || !record.isComplete) {
            _chat.update { it.copy(error = "This model is not fully downloaded.") }
            return
        }
        if (_chat.value.loadingModel && _chat.value.recordId != recordId) return

        val same = _chat.value.recordId == recordId
        if (same) {
            if (autoLoad && !_chat.value.ready && !_chat.value.loadingModel) {
                loadChatModel()
            }
            return
        }

        generationId++
        chatLoadEpoch++
        val epoch = chatLoadEpoch
        val unsupported = when {
            !device.canRunLocalChat ->
                "Local chat needs a 64-bit ARM phone. This device cannot load the engine."

            record.files.size > 1 ->
                "Chat currently supports single-file GGUFs. This download has ${record.files.size} shards."

            else -> null
        }

        _chat.value = ChatState(
            recordId = record.id,
            modelName = record.displayName,
            error = unsupported,
        )
        chatPreferences.edit().putString(KEY_LAST_MODEL, record.id).apply()

        if (unsupported != null) {
            viewModelScope.launch { engine.unload() }
            return
        }

        viewModelScope.launch {
            engine.unload()
            if (chatLoadEpoch == epoch && _chat.value.recordId == record.id && autoLoad) {
                loadChatModelInternal(record, epoch)
            }
        }
    }

    fun loadChatModel() {
        val state = _chat.value
        val recordId = state.recordId ?: return
        if (state.loadingModel || state.ready) return
        val record = downloadsStore.records.value.firstOrNull { it.id == recordId } ?: return
        if (!device.canRunLocalChat) {
            _chat.update { it.copy(error = "Local chat needs a 64-bit ARM phone.") }
            return
        }
        if (record.files.size > 1) {
            _chat.update {
                it.copy(error = "Chat currently supports single-file GGUFs. Download a single-file quant.")
            }
            return
        }

        chatLoadEpoch++
        val epoch = chatLoadEpoch
        viewModelScope.launch {
            loadChatModelInternal(record, epoch)
        }
    }

    private suspend fun loadChatModelInternal(record: DownloadRecord, epoch: Long) {
        if (_chat.value.recordId != record.id || chatLoadEpoch != epoch) return

        val fit = ModelFit.ramFit(record.totalBytes, device)
        if (fit.fit == Fit.WONT_RUN) {
            _chat.update {
                it.copy(
                    loadingModel = false,
                    ready = false,
                    error = "This model needs about ${formatBytes(fit.estimatedRamBytes)} RAM, " +
                        "but the safe budget is ${formatBytes(device.inferenceBudgetBytes)}. " +
                        "Use a smaller quantization.",
                )
            }
            return
        }

        _chat.update { it.copy(loadingModel = true, ready = false, error = null, status = null) }
        try {
            val path = record.firstPath
            if (path == null) {
                throw IllegalStateException("The model file is missing. Download it again.")
            }
            val contextLength = (record.contextLength ?: 2_048L)
                .coerceIn(512L, 2_048L)
                .toInt()
            val loaded = engine.load(File(path), contextLength)

            if (_chat.value.recordId != record.id || chatLoadEpoch != epoch) {
                return
            }

            val note = if (fit.fit == Fit.SLOW) {
                "Tight memory. Keep the conversation short for stable speed."
            } else {
                null
            }
            _chat.update {
                it.copy(
                    loadingModel = false,
                    ready = true,
                    error = null,
                    status = note,
                    contextLength = loaded.contextLength,
                    threads = loaded.threads,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (_chat.value.recordId == record.id && chatLoadEpoch == epoch) {
                _chat.update {
                    it.copy(
                        loadingModel = false,
                        ready = false,
                        error = e.message ?: "The model could not be loaded.",
                    )
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val message = text.trim()
        val state = _chat.value
        if (message.isEmpty() || !state.ready || state.generating || state.loadingModel) return

        val record = downloadsStore.records.value.firstOrNull { it.id == state.recordId } ?: return
        if (record.files.size > 1) {
            _chat.update { it.copy(error = "Chat supports single-file GGUFs only.") }
            return
        }

        val userMessage = ChatMessage(nextId(), ChatRole.USER, message)
        val assistantId = nextId()
        val assistantMessage = ChatMessage(
            id = assistantId,
            role = ChatRole.ASSISTANT,
            text = "",
            streaming = true,
        )
        _chat.update {
            it.copy(
                messages = it.messages + userMessage + assistantMessage,
                generating = true,
                stopping = false,
                error = null,
                status = null,
            )
        }

        val generation = ++generationId
        viewModelScope.launch {
            val contextLength = state.contextLength.coerceAtLeast(512)
            val history = _chat.value.messages
                .filter { it.id != assistantId && it.text.isNotBlank() }
                .map {
                    ChatTurn(
                        role = if (it.role == ChatRole.USER) "user" else "assistant",
                        content = it.text,
                    )
                }
            val trimmed = ChatPrompt.trimHistory(
                history,
                maxChars = (contextLength * 2).coerceIn(800, 6_000),
            )
            val system = ChatPrompt.DEFAULT_SYSTEM
            val modelKey = record.modelId + " " + record.architecture.orEmpty()
            val prompt = engine.format(ChatPrompt.nativeMessages(system, trimmed))
                ?: ChatPrompt.fallback(modelKey, system, trimmed)

            if (generationId != generation) return@launch
            if (_chat.value.stopping) {
                finishStopped(assistantId)
                return@launch
            }

            try {
                val result = engine.generate(
                    prompt,
                    maxTokens = (contextLength / 2).coerceIn(128, 512),
                ) { token ->
                    if (generationId != generation) return@generate
                    _chat.update { current ->
                        if (current.recordId != record.id) {
                            current
                        } else {
                            current.copy(
                                messages = current.messages.map {
                                    if (it.id == assistantId) it.copy(text = it.text + token) else it
                                },
                            )
                        }
                    }
                }

                if (generationId == generation) {
                    val wasStopping = _chat.value.stopping
                    val assistant = _chat.value.messages.firstOrNull { it.id == assistantId }
                    if (assistant == null || assistant.text.isBlank()) {
                        _chat.update {
                            it.copy(
                                messages = it.messages.filterNot { msg -> msg.id == assistantId },
                                generating = false,
                                stopping = false,
                                status = if (wasStopping) "Stopped" else null,
                                error = if (wasStopping) null else "The model returned an empty response.",
                            )
                        }
                    } else {
                        val speed = if (result.durationMs > 0L) {
                            result.tokenCount * 1_000.0 / result.durationMs
                        } else {
                            0.0
                        }
                        val meta = String.format(
                            Locale.US,
                            "%d tokens · %.1f tok/s",
                            result.tokenCount,
                            speed,
                        )
                        _chat.update {
                            it.copy(
                                messages = it.messages.map { msg ->
                                    if (msg.id == assistantId) {
                                        msg.copy(streaming = false, meta = meta)
                                    } else {
                                        msg
                                    }
                                },
                                generating = false,
                                stopping = false,
                                status = if (wasStopping) "Stopped" else null,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (generationId == generation) {
                    _chat.update {
                        it.copy(
                            messages = it.messages.map { msg ->
                                if (msg.id == assistantId) msg.copy(streaming = false) else msg
                            },
                            generating = false,
                            stopping = false,
                            status = "Stopped",
                        )
                    }
                }
                throw e
            } catch (e: Throwable) {
                if (generationId == generation) {
                    val hasText = _chat.value.messages
                        .firstOrNull { it.id == assistantId }
                        ?.text
                        ?.isNotBlank() == true
                    _chat.update {
                        it.copy(
                            messages = if (hasText) {
                                it.messages.map { msg ->
                                    if (msg.id == assistantId) msg.copy(streaming = false) else msg
                                }
                            } else {
                                it.messages.filterNot { msg -> msg.id == assistantId }
                            },
                            generating = false,
                            stopping = false,
                            error = e.message ?: "Generation failed.",
                        )
                    }
                }
            }
        }
    }

    fun stopChat() {
        if (!_chat.value.generating || _chat.value.stopping) return
        _chat.update { it.copy(stopping = true) }
        viewModelScope.launch {
            engine.stop()
        }
    }

    fun clearChat() {
        val wasGenerating = _chat.value.generating
        generationId++
        if (wasGenerating) {
            viewModelScope.launch { engine.stop() }
        }
        _chat.update {
            it.copy(
                messages = emptyList(),
                generating = false,
                stopping = false,
                error = null,
                status = null,
            )
        }
    }

    fun unloadChat() {
        generationId++
        chatLoadEpoch++
        _chat.update { state ->
            state.copy(
                messages = state.messages
                    .filterNot { it.streaming && it.text.isBlank() }
                    .map { if (it.streaming) it.copy(streaming = false) else it },
                loadingModel = false,
                ready = false,
                generating = false,
                stopping = false,
                error = null,
                status = null,
            )
        }
        viewModelScope.launch { engine.unload() }
    }

    private fun finishStopped(assistantId: Long) {
        _chat.update {
            it.copy(
                messages = it.messages.map { msg ->
                    if (msg.id == assistantId) msg.copy(streaming = false) else msg
                },
                generating = false,
                stopping = false,
                status = "Stopped",
            )
        }
    }

    private fun invalidateChat(message: String) {
        if (_chat.value.recordId == null) return
        generationId++
        chatLoadEpoch++
        _chat.update {
            ChatState(error = message)
        }
        chatPreferences.edit().remove(KEY_LAST_MODEL).apply()
        viewModelScope.launch { engine.unload() }
    }

    private fun nextId(): Long = ++nextMessageId

    private fun analyze(model: HfModel): ModelCard = ModelFit.analyze(model, device)

    private fun score(card: ModelCard): Double {
        val downloads = (card.model.downloads ?: 0L).coerceAtLeast(0L)
        val likes = (card.model.likes ?: 0L).coerceAtLeast(0L)
        val popularity = log10(downloads + 1.0) + 0.4 * log10(likes + 1.0)
        val fitPenalty = card.fit.ratio.coerceIn(0f, 1.5f) * 1.2
        return popularity - fitPenalty
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is UnknownHostException ->
            "No internet connection. Check your network and try again."

        is SocketTimeoutException, is InterruptedIOException ->
            "Hugging Face took too long to answer. Try again."

        is HfHttpException -> when (e.code) {
            429 -> "Hugging Face is rate-limiting this device. Wait a minute, then retry."
            in 500..599 -> "Hugging Face is having trouble (${e.code}). Try again shortly."
            401, 403 -> "This model is private or gated. Open it on Hugging Face, accept access, then retry."
            404 -> "This model was not found. It may have been renamed or removed."
            400 -> "Hugging Face could not read this model's metadata. Try again."
            else -> "Hugging Face returned error ${e.code}."
        }

        is IOException -> e.message ?: "Network error. Check your connection."
        else -> e.message ?: "Something went wrong."
    }

    override fun onCleared() {
        generationId++
        chatLoadEpoch++
        downloadsStore.pauseActive()
        engine.release()
        super.onCleared()
    }

    private companion object {
        const val KEY_LAST_MODEL = "last_model"
    }
}
