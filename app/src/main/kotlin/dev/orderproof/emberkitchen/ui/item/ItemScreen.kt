package dev.orderproof.emberkitchen.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orderproof.contract.OptionDto
import dev.orderproof.contract.OptionGroupDto
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.core.formatMoney
import dev.orderproof.emberkitchen.ui.TestTags
import dev.orderproof.emberkitchen.ui.components.EmberScaffold
import dev.orderproof.emberkitchen.ui.components.LoadingState
import dev.orderproof.emberkitchen.ui.components.ProblemCard

@Composable
fun ItemScreen(container: AppContainer, itemId: String, onDone: () -> Unit) {
    val vm: ItemViewModel = viewModel(key = "item-$itemId") { ItemViewModel(container, itemId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val item = state.item

    EmberScaffold(
        title = item?.name ?: "Customize",
        onBack = onDone,
        bottomBar = {
            if (item != null) {
                Button(
                    onClick = { vm.addToCart(onDone) },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag(TestTags.ADD_TO_CART),
                ) { Text("Add ${state.quantity} to cart · ${formatMoney(state.estimateMinor)}") }
            }
        },
    ) { padding ->
        when {
            state.error != null -> Column(Modifier.padding(padding).padding(16.dp)) {
                ProblemCard("Item unavailable", state.error.orEmpty(), "Back to menu", onDone)
            }
            item == null -> LoadingState("Loading…", Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).testTag(TestTags.ITEM_DETAILS).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(item.description, style = MaterialTheme.typography.bodyLarge)
                item.optionGroups.forEach { group -> OptionGroupSection(group, state, vm) }
                QuantityStepper(state.quantity, vm::changeQuantity)
                Text(
                    "Prices are confirmed by the restaurant at checkout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OptionGroupSection(group: OptionGroupDto, state: ItemUiState, vm: ItemViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val rule = if (group.required) "Choose 1" else "Optional · up to ${group.maxSelections}"
        Text(
            "${group.name} · $rule",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        group.options.forEach { option ->
            val checked = option.id in state.selected
            if (group.required) {
                OptionRow(option, Modifier.selectable(checked, role = Role.RadioButton) { vm.choose(group, option.id) }) {
                    RadioButton(selected = checked, onClick = null)
                }
            } else {
                val enabled = checked || state.selectedIn(group) < group.maxSelections
                OptionRow(option, Modifier.toggleable(checked, enabled, Role.Checkbox) { vm.choose(group, option.id) }) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(option: OptionDto, modifier: Modifier, control: @Composable () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(TestTags.option(option.id)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        control()
        Text(option.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (option.priceDeltaMinor > 0) Text("+${formatMoney(option.priceDeltaMinor)}", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Quantity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        StepButton("−", "Decrease quantity", quantity > 1, TestTags.QUANTITY_DECREASE) { onChange(-1) }
        Text(
            "$quantity",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag(TestTags.QUANTITY_VALUE).semantics { contentDescription = "Quantity $quantity" },
        )
        StepButton("+", "Increase quantity", quantity < ItemViewModel.MAX_QUANTITY, TestTags.QUANTITY_INCREASE) { onChange(1) }
    }
}

@Composable
fun StepButton(symbol: String, description: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp).testTag(tag).semantics { contentDescription = description },
    ) { Text(symbol, style = MaterialTheme.typography.titleLarge) }
}
