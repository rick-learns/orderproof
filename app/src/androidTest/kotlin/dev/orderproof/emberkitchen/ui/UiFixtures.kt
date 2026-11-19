package dev.orderproof.emberkitchen.ui

import dev.orderproof.contract.CouponState
import dev.orderproof.contract.CouponStatusDto
import dev.orderproof.contract.OrderLineRequest
import dev.orderproof.contract.QuoteResponse
import dev.orderproof.contract.QuotedLine
import dev.orderproof.emberkitchen.domain.checkout.AttemptPhase
import dev.orderproof.emberkitchen.domain.checkout.CheckoutAttempt
import dev.orderproof.emberkitchen.ui.cart.QuoteLoad
import dev.orderproof.emberkitchen.ui.checkout.CheckoutActions
import dev.orderproof.emberkitchen.ui.checkout.CheckoutUiState

/** Hand-written server answers (seed prices, 8.25% tax) for rendering screens in isolation. */
object UiFixtures {
    private val burger = QuotedLine("ember-smash-burger", "Ember Smash Burger", listOf("patty-single"), listOf("Single"), 1, 1295, 1295)

    /** 1 × burger: 1295 + 107 tax = 1402. */
    val singleBurger = QuoteResponse(listOf(burger), subtotalMinor = 1295, discountMinor = 0, taxMinor = 107, totalMinor = 1402)

    /** 2 × burger with EMBER10: 2590 − 259 = 2331, tax 192, total 2523. */
    val twoBurgersWithCoupon = QuoteResponse(
        listOf(burger.copy(quantity = 2, lineTotalMinor = 2590)), 2590, 259, 192, 2523,
        CouponStatusDto("EMBER10", CouponState.APPLIED, "EMBER10 applied."),
    )

    /** The same cart after EMBER10 expired: 2590 + 214 = 2804. */
    val twoBurgersCouponExpired = QuoteResponse(
        listOf(burger.copy(quantity = 2, lineTotalMinor = 2590)), 2590, 0, 214, 2804,
        CouponStatusDto("EMBER10", CouponState.EXPIRED, "EMBER10 has expired."),
    )

    fun ready(quote: QuoteResponse) = CheckoutUiState(quote = QuoteLoad.Ready(quote), acceptedTotalMinor = quote.totalMinor)

    val attempt = CheckoutAttempt(
        attemptId = "attempt-1", idempotencyKey = "key-1",
        lines = listOf(OrderLineRequest("ember-smash-burger", listOf("patty-single"), 1)),
        couponCode = null, expectedTotalMinor = 1402, paymentToken = CheckoutAttempt.SIMULATED_CARD_TOKEN,
        phase = AttemptPhase.OUTCOME_UNKNOWN, sendCount = 1, createdAtMillis = 0,
    )
}

/** Counts each callback so tests can assert exactly what a tap triggered. */
class RecordingActions {
    val calls = mutableListOf<String>()
    val actions = CheckoutActions(
        onBack = { calls += "back" },
        onPlace = { calls += "place" },
        onRetry = { calls += "retry" },
        onConfirmNewTotal = { calls += "confirmNewTotal" },
        onRefreshQuote = { calls += "refreshQuote" },
        onBackToCart = { calls += "backToCart" },
    )
}
