package dev.orderproof.emberkitchen.ui.checkout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.domain.checkout.CheckoutState

@Composable
fun CheckoutScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onBackToCart: () -> Unit,
    onConfirmed: (String) -> Unit,
) {
    val vm: CheckoutViewModel = viewModel { CheckoutViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResumed() }

    val confirmed = state.submission as? CheckoutState.Confirmed
    LaunchedEffect(confirmed) {
        if (confirmed != null) {
            onConfirmed(confirmed.order.orderId)
            vm.onConfirmationShown()
        }
    }

    CheckoutContent(
        state,
        CheckoutActions(
            onBack = onBack,
            onPlace = vm::placeOrder,
            onRetry = vm::retry,
            onConfirmNewTotal = vm::confirmNewTotal,
            onRefreshQuote = { vm.refreshQuote() },
            onBackToCart = { vm.dismissRejection(); onBackToCart() },
        ),
    )
}
