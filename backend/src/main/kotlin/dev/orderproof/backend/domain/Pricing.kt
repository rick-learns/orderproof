package dev.orderproof.backend.domain

import dev.orderproof.contract.CouponState
import dev.orderproof.contract.ErrorCode
import dev.orderproof.contract.OrderLineRequest

data class PricedLine(
    val item: MenuItem,
    val selected: List<MenuOption>,
    val quantity: Int,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
)

data class CouponOutcome(val code: String, val state: CouponState, val discountMinor: Long)

data class PricedCart(
    val lines: List<PricedLine>,
    val subtotalMinor: Long,
    val coupon: CouponOutcome?,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    /** Items whose remaining stock cannot cover the requested quantity. */
    val unavailableItemIds: List<String>,
)

class PricingException(val code: ErrorCode, message: String) : Exception(message)

/** Integer rounding half-up for non-negative values: never touches floating point. */
internal fun roundHalfUp(numerator: Long, denominator: Long): Long {
    require(numerator >= 0 && denominator > 0)
    return (numerator * 2 + denominator) / (denominator * 2)
}

/**
 * The server's authoritative pricing. Rules (see docs/behavior-contract.md):
 * unit = base + option deltas; line = unit × qty; discount from coupon on the subtotal;
 * tax = 8.25% of (subtotal − discount), rounded half-up; total = subtotal − discount + tax.
 */
class PricingEngine(private val taxBasisPoints: Long) {

    fun price(
        menu: List<MenuItem>,
        lines: List<OrderLineRequest>,
        couponCode: String?,
        findCoupon: (String) -> Coupon?,
        nowMillis: Long,
    ): PricedCart {
        if (lines.isEmpty()) throw PricingException(ErrorCode.INVALID_REQUEST, "An order needs at least one item.")
        val byId = menu.associateBy { it.id }
        val priced = lines.map { priceLine(byId, it) }
        val subtotal = priced.sumOf { it.lineTotalMinor }
        val coupon = couponCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            ?.let { applyCoupon(it, findCoupon(it), subtotal, nowMillis) }
        val discount = coupon?.discountMinor ?: 0
        val taxable = subtotal - discount
        val tax = roundHalfUp(taxable * taxBasisPoints, 10_000)
        val requested = priced.groupBy { it.item.id }.mapValues { (_, l) -> l.sumOf { it.quantity } }
        val unavailable = requested.filter { (id, qty) -> byId.getValue(id).stock < qty }.keys.toList()
        return PricedCart(priced, subtotal, coupon, discount, tax, taxable + tax, unavailable)
    }

    private fun priceLine(menu: Map<String, MenuItem>, line: OrderLineRequest): PricedLine {
        val item = menu[line.itemId] ?: throw PricingException(ErrorCode.UNKNOWN_ITEM, "Unknown item ${line.itemId}.")
        if (line.quantity !in 1..MAX_QUANTITY) {
            throw PricingException(ErrorCode.INVALID_REQUEST, "Quantity must be between 1 and $MAX_QUANTITY.")
        }
        if (line.optionIds.toSet().size != line.optionIds.size) {
            throw PricingException(ErrorCode.INVALID_OPTIONS, "Duplicate options for ${item.name}.")
        }
        val selected = line.optionIds.map { optionId ->
            item.optionGroups.flatMap { it.options }.firstOrNull { it.id == optionId }
                ?: throw PricingException(ErrorCode.INVALID_OPTIONS, "Option $optionId does not belong to ${item.name}.")
        }
        item.optionGroups.forEach { group ->
            val chosen = group.options.count { it in selected }
            if (group.required && chosen != 1) {
                throw PricingException(ErrorCode.INVALID_OPTIONS, "Choose one ${group.name} for ${item.name}.")
            }
            if (chosen > group.maxSelections) {
                throw PricingException(ErrorCode.INVALID_OPTIONS, "Too many ${group.name} for ${item.name}.")
            }
        }
        val unit = item.basePriceMinor + selected.sumOf { it.priceDeltaMinor }
        return PricedLine(item, selected, line.quantity, unit, unit * line.quantity)
    }

    private fun applyCoupon(code: String, coupon: Coupon?, subtotal: Long, nowMillis: Long): CouponOutcome = when {
        coupon == null -> CouponOutcome(code, CouponState.NOT_FOUND, 0)
        coupon.expiresAtMillis != null && nowMillis >= coupon.expiresAtMillis -> CouponOutcome(code, CouponState.EXPIRED, 0)
        subtotal < coupon.minSubtotalMinor -> CouponOutcome(code, CouponState.MINIMUM_NOT_MET, 0)
        else -> {
            val discount = when (coupon.kind) {
                CouponKind.PERCENT -> roundHalfUp(subtotal * coupon.value, 100)
                CouponKind.FIXED -> coupon.value
            }
            CouponOutcome(code, CouponState.APPLIED, minOf(discount, subtotal))
        }
    }

    companion object {
        const val MAX_QUANTITY = 20
    }
}
