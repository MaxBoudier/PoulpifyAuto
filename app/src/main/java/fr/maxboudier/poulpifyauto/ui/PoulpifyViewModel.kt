package fr.maxboudier.poulpifyauto.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.maxboudier.poulpifyauto.core.model.AppConfig
import fr.maxboudier.poulpifyauto.core.data.PoulpifySettings
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState
import fr.maxboudier.poulpifyauto.core.model.Track
import fr.maxboudier.poulpifyauto.core.session.PoulpifyGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PoulpifyViewModel : ViewModel() {

    private val coordinator = PoulpifyGraph.coordinator
    private val settings: PoulpifySettings = PoulpifyGraph.settings

    val state: StateFlow<PoulpifyUiState> = coordinator.state

    val config: StateFlow<AppConfig?> = settings.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    /** Position interpolée, rafraîchie par l'UI et non par l'état global. */
    fun currentPositionMs(): Long = coordinator.currentPositionMs()

    fun startSession() = coordinator.acquire()

    fun stopSession() = coordinator.release()

    fun retry() = coordinator.retry()

    fun clearError() = coordinator.clearError()

    fun togglePlayPause() = viewModelScope.launch { coordinator.togglePlayPause() }

    fun skipNext() = viewModelScope.launch { coordinator.hostSkip() }

    fun skipPrevious() = viewModelScope.launch { coordinator.skipPrevious() }

    fun seekTo(positionMs: Long) = viewModelScope.launch { coordinator.seekTo(positionMs) }

    fun toggleQueueLock() = viewModelScope.launch { coordinator.toggleQueueLock() }

    fun addToQueue(track: Track) = viewModelScope.launch { coordinator.addToQueue(track) }

    fun refreshLibrary() = coordinator.refreshLibrary()

    fun search(query: String) = viewModelScope.launch {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return@launch
        }
        _searching.value = true
        _searchResults.value = coordinator.search(query)
        _searching.value = false
    }

    // --- Réglages ---

    fun saveServerUrl(url: String) = viewModelScope.launch { settings.setServerUrl(url) }

    fun saveHostPassword(password: String) = viewModelScope.launch {
        settings.setHostPassword(password)
    }

    fun saveDriverProfile(name: String, emoji: String) = viewModelScope.launch {
        settings.setDriverProfile(name, emoji)
    }

    fun setAutoStart(enabled: Boolean) = viewModelScope.launch {
        settings.setAutoStartOnCarConnect(enabled)
    }

    fun setDisableServerAutoDisconnect(enabled: Boolean) = viewModelScope.launch {
        settings.setDisableServerAutoDisconnect(enabled)
    }
}
