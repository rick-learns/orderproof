package dev.orderproof.backend.domain

data class MenuItem(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val basePriceMinor: Long,
    val stock: Int,
    val optionGroups: List<OptionGroup>,
)

data class OptionGroup(
    val id: String,
    val name: String,
    val required: Boolean,
    val maxSelections: Int,
    val options: List<MenuOption>,
)

data class MenuOption(
    val id: String,
    val name: String,
    val priceDeltaMinor: Long,
)

enum class CouponKind { PERCENT, FIXED }

data class Coupon(
    val code: String,
    val kind: CouponKind,
    /** Percent (0-100) for [CouponKind.PERCENT], minor units for [CouponKind.FIXED]. */
    val value: Long,
    val minSubtotalMinor: Long,
    val expiresAtMillis: Long?,
)

enum class OrderState { PLACED, PREPARING, READY, COMPLETED }

data class StoredLine(
    val itemId: String,
    val itemName: String,
    val optionIds: List<String>,
    val optionNames: List<String>,
    val quantity: Int,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
)

data class StoredOrder(
    val orderId: String,
    val orderNumber: String,
    val idempotencyKey: String,
    val checkoutAttemptId: String?,
    val status: OrderState,
    val lines: List<StoredLine>,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val couponCode: String?,
    val createdAtMillis: Long,
)

data class StoredPayment(
    val paymentId: String,
    val orderId: String,
    val amountMinor: Long,
    val status: String,
    val method: String,
    val createdAtMillis: Long,
)
