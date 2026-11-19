package dev.orderproof.emberkitchen.domain.checkout

import dev.orderproof.contract.OrderLineRequest
import dev.orderproof.contract.PlaceOrderRequest
import kotlinx.serialization.Serializable

@Serializable
enum class AttemptPhase {
    /** Persisted, not yet handed to the network. */
    PREPARED,

    /** A send started; the outcome is not known yet. */
    SENT,

    /** A send ended without a definitive answer (lost response, timeout, 5xx, outage). */
    OUTCOME_UNKNOWN,
}

/**
 * One customer intent to pay for one exact cart at one confirmed total. It is written to disk
 * before the first network send, so retries and a relaunched process refer to the same operation.
 */
@Serializable
data class CheckoutAttempt(
    val attemptId: String,
    val idempotencyKey: String,
    val lines: List<OrderLineRequest>,
    val couponCode: String?,
    val expectedTotalMinor: Long,
    val paymentToken: String,
    val phase: AttemptPhase,
    val sendCount: Int,
    val createdAtMillis: Long,
) {
    fun toRequest() = PlaceOrderRequest(lines, couponCode, expectedTotalMinor, paymentToken)

    fun matches(lines: List<OrderLineRequest>, couponCode: String?, expectedTotalMinor: Long) =
        this.lines == lines && this.couponCode == couponCode && this.expectedTotalMinor == expectedTotalMinor

    companion object {
        /** Simulated payment method; the backend refuses anything that is not a `sim_` token. */
        const val SIMULATED_CARD_TOKEN = "sim_visa_4242"
    }
}
