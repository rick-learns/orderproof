package dev.orderproof.emberkitchen.ui.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.orderproof.contract.CouponState
import dev.orderproof.emberkitchen.core.formatMoney
import dev.orderproof.emberkitchen.domain.checkout.CheckoutState
import dev.orderproof.emberkitchen.domain.checkout.RetryReason
import dev.orderproof.emberkitchen.ui.TestTags
import dev.orderproof.emberkitchen.ui.cart.QuoteLoad
import dev.orderproof.emberkitchen.ui.components.EmberScaffold
import dev.orderproof.emberkitchen.ui.components.LoadingState
import dev.orderproof.emberkitchen.ui.components.PriceSummary
import dev.orderproof.emberkitchen.ui.components.ProblemCard

/** Everything the review screen can ask for, so [CheckoutContent] stays free of view models. */
data class CheckoutActions(
    val onBack: () -> Unit,
    val onPlace: () -> Unit,
    val onRetry: () -> Unit,
    val onConfirmNewTotal: () -> Unit,
    val onRefreshQuote: () -> Unit,
    val onBackToCart: () -> Unit,
)

/** Stateless review-and-pay screen: renders one [CheckoutUiState]. */
@Composable
fun CheckoutContent(state: CheckoutUiState, actions: CheckoutActions) {
    val submitting = state.submission as? CheckoutState.Submitting
    EmberScaffold(
        title = "Review and pay",
        onBack = if (submitting == null) actions.onBack else null,
        bottomBar = { PlaceOrderBar(state, submitting != null, actions.onPlace) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
                .testTag(TestTags.CHECKOUT_LIST),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SubmissionNotice(state.submission, actions.onRetry)
            state.rejection?.let {
                ProblemCard("Order not placed", it, "Back to cart", actions.onBackToCart, actionTag = TestTags.BACK_TO_CART)
            }
            state.changedFromMinor?.let { previous -> TotalChangedNotice(previous, state, actions.onConfirmNewTotal) }
            when (val quote = state.quote) {
                QuoteLoad.Loading -> LoadingState("Checking prices…", Modifier.padding(vertical = 24.dp))
                QuoteLoad.Empty -> Text("Your cart is empty.", style = MaterialTheme.typography.titleMedium)
                is QuoteLoad.Failed -> ProblemCard("Couldn't price your order", quote.message, "Try again", actions.onRefreshQuote)
                is QuoteLoad.Ready -> {
                    quote.quote.lines.forEach { line ->
                        val options = if (line.optionNames.isEmpty()) "" else line.optionNames.joinToString(prefix = " (", postfix = ")")
                        Text("${line.quantity} × ${line.name}$options", style = MaterialTheme.typography.bodyLarge)
                    }
                    PriceSummary(quote.quote)
                    Text("Payment: simulated card ending 4242. No real money moves.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PlaceOrderBar(state: CheckoutUiState, submitting: Boolean, onPlace: () -> Unit) {
    val total = state.ready?.quote?.totalMinor
    Button(
        onClick = onPlace,
        enabled = state.canPlace,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag(TestTags.PLACE_ORDER),
    ) {
        Text(
            when {
                submitting -> "Placing order…"
                total != null -> "Place order · ${formatMoney(total)}"
                else -> "Place order"
            },
        )
    }
}

@Composable
private fun SubmissionNotice(submission: CheckoutState, onRetry: () -> Unit) {
    when (submission) {
        is CheckoutState.Submitting -> LoadingState(
            if (submission.reconciling) "Checking on your earlier order…" else "Placing your order…",
            Modifier.padding(vertical = 8.dp).testTag(TestTags.SUBMITTING),
        )
        is CheckoutState.NeedsRetry -> ProblemCard(
            title = retryTitle(submission),
            message = retryMessage(submission),
            actionLabel = "Try again",
            onAction = onRetry,
            modifier = Modifier.testTag(TestTags.CHECKOUT_PROBLEM),
            actionTag = TestTags.CHECKOUT_RETRY,
        )
        else -> Unit
    }
}

@Composable
private fun TotalChangedNotice(previousMinor: Long, state: CheckoutUiState, onConfirm: () -> Unit) {
    val quote = state.ready?.quote ?: return
    val couponNote = quote.coupon?.takeIf { it.state != CouponState.APPLIED }?.message?.let { " $it" }.orEmpty()
    Column(Modifier.testTag(TestTags.TOTAL_CHANGED), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProblemCard(
            title = "Your total changed",
            message = "It was ${formatMoney(previousMinor)} and is now ${formatMoney(quote.totalMinor)}.$couponNote " +
                "Nothing has been charged. Confirm the new total to continue.",
            actionLabel = "Confirm new total ${formatMoney(quote.totalMinor)}",
            onAction = onConfirm,
            actionTag = TestTags.CONFIRM_NEW_TOTAL,
        )
        Text("Review the updated breakdown below.", fontWeight = FontWeight.SemiBold)
    }
}

private fun retryTitle(state: CheckoutState.NeedsRetry) = when (state.reason) {
    RetryReason.SERVICE_UNAVAILABLE -> "Ember Kitchen is unavailable"
    else -> "We couldn't confirm your order"
}

private fun retryMessage(state: CheckoutState.NeedsRetry) = when (state.reason) {
    RetryReason.OUTCOME_UNKNOWN ->
        "The connection dropped before we heard back. Trying again is safe: you won't be charged twice."
    RetryReason.SERVICE_UNAVAILABLE ->
        "Your order wasn't placed and you weren't charged. Try again in a moment."
    RetryReason.NOT_PLACED_AFTER_RESTART ->
        "The app closed before your order finished. It wasn't placed, and you weren't charged. Try again to place it."
    RetryReason.RECONCILE_FAILED ->
        "The app closed while placing your order and we can't reach Ember Kitchen to check it. Try again: you won't be charged twice."
}
