package dev.orderproof.emberkitchen.domain.cart

import dev.orderproof.contract.OrderLineRequest
import kotlinx.serialization.Serializable

@Serializable
data class CartLine(
    val lineId: String,
    val itemId: String,
    val itemName: String,
    val optionIds: List<String>,
    val optionNames: List<String>,
    val quantity: Int,
)

/** The customer's selections. Prices are never stored here: the backend quote is authoritative. */
@Serializable
data class Cart(
    val lines: List<CartLine> = emptyList(),
    val couponCode: String? = null,
) {
    val itemCount: Int get() = lines.sumOf { it.quantity }

    fun toOrderLines(): List<OrderLineRequest> = lines.map { OrderLineRequest(it.itemId, it.optionIds, it.quantity) }

    /** Identical item + options merge into one line. */
    fun add(line: CartLine): Cart {
        val same = lines.firstOrNull { it.itemId == line.itemId && it.optionIds.sorted() == line.optionIds.sorted() }
        return if (same == null) {
            copy(lines = lines + line)
        } else {
            copy(lines = lines.map { if (it.lineId == same.lineId) it.copy(quantity = it.quantity + line.quantity) else it })
        }
    }

    fun setQuantity(lineId: String, quantity: Int): Cart = copy(
        lines = if (quantity <= 0) {
            lines.filterNot { it.lineId == lineId }
        } else {
            lines.map { if (it.lineId == lineId) it.copy(quantity = quantity) else it }
        },
    )
}
