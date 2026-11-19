package dev.orderproof.control

import kotlinx.serialization.Serializable

/** Raw persisted records, read directly from backend storage for independent verification. */
@Serializable
data class LedgerSnapshot(
    val orders: List<LedgerOrder>,
    val payments: List<LedgerPayment>,
    val idempotencyRecords: List<LedgerIdempotencyRecord>,
    val stock: List<StockLevel>,
    val clockEpochMillis: Long,
)

@Serializable
data class LedgerOrder(
    val orderId: String,
    val orderNumber: String,
    val idempotencyKey: String,
    val checkoutAttemptId: String? = null,
    val status: String,
    val lines: List<LedgerLine>,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val couponCode: String? = null,
    val createdAtMillis: Long,
)

@Serializable
data class LedgerLine(
    val itemId: String,
    val optionIds: List<String>,
    val quantity: Int,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
)

@Serializable
data class LedgerPayment(
    val paymentId: String,
    val orderId: String,
    val amountMinor: Long,
    val status: String,
    val method: String,
    val createdAtMillis: Long,
)

@Serializable
data class LedgerIdempotencyRecord(
    val idempotencyKey: String,
    val orderId: String,
    val requestHash: String,
    val replayCount: Int,
)

@Serializable
data class StockLevel(
    val itemId: String,
    val stock: Int,
)

/** Append-only backend event, correlated with app logs through request/attempt identifiers. */
@Serializable
data class BackendEvent(
    val seq: Long,
    val atMillis: Long,
    val wallClockMillis: Long,
    val kind: String,
    val requestId: String? = null,
    val idempotencyKey: String? = null,
    val checkoutAttemptId: String? = null,
    val orderId: String? = null,
    val detail: String = "",
)

@Serializable
data class EventsResponse(
    val events: List<BackendEvent>,
)
