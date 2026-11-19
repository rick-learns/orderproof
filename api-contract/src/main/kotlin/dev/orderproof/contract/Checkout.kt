package dev.orderproof.contract

import kotlinx.serialization.Serializable

@Serializable
data class OrderLineRequest(
    val itemId: String,
    val optionIds: List<String>,
    val quantity: Int,
)

@Serializable
data class QuoteRequest(
    val lines: List<OrderLineRequest>,
    val couponCode: String? = null,
)

/** Server-authoritative pricing. Clients display it; they never compute totals themselves. */
@Serializable
data class QuoteResponse(
    val lines: List<QuotedLine>,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val coupon: CouponStatusDto? = null,
    val unavailableItemIds: List<String> = emptyList(),
)

@Serializable
data class QuotedLine(
    val itemId: String,
    val name: String,
    val optionIds: List<String>,
    val optionNames: List<String>,
    val quantity: Int,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
)

@Serializable
data class CouponStatusDto(
    val code: String,
    val state: CouponState,
    val message: String,
)

@Serializable
enum class CouponState { APPLIED, EXPIRED, NOT_FOUND, MINIMUM_NOT_MET }

/**
 * Submitted with an `Idempotency-Key` header. [expectedTotalMinor] is the total the customer
 * confirmed; the server rejects the order with [ErrorCode.TOTAL_CHANGED] if its own total differs.
 */
@Serializable
data class PlaceOrderRequest(
    val lines: List<OrderLineRequest>,
    val couponCode: String? = null,
    val expectedTotalMinor: Long,
    val paymentToken: String,
)
