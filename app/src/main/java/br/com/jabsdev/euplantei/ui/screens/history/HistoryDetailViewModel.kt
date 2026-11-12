package br.com.jabsdev.euplantei.ui.screens.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.data.db.ConsultationEntity
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

class HistoryDetailViewModel(
    private val container: AppContainer,
    private val id: Long,
) : ViewModel() {
    private val repository = container.consultationRepository
    private val _pendingDelete = MutableStateFlow<ConsultationEntity?>(null)
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages
    private var cleanupJob: Job? = null

    val consultation: StateFlow<ConsultationEntity?> = combine(
        repository.observeById(id),
        _pendingDelete,
    ) { entity, pending -> entity ?: pending }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    fun weather() = consultation.value?.let(repository::weatherOf)

    fun delete() {
        val entity = consultation.value ?: return
        cleanupJob?.cancel()
        viewModelScope.launch {
            repository.delete(entity)
            _pendingDelete.value = entity
            cleanupJob = viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                repository.deleteImage(entity.imagePath)
                _pendingDelete.value = null
            }
        }
    }

    fun undoDelete() {
        val entity = _pendingDelete.value ?: return
        cleanupJob?.cancel()
        viewModelScope.launch {
            repository.undoDelete(entity)
            _pendingDelete.value = null
            _messages.emit(br.com.jabsdev.euplantei.R.string.history_restored)
        }
    }

    fun export(uri: Uri) {
        val entity = consultation.value ?: return
        viewModelScope.launch {
            repository.exportText(entity, uri)
                .onSuccess { _messages.emit(br.com.jabsdev.euplantei.R.string.history_export_success) }
                .onFailure { _messages.emit(br.com.jabsdev.euplantei.R.string.history_export_failed) }
        }
    }

    fun exportFileName(): String {
        val entity = consultation.value ?: return "consulta_euplantei.txt"
        val date = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", java.util.Locale.US)
            .format(
                java.time.Instant.ofEpochMilli(entity.createdAt)
                    .atZone(java.time.ZoneId.systemDefault()),
            )
        return "consulta_euplantei_$date.txt"
    }

    fun buildText(): String = consultation.value?.let(repository::buildExportText).orEmpty()

    companion object {
        private const val UNDO_WINDOW_MS = 5_000L

        fun factory(container: AppContainer, id: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { HistoryDetailViewModel(container, id) }
            }
    }
}
