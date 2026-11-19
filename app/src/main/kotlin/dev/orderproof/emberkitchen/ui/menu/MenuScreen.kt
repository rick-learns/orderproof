package dev.orderproof.emberkitchen.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orderproof.contract.MenuItemDto
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.core.formatMoney
import dev.orderproof.emberkitchen.domain.OrderSummary
import dev.orderproof.emberkitchen.ui.TestTags
import dev.orderproof.emberkitchen.ui.components.EmberScaffold
import dev.orderproof.emberkitchen.ui.components.ErrorState
import dev.orderproof.emberkitchen.ui.components.LoadingState
import dev.orderproof.emberkitchen.ui.theme.EmberColors

@Composable
fun MenuScreen(
    container: AppContainer,
    onOpenItem: (String) -> Unit,
    onOpenCart: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: MenuViewModel = viewModel { MenuViewModel(container) }
    val load by vm.menu.collectAsStateWithLifecycle()
    val cartCount by vm.cartCount.collectAsStateWithLifecycle()
    val activeOrder by vm.activeOrder.collectAsStateWithLifecycle()

    EmberScaffold(
        title = "Ember Kitchen",
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Backend connection settings") }
        },
        bottomBar = {
            if (cartCount > 0) {
                Button(
                    onClick = onOpenCart,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag(TestTags.VIEW_CART),
                ) { Text("View cart · $cartCount ${if (cartCount == 1) "item" else "items"}") }
            }
        },
    ) { padding ->
        when (val state = load) {
            MenuLoad.Loading -> LoadingState("Loading the menu…", Modifier.padding(padding))
            is MenuLoad.Failed -> ErrorState(
                title = "Menu unavailable",
                message = state.message,
                actionLabel = "Try again",
                onAction = vm::load,
                modifier = Modifier.padding(padding),
                tag = TestTags.MENU_ERROR,
                actionTag = TestTags.MENU_RETRY,
            )
            is MenuLoad.Loaded -> MenuList(state, activeOrder, padding, onOpenItem, onOpenOrder)
        }
    }
}

@Composable
private fun MenuList(
    state: MenuLoad.Loaded,
    activeOrder: OrderSummary?,
    padding: PaddingValues,
    onOpenItem: (String) -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    val byCategory = state.menu.items.groupBy { it.category }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag(TestTags.MENU_LIST),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (activeOrder != null) {
            item(key = "active-order") { ActiveOrderCard(activeOrder, onOpenOrder) }
        }
        byCategory.forEach { (category, items) ->
            item(key = "header-$category") {
                Text(
                    category,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            items(items, key = { it.id }) { MenuItemCard(it, onOpenItem) }
        }
    }
}

@Composable
private fun ActiveOrderCard(order: OrderSummary, onOpenOrder: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(TestTags.ACTIVE_ORDER_BANNER)
            .clickable(role = Role.Button, onClickLabel = "Track order") { onOpenOrder(order.orderId) },
        colors = CardDefaults.cardColors(containerColor = EmberColors.Charcoal, contentColor = EmberColors.Cream),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your order ${order.orderNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${formatMoney(order.totalMinor)} paid · Tap to track it", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MenuItemCard(item: MenuItemDto, onOpenItem: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(TestTags.menuItem(item.id))
            .semantics(mergeDescendants = true) {}
            .clickable(enabled = item.available, role = Role.Button, onClickLabel = "Customize") { onOpenItem(item.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Text(formatMoney(item.basePriceMinor), style = MaterialTheme.typography.titleMedium, color = EmberColors.Ember)
            }
            Text(item.description, style = MaterialTheme.typography.bodyMedium)
            if (!item.available) {
                Text("Sold out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}
