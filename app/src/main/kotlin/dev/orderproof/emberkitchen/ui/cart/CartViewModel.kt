package dev.orderproof.emberkitchen.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.orderproof.contract.QuoteRequest
import dev.orderproof.contract.QuoteResponse
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.domain.ApiOutcome
import dev.orderproof.emberkitchen.domain.cart.Cart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface QuoteLoad {
    data object Loading : QuoteLoad
    data object Empty : QuoteLoad
    data class Failed(val message: String) : QuoteLoad
    data class Ready(val quote: QuoteResponse) : QuoteLoad
}

data class CartUiState(val cart: Cart = Cart(), val quote: QuoteLoad = QuoteLoad.Loading)

/** Re-quotes on every cart change: the screen always shows the server's numbers. */
@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModel(private val container: AppContainer) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    private val quote = combine(container.cart.cart.distinctUntilChanged(), refresh) { cart, _ -> cart }
        .flatMapLatest { cart ->
            flow {
                if (cart.lines.isEmpty()) {
                    emit(QuoteLoad.Empty)
                    return@flow
                }
                emit(QuoteLoad.Loading)
                emit(fetchQuote(container, cart))
            }
        }

    val state: StateFlow<CartUiState> = combine(container.cart.cart, quote) { cart, q -> CartUiState(cart, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartUiState())

    fun retryQuote() = refresh.value++

    fun setQuantity(lineId: String, quantity: Int) = viewModelScope.launch {
        container.cart.update { it.setQuantity(lineId, quantity) }
    }

    fun applyCoupon(code: String) = viewModelScope.launch {
        container.cart.update { it.copy(couponCode = code.trim().uppercase().ifEmpty { null }) }
    }
}

suspend fun fetchQuote(container: AppContainer, cart: Cart): QuoteLoad =
    when (val outcome = container.api.quote(QuoteRequest(cart.toOrderLines(), cart.couponCode))) {
        is ApiOutcome.Success -> QuoteLoad.Ready(outcome.value)
        is ApiOutcome.Unavailable -> QuoteLoad.Failed("Ember Kitchen is temporarily unavailable, so we can't price your order. Try again.")
        is ApiOutcome.Unknown -> QuoteLoad.Failed("We couldn't price your order (${outcome.detail}). Try again.")
        is ApiOutcome.Rejected -> QuoteLoad.Failed(outcome.error.message)
    }
