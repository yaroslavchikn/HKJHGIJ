package com.example.calculator.vault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calculator.vault.data.MediaItem
import com.example.calculator.vault.data.PinManager
import com.example.calculator.vault.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    enum class PinStage {
        CREATE,
        UNLOCK,
        UNLOCKED
    }

    data class VaultUiState(
        val pinStage: PinStage? = null,
        val items: List<MediaItem> = emptyList(),
        val importing: Boolean = false,
        val deleteOriginal: Boolean = true,
        val selected: MediaItem? = null
    )

    sealed class VaultEvent {
        data class Message(val text: String) : VaultEvent()
        data class DeleteOriginals(val uris: List<Uri>) : VaultEvent()
    }

    val repository = VaultRepository(application)
    private val pinManager = PinManager(application)

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState

    private val _events = Channel<VaultEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (_uiState.value.pinStage == null) {
            _uiState.update {
                it.copy(
                    pinStage = if (pinManager.hasPin()) {
                        PinStage.UNLOCK
                    } else {
                        PinStage.CREATE
                    }
                )
            }
        }
    }

    fun createPin(pin: String) {
        if (pin.length < 4) {
            sendEvent(VaultEvent.Message("Код должен содержать минимум 4 символа"))
            return
        }

        pinManager.setPin(pin)
        _uiState.update { it.copy(pinStage = PinStage.UNLOCKED) }
        loadItems()
    }

    fun unlock(pin: String) {
        if (pinManager.verifyPin(pin)) {
            _uiState.update { it.copy(pinStage = PinStage.UNLOCKED) }
            loadItems()
        } else {
            sendEvent(VaultEvent.Message("Неверный код"))
        }
    }

    fun lock() {
        if (_uiState.value.pinStage == PinStage.UNLOCKED) {
            repository.clearCache()

            _uiState.update {
                it.copy(
                    pinStage = if (pinManager.hasPin()) PinStage.UNLOCK else PinStage.CREATE,
                    items = emptyList(),
                    selected = null
                )
            }
        }
    }

    fun lockIfUnlocked() {
        lock()
    }

    fun toggleDeleteOriginal() {
        val newValue = !_uiState.value.deleteOriginal

        _uiState.update { it.copy(deleteOriginal = newValue) }

        sendEvent(
            VaultEvent.Message(
                if (newValue) {
                    "Удаление оригиналов включено"
                } else {
                    "Удаление оригиналов выключено"
                }
            )
        )
    }

    fun open(item: MediaItem?) {
        _uiState.update { it.copy(selected = item) }
    }

    fun delete(item: MediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.deleteItem(item) }
            loadItemsInternal()

            withContext(Dispatchers.Main) {
                sendEvent(VaultEvent.Message("Удалено"))
            }
        }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(importing = true) }
            }

            val imported = mutableListOf<Uri>()

            uris.forEach { uri ->
                val ok = runCatching {
                    repository.importMedia(uri, null)
                }.isSuccess

                if (ok) imported.add(uri)
            }

            loadItemsInternal()

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(importing = false) }

                if (imported.isEmpty()) {
                    sendEvent(VaultEvent.Message("Не удалось импортировать файлы"))
                } else {
                    sendEvent(VaultEvent.Message("Импортировано: ${imported.size}"))

                    if (_uiState.value.deleteOriginal) {
                        sendEvent(VaultEvent.DeleteOriginals(imported))
                    }
                }
            }
        }
    }

    private fun loadItems() {
        viewModelScope.launch {
            loadItemsInternal()
        }
    }

    private suspend fun loadItemsInternal() {
        val items = withContext(Dispatchers.IO) {
            repository.listItems()
        }

        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(items = items) }
        }
    }

    private fun sendEvent(event: VaultEvent) {
        _events.trySend(event)
    }
}
