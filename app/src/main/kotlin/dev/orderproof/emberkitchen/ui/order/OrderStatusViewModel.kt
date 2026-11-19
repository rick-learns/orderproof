package dev.orderproof.emberkitchen.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.orderproof.contract.OrderDto
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.domain.ApiOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderUiState(
    val order: OrderDto? = null,
    val loading: Boolean = true,
    /** Shown above the last known order (if any): a refresh failure never hides a confirmed order. */
    val problem: String? = null,
)

class OrderStatusViewModel(private val container: AppContainer, private val orderId: String) : ViewModel() {
    private val mutable = MutableStateFlow(OrderUiState())
    val state: StateFlow<OrderUiState> = mutable.asStateFlow()

    fun refresh() {
        mutable.update { it.copy(loading = true) }
        viewModelScope.launch {
            val outcome = container.api.order(orderId)
            mutable.update {
                when (outcome) {
                    is ApiOutcome.Success -> OrderUiState(order = outcome.value, loading = false)
                    is ApiOutcome.Rejected -> it.copy(loading = false, problem = "We couldn't find this order (HTTP ${outcome.httpStatus}).")
                    else -> it.copy(loading = false, problem = "Couldn't refresh the order status. Your order is safe; try again.")
                }
            }
        }
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        container.activeOrder.clear()
        onDone()
    }
}
