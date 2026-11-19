package dev.orderproof.contract

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    INVALID_REQUEST,
    UNKNOWN_ITEM,
    INVALID_OPTIONS,
    ITEM_UNAVAILABLE,
    TOTAL_CHANGED,
    MISSING_IDEMPOTENCY_KEY,
    IDEMPOTENCY_KEY_REUSED,
    SERVICE_UNAVAILABLE,
    NOT_FOUND,
    INTERNAL_ERROR,
}

/**
 * Error body for every non-2xx public response. Rejections carrying a fresh [quote] let the
 * client show the updated total and ask the customer to confirm it explicitly.
 */
@Serializable
data class ApiError(
    val code: ErrorCode,
    val message: String,
    val quote: QuoteResponse? = null,
    val unavailableItemIds: List<String> = emptyList(),
)
