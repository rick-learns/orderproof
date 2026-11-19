package dev.orderproof.emberkitchen.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.orderproof.contract.MenuResponse
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.domain.ApiOutcome
import dev.orderproof.emberkitchen.domain.OrderSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MenuLoad {
    data object Loading : MenuLoad
    data class Failed(val message: String) : MenuLoad
    data class Loaded(val menu: MenuResponse) : MenuLoad
}

class MenuViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow<MenuLoad>(MenuLoad.Loading)
    val menu: StateFlow<MenuLoad> = mutable.asStateFlow()

    val cartCount: StateFlow<Int> = container.cart.cart.map { it.itemCount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val activeOrder: StateFlow<OrderSummary?> = container.activeOrder.activeOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load()
    }

    fun load() {
        mutable.value = MenuLoad.Loading
        viewModelScope.launch {
            mutable.value = when (val outcome = container.menu.load()) {
                is ApiOutcome.Success -> MenuLoad.Loaded(outcome.value)
                is ApiOutcome.Unavailable -> MenuLoad.Failed("Ember Kitchen is temporarily unavailable. Check your connection and try again.")
                is ApiOutcome.Unknown -> MenuLoad.Failed("The menu didn't load (${outcome.detail}). Try again.")
                is ApiOutcome.Rejected -> MenuLoad.Failed("The menu didn't load (HTTP ${outcome.httpStatus}).")
            }
        }
    }
}
