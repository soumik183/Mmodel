package com.m.model.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m.model.data.DeviceSpecs
import com.m.model.data.Fit
import com.m.model.data.HfApi
import com.m.model.data.HfHttpException
import com.m.model.data.HfModel
import com.m.model.data.ModelCard
import com.m.model.data.ModelFit
import com.m.model.data.ModelSort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.log10

data class BrowseState(
    val query: String = "",
    val sort: ModelSort = ModelSort.TRENDING,
    val ggufOnly: Boolean = false,
    val items: List<ModelCard> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class DetailState(
    val id: String = "",
    val card: ModelCard? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val device: DeviceSpecs = DeviceSpecs.read(application)

    private val api = HfApi()
    private val cache = mutableMapOf<String, ModelCard>()
    private var searchJob: Job? = null

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

    init {
        loadRecommendations()
        scheduleSearch(0)
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

    fun refreshRecommendations() {
        loadRecommendations()
    }

    private fun scheduleSearch(delayMs: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)

            val query = browse.query
            val sort = browse.sort
            val ggufOnly = browse.ggufOnly

            browse = browse.copy(loading = true, error = null)
            try {
                val models = api.listModels(query, sort, ggufOnly)
                val cards = models.map { analyze(it) }
                cache.putAll(cards.associateBy { it.id })
                browse = browse.copy(items = cards, loading = false, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                browse = browse.copy(loading = false, error = friendlyError(e))
            }
        }
    }

    private fun loadRecommendations() {
        recLoading = true
        recError = null
        viewModelScope.launch {
            try {
                val models = api.listModels(
                    query = "",
                    sort = ModelSort.TRENDING,
                    ggufOnly = true,
                    limit = 80,
                )
                val cards = models.map { analyze(it) }
                cache.putAll(cards.associateBy { it.id })

                val fitting = cards.filter {
                    it.fit.fit == Fit.WORKS || it.fit.fit == Fit.SLOW
                }
                recommendations = fitting
                    .sortedByDescending { score(it) }
                    .take(12)

                if (recommendations.isEmpty()) {
                    recError = "No sized GGUF models were found. Try refreshing."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recError = friendlyError(e)
            } finally {
                recLoading = false
            }
        }
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
                    detail = DetailState(id = id, error = friendlyError(e))
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

        is SocketTimeoutException ->
            "Hugging Face took too long to answer. Try again."

        is HfHttpException -> when (e.code) {
            429 -> "Hugging Face is rate-limiting this device. Wait a minute, then retry."
            in 500..599 -> "Hugging Face is having trouble (${e.code}). Try again shortly."
            401, 403 -> "This model is private or gated on Hugging Face."
            else -> "Hugging Face returned error ${e.code}."
        }

        is IOException -> e.message ?: "Network error. Check your connection."
        else -> e.message ?: "Something went wrong."
    }
}
