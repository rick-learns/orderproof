package dev.orderproof.emberkitchen.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.orderproof.contract.MenuItemDto
import dev.orderproof.contract.OptionGroupDto
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.domain.ApiOutcome
import dev.orderproof.emberkitchen.domain.cart.CartLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ItemUiState(
    val item: MenuItemDto? = null,
    val selected: Set<String> = emptySet(),
    val quantity: Int = 1,
    val error: String? = null,
) {
    /** Display estimate only; the backend re-prices everything at quote and checkout time. */
    val estimateMinor: Long
        get() = item?.let { menuItem ->
            val deltas = menuItem.optionGroups.flatMap { it.options }.filter { it.id in selected }.sumOf { it.priceDeltaMinor }
            (menuItem.basePriceMinor + deltas) * quantity
        } ?: 0

    fun selectedIn(group: OptionGroupDto) = group.options.count { it.id in selected }
}

class ItemViewModel(private val container: AppContainer, private val itemId: String) : ViewModel() {
    private val mutable = MutableStateFlow(ItemUiState())
    val state: StateFlow<ItemUiState> = mutable.asStateFlow()

    init {
        container.menu.cached()?.items?.firstOrNull { it.id == itemId }?.let(::show) ?: viewModelScope.launch {
            when (val outcome = container.menu.load()) {
                is ApiOutcome.Success -> outcome.value.items.firstOrNull { it.id == itemId }?.let(::show)
                    ?: mutable.update { it.copy(error = "This item is no longer on the menu.") }
                else -> mutable.update { it.copy(error = "Couldn't load this item. Go back and try again.") }
            }
        }
    }

    private fun show(item: MenuItemDto) {
        val defaults = item.optionGroups.filter { it.required }.mapNotNull { it.options.firstOrNull()?.id }.toSet()
        mutable.value = ItemUiState(item = item, selected = defaults)
    }

    fun choose(group: OptionGroupDto, optionId: String) = mutable.update { state ->
        val inGroup = group.options.map { it.id }.toSet()
        if (group.required || group.maxSelections == 1) {
            state.copy(selected = state.selected - inGroup + optionId)
        } else if (optionId in state.selected) {
            state.copy(selected = state.selected - optionId)
        } else if (state.selectedIn(group) < group.maxSelections) {
            state.copy(selected = state.selected + optionId)
        } else {
            state
        }
    }

    fun changeQuantity(delta: Int) = mutable.update { it.copy(quantity = (it.quantity + delta).coerceIn(1, MAX_QUANTITY)) }

    fun addToCart(onAdded: () -> Unit) {
        val state = state.value
        val item = state.item ?: return
        val chosen = item.optionGroups.flatMap { it.options }.filter { it.id in state.selected }
        viewModelScope.launch {
            container.cart.update { cart ->
                cart.add(CartLine(UUID.randomUUID().toString(), item.id, item.name, chosen.map { it.id }, chosen.map { it.name }, state.quantity))
            }
            onAdded()
        }
    }

    companion object {
        const val MAX_QUANTITY = 20
    }
}
