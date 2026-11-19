package dev.orderproof.emberkitchen.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.orderproof.contract.ErrorCode
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.core.AppLog
import dev.orderproof.emberkitchen.domain.cart.Cart
import dev.orderproof.emberkitchen.domain.checkout.CheckoutState
import dev.orderproof.emberkitchen.ui.cart.QuoteLoad
import dev.orderproof.emberkitchen.ui.cart.fetchQuote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckoutUiState(
    val cart: Cart = Cart(),
    val quote: QuoteLoad = QuoteLoad.Loading,
    /** The total the customer has seen and accepted; the order is placed only at this total. */
    val acceptedTotalMinor: Long? = null,
    /** Set when the server total moved away from [acceptedTotalMinor]; blocks placing until confirmed. */
    val changedFromMinor: Long? = null,
    /** A definitive rejection the customer needs to read (e.g. an item sold out). */
    val rejection: String? = null,
    val submission: CheckoutState = CheckoutState.Idle,
) {
    val ready: QuoteLoad.Ready? get() = quote as? QuoteLoad.Ready
    val canPlace: Boolean
        get() = ready != null && ready!!.quote.unavailableItemIds.isEmpty() && changedFromMinor == null &&
            acceptedTotalMinor == ready!!.quote.totalMinor && submission == CheckoutState.Idle
}

class CheckoutViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch { container.checkout.state.collect(::onSubmission) }
        refreshQuote("open")
    }

    /** Called whenever the screen returns to the foreground: prices and coupons may have changed meanwhile. */
    fun onResumed() {
        if (state.value.submission == CheckoutState.Idle && state.value.quote !is QuoteLoad.Loading) refreshQuote("resume")
    }

    fun refreshQuote(reason: String = "manual") {
        mutable.update { it.copy(quote = QuoteLoad.Loading) }
        viewModelScope.launch {
            val cart = container.cart.cart.first()
            val quote = if (cart.lines.isEmpty()) QuoteLoad.Empty else fetchQuote(container, cart)
            mutable.update { current -> withQuote(current.copy(cart = cart), quote, reason) }
        }
    }

    fun confirmNewTotal() = mutable.update { current ->
        val total = current.ready?.quote?.totalMinor ?: return@update current
        AppLog.i("checkout.total_confirmed previous=${current.changedFromMinor} accepted=$total")
        current.copy(acceptedTotalMinor = total, changedFromMinor = null)
    }

    fun placeOrder() {
        val current = state.value
        val total = current.acceptedTotalMinor
        if (!current.canPlace || total == null) {
            AppLog.i("checkout.place_ignored reason=not_placeable")
            return
        }
        container.checkout.placeOrder(current.cart.toOrderLines(), current.cart.couponCode, total)
    }

    fun retry() {
        container.checkout.retry()
    }

    fun dismissRejection() {
        mutable.update { it.copy(rejection = null) }
        refreshQuote("after_rejection")
    }

    fun onConfirmationShown() = container.checkout.acknowledge()

    private fun withQuote(current: CheckoutUiState, quote: QuoteLoad, reason: String): CheckoutUiState {
        val ready = quote as? QuoteLoad.Ready ?: return current.copy(quote = quote)
        val total = ready.quote.totalMinor
        val accepted = current.acceptedTotalMinor
        return when {
            accepted == null -> current.copy(quote = quote, acceptedTotalMinor = total)
            accepted != total -> {
                AppLog.i("checkout.total_changed reason=$reason accepted=$accepted server=$total coupon=${ready.quote.coupon?.state}")
                current.copy(quote = quote, changedFromMinor = current.changedFromMinor ?: accepted)
            }
            else -> current.copy(quote = quote, changedFromMinor = null)
        }
    }

    private fun onSubmission(submission: CheckoutState) {
        when (submission) {
            is CheckoutState.TotalChanged -> {
                mutable.update {
                    it.copy(
                        quote = QuoteLoad.Ready(submission.quote),
                        changedFromMinor = submission.previousTotalMinor,
                        acceptedTotalMinor = submission.previousTotalMinor,
                        submission = CheckoutState.Idle,
                    )
                }
                container.checkout.acknowledge()
            }
            is CheckoutState.Rejected -> {
                val message = when (submission.code) {
                    ErrorCode.ITEM_UNAVAILABLE -> "Sorry — something in your order just sold out. You weren't charged. " +
                        "Update your cart to continue."
                    else -> "${submission.message} You weren't charged."
                }
                mutable.update { it.copy(rejection = message, submission = CheckoutState.Idle) }
                container.checkout.acknowledge()
                refreshQuote("after_rejection")
            }
            else -> mutable.update { it.copy(submission = submission) }
        }
    }
}
