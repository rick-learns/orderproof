package dev.orderproof.emberkitchen.ui.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orderproof.contract.OrderDto
import dev.orderproof.contract.OrderStatus
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.core.formatMoney
import dev.orderproof.emberkitchen.ui.TestTags
import dev.orderproof.emberkitchen.ui.components.EmberScaffold
import dev.orderproof.emberkitchen.ui.components.LoadingState
import dev.orderproof.emberkitchen.ui.components.ProblemCard
import dev.orderproof.emberkitchen.ui.theme.EmberColors

@Composable
fun OrderStatusScreen(container: AppContainer, orderId: String, onBackToMenu: () -> Unit) {
    val vm: OrderStatusViewModel = viewModel(key = "order-$orderId") { OrderStatusViewModel(container, orderId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    EmberScaffold(title = "Order status") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.problem?.let { ProblemCard("Status not updated", it, "Try again", vm::refresh) }
            val order = state.order
            when {
                order != null -> OrderDetails(order)
                state.loading -> LoadingState("Loading your order…", Modifier.padding(vertical = 24.dp))
            }
            if (order != null) {
                OutlinedButton(onClick = vm::refresh, modifier = Modifier.fillMaxWidth().testTag(TestTags.ORDER_REFRESH)) {
                    Text("Refresh status")
                }
            }
            Button(onClick = { vm.finish(onBackToMenu) }, modifier = Modifier.fillMaxWidth().testTag(TestTags.ORDER_DONE)) {
                Text("Start a new order")
            }
        }
    }
}

@Composable
private fun OrderDetails(order: OrderDto) {
    Card(colors = CardDefaults.cardColors(containerColor = EmberColors.Charcoal, contentColor = EmberColors.Cream)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Thank you! Order placed.", style = MaterialTheme.typography.titleMedium)
            Text(
                order.orderNumber,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(TestTags.ORDER_NUMBER),
            )
            Text(
                statusText(order.status),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(TestTags.ORDER_STATUS).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                "Total paid ${formatMoney(order.totalMinor)} · payment ${order.paymentStatus.lowercase()}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(TestTags.ORDER_TOTAL),
            )
        }
    }
    order.lines.forEach { line ->
        val options = if (line.optionNames.isEmpty()) "" else line.optionNames.joinToString(prefix = " (", postfix = ")")
        Text("${line.quantity} × ${line.name}$options · ${formatMoney(line.lineTotalMinor)}", style = MaterialTheme.typography.bodyLarge)
    }
}

private fun statusText(status: OrderStatus) = when (status) {
    OrderStatus.PLACED -> "Status: received by the kitchen"
    OrderStatus.PREPARING -> "Status: being prepared"
    OrderStatus.READY -> "Status: ready for pickup"
    OrderStatus.COMPLETED -> "Status: picked up"
}
