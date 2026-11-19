package dev.orderproof.emberkitchen.ui.cart

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orderproof.contract.CouponState
import dev.orderproof.contract.QuoteResponse
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.domain.cart.CartLine
import dev.orderproof.emberkitchen.ui.TestTags
import dev.orderproof.emberkitchen.ui.components.EmberScaffold
import dev.orderproof.emberkitchen.ui.components.LoadingState
import dev.orderproof.emberkitchen.ui.components.PriceSummary
import dev.orderproof.emberkitchen.ui.components.ProblemCard
import dev.orderproof.emberkitchen.ui.item.StepButton

@Composable
fun CartScreen(container: AppContainer, onBack: () -> Unit, onReview: () -> Unit) {
    val vm: CartViewModel = viewModel { CartViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val ready = state.quote as? QuoteLoad.Ready
    val canReview = ready != null && ready.quote.unavailableItemIds.isEmpty()

    EmberScaffold(
        title = "Your cart",
        onBack = onBack,
        bottomBar = {
            if (state.cart.lines.isNotEmpty()) {
                Button(
                    onClick = onReview,
                    enabled = canReview,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag(TestTags.REVIEW_ORDER),
                ) { Text("Review order") }
            }
        },
    ) { padding ->
        if (state.quote == QuoteLoad.Empty) {
            Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your cart is empty.", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onBack) { Text("Browse the menu") }
            }
            return@EmberScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag(TestTags.CART_LIST),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val unavailable = ready?.quote?.unavailableItemIds.orEmpty()
            items(state.cart.lines, key = { it.lineId }) { line ->
                CartLineCard(line, soldOut = line.itemId in unavailable, onQuantity = { vm.setQuantity(line.lineId, it) })
            }
            item(key = "coupon") { CouponEntry(state.cart.couponCode, ready?.quote, vm::applyCoupon) }
            item(key = "summary") {
                when (val quote = state.quote) {
                    QuoteLoad.Loading, QuoteLoad.Empty -> LoadingState("Pricing your order…", Modifier.padding(vertical = 24.dp))
                    is QuoteLoad.Failed -> ProblemCard("Couldn't price your order", quote.message, "Try again", vm::retryQuote)
                    is QuoteLoad.Ready -> PriceSummary(quote.quote)
                }
            }
        }
    }
}

@Composable
private fun CartLineCard(line: CartLine, soldOut: Boolean, onQuantity: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(line.itemName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (line.optionNames.isNotEmpty()) Text(line.optionNames.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            if (soldOut) {
                Text(
                    "Sold out — remove it or lower the quantity to continue.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepButton("−", "Decrease ${line.itemName} quantity", true, "${TestTags.QUANTITY_DECREASE}_${line.itemId}") {
                    onQuantity(line.quantity - 1)
                }
                Text("${line.quantity}", style = MaterialTheme.typography.titleLarge)
                StepButton("+", "Increase ${line.itemName} quantity", line.quantity < 20, "${TestTags.QUANTITY_INCREASE}_${line.itemId}") {
                    onQuantity(line.quantity + 1)
                }
            }
        }
    }
}

@Composable
private fun CouponEntry(applied: String?, quote: QuoteResponse?, onApply: (String) -> Unit) {
    var code by rememberSaveable(applied) { mutableStateOf(applied.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Coupon code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.COUPON_FIELD),
        )
        OutlinedButton(onClick = { onApply(code) }, modifier = Modifier.testTag(TestTags.COUPON_APPLY)) {
            Text(if (code.isBlank() && applied != null) "Remove coupon" else "Apply coupon")
        }
        quote?.coupon?.let { coupon ->
            Text(
                coupon.message,
                color = if (coupon.state == CouponState.APPLIED) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(TestTags.COUPON_STATUS).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
