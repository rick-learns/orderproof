package dev.orderproof.emberkitchen.domain.checkout

import dev.orderproof.contract.ErrorCode
import dev.orderproof.contract.OrderDto
import dev.orderproof.contract.QuoteResponse

enum class RetryReason {
    /** Sent, but no definitive answer. Retrying with the same key is safe. */
    OUTCOME_UNKNOWN,

    /** Backend refused or could not be reached; nothing was placed. */
    SERVICE_UNAVAILABLE,

    /** After a relaunch the backend has no order for this attempt. */
    NOT_PLACED_AFTER_RESTART,

    /** After a relaunch we could not ask the backend about the attempt. */
    RECONCILE_FAILED,
}

sealed interface CheckoutState {
    data object Idle : CheckoutState

    data class Submitting(val attempt: CheckoutAttempt, val reconciling: Boolean = false) : CheckoutState

    data class Confirmed(val order: OrderDto) : CheckoutState

    data class NeedsRetry(val attempt: CheckoutAttempt, val reason: RetryReason) : CheckoutState

    /** Definitive rejection; the attempt is closed and the customer was not charged. */
    data class Rejected(val code: ErrorCode, val message: String) : CheckoutState

    /** Server total differs from the one the customer confirmed; needs explicit re-confirmation. */
    data class TotalChanged(val quote: QuoteResponse, val previousTotalMinor: Long) : CheckoutState
}
