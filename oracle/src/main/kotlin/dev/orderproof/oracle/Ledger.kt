package dev.orderproof.oracle

import kotlinx.serialization.Serializable

/** Raw persisted rows as the oracle reads them. No backend types are reused. */
@Serializable
data class Ledger(
    val orders: List<Order>,
    val payments: List<Payment>,
    val idempotency: List<IdempotencyRecord>,
    val stock: Map<String, Int>,
) {
    @Serializable
    data class Order(
        val orderId: String,
        val orderNumber: String,
        val idempotencyKey: String,
        val checkoutAttemptId: String?,
        val subtotalMinor: Long,
        val discountMinor: Long,
        val taxMinor: Long,
        val totalMinor: Long,
        val couponCode: String?,
        val lines: List<Line>,
    )

    @Serializable
    data class Line(val itemId: String, val optionIds: List<String>, val quantity: Int, val unitPriceMinor: Long, val lineTotalMinor: Long)

    @Serializable
    data class Payment(val paymentId: String, val orderId: String, val amountMinor: Long, val status: String, val method: String)

    @Serializable
    data class IdempotencyRecord(val key: String, val orderId: String, val replayCount: Int)
}
