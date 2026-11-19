package dev.orderproof.emberkitchen.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.data.settings.ConnectionSettings
import dev.orderproof.emberkitchen.domain.ApiOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val url: String = "",
    val checking: Boolean = false,
    val status: String? = null,
    val reachable: Boolean = false,
) {
    val valid: Boolean get() = ConnectionSettings.normalize(url) != null
}

class ConnectViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            container.connection.current()?.let { saved -> mutable.update { it.copy(url = saved) } }
        }
    }

    fun onUrlChange(value: String) = mutable.update { it.copy(url = value, status = null, reachable = false) }

    fun test() {
        val url = ConnectionSettings.normalize(state.value.url) ?: return
        mutable.update { it.copy(checking = true, status = null) }
        viewModelScope.launch {
            val outcome = container.api.health(url)
            mutable.update {
                it.copy(
                    checking = false,
                    reachable = outcome is ApiOutcome.Success,
                    status = when (outcome) {
                        is ApiOutcome.Success -> "Connected to ${outcome.value.service} ${outcome.value.version}."
                        is ApiOutcome.Unavailable -> "Can't reach $url. Check the address and that the backend is running."
                        is ApiOutcome.Unknown -> "No answer from $url (${outcome.detail})."
                        is ApiOutcome.Rejected -> "$url answered HTTP ${outcome.httpStatus}; is this the Ember Kitchen backend?"
                    },
                )
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            container.connection.save(state.value.url)
            onSaved()
        }
    }
}
