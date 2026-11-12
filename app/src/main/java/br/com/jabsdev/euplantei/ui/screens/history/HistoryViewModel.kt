package br.com.jabsdev.euplantei.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.data.db.ConsultationEntity
import br.com.jabsdev.euplantei.util.ImageUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import java.text.Normalizer
import java.util.Locale

data class HistoryUiState(
    val items: List<ConsultationEntity> = emptyList(),
    val filtered: List<ConsultationEntity> = emptyList(),
    val query: String = "",
    val speciesFilter: String? = null,
    val speciesOptions: List<String> = emptyList(),
    val pendingDelete: ConsultationEntity? = null,
    val loading: Boolean = true,
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.consultationRepository
    private val _query = MutableStateFlow("")
    private val _species = MutableStateFlow<String?>(null)
    private val _pendingDelete = MutableStateFlow<ConsultationEntity?>(null)
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages

    private var cleanupJob: Job? = null

    val ui: StateFlow<HistoryUiState> = combine(
        repository.observeAll(),
        _query,
        _species,
        _pendingDelete,
    ) { items, query, species, pending ->
        val options = items.map { it.species }.distinct().sorted()
        val normalizedQuery = normalize(query)
        val filtered = items.filter { item ->
            (species == null || item.species == species) &&
                (normalizedQuery.isBlank() || normalize(
                    "${item.species} ${item.condition} ${item.symptom} ${item.advice}",
                ).contains(normalizedQuery))
        }
        HistoryUiState(
            items = items,
            filtered = filtered,
            query = query,
            speciesFilter = species,
            speciesOptions = options,
            pendingDelete = pending,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setSpecies(value: String?) {
        _species.value = value
    }

    fun deleteWithUndo(entity: ConsultationEntity) {
        cleanupJob?.cancel()
        _pendingDelete.value?.let { previous ->
            viewModelScope.launch { repository.deleteImage(previous.imagePath) }
        }
        viewModelScope.launch {
            repository.delete(entity)
            _pendingDelete.value = entity
            cleanupJob = viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                if (_pendingDelete.value?.id == entity.id) {
                    repository.deleteImage(entity.imagePath)
                    _pendingDelete.value = null
                }
            }
        }
    }

    fun undoDelete() {
        val pending = _pendingDelete.value ?: return
        cleanupJob?.cancel()
        viewModelScope.launch {
            repository.undoDelete(pending)
            _pendingDelete.value = null
            _messages.emit(br.com.jabsdev.euplantei.R.string.history_restored)
        }
    }

    fun dismissUndo() {
        _pendingDelete.value = null
    }

    fun export(entity: ConsultationEntity, uri: Uri) {
        viewModelScope.launch {
            repository.exportText(entity, uri)
                .onSuccess { _messages.emit(br.com.jabsdev.euplantei.R.string.history_export_success) }
                .onFailure { _messages.emit(br.com.jabsdev.euplantei.R.string.history_export_failed) }
        }
    }

    fun exportFileName(entity: ConsultationEntity): String {
        val date = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.US)
            .format(
                java.time.Instant.ofEpochMilli(entity.createdAt)
                    .atZone(java.time.ZoneId.systemDefault()),
            )
        return "consulta_euplantei_$date.txt"
    }

    fun buildExportText(entity: ConsultationEntity) = repository.buildExportText(entity)

    fun clearAll(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.clearAll()
            _pendingDelete.value = null
            onDone()
        }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return decomposed.lowercase(Locale.ROOT)
    }

    companion object {
        private const val UNDO_WINDOW_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(container) }
        }
    }
}
