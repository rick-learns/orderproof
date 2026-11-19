package dev.orderproof.backend.domain

import dev.orderproof.backend.seed.SeedCatalog
import dev.orderproof.contract.CouponState
import dev.orderproof.contract.CouponStatusDto
import dev.orderproof.contract.MenuItemDto
import dev.orderproof.contract.MenuResponse
import dev.orderproof.contract.OptionDto
import dev.orderproof.contract.OptionGroupDto
import dev.orderproof.contract.OrderDto
import dev.orderproof.contract.OrderStatus
import dev.orderproof.contract.QuoteResponse
import dev.orderproof.contract.QuotedLine
import java.time.Instant

fun List<MenuItem>.toMenuResponse() = MenuResponse(
    restaurant = SeedCatalog.RESTAURANT,
    currency = SeedCatalog.CURRENCY,
    items = map { item ->
        MenuItemDto(
            id = item.id,
            name = item.name,
            description = item.description,
            category = item.category,
            basePriceMinor = item.basePriceMinor,
            available = item.stock > 0,
            optionGroups = item.optionGroups.map { group ->
                OptionGroupDto(
                    id = group.id,
                    name = group.name,
                    required = group.required,
                    maxSelections = group.maxSelections,
                    options = group.options.map { OptionDto(it.id, it.name, it.priceDeltaMinor) },
                )
            },
        )
    },
)

fun PricedCart.toQuoteResponse() = QuoteResponse(
    lines = lines.map { line ->
        QuotedLine(
            itemId = line.item.id,
            name = line.item.name,
            optionIds = line.selected.map { it.id },
            optionNames = line.selected.map { it.name },
            quantity = line.quantity,
            unitPriceMinor = line.unitPriceMinor,
            lineTotalMinor = line.lineTotalMinor,
        )
    },
    subtotalMinor = subtotalMinor,
    discountMinor = discountMinor,
    taxMinor = taxMinor,
    totalMinor = totalMinor,
    coupon = coupon?.let { CouponStatusDto(it.code, it.state, couponMessage(it)) },
    unavailableItemIds = unavailableItemIds,
)

private fun couponMessage(outcome: CouponOutcome) = when (outcome.state) {
    CouponState.APPLIED -> "${outcome.code} applied."
    CouponState.EXPIRED -> "${outcome.code} has expired."
    CouponState.NOT_FOUND -> "${outcome.code} is not a valid code."
    CouponState.MINIMUM_NOT_MET -> "${outcome.code} needs a larger order."
}

fun StoredOrder.toOrderDto(paymentStatus: String) = OrderDto(
    orderId = orderId,
    orderNumber = orderNumber,
    status = OrderStatus.valueOf(status.name),
    lines = lines.map {
        QuotedLine(it.itemId, it.itemName, it.optionIds, it.optionNames, it.quantity, it.unitPriceMinor, it.lineTotalMinor)
    },
    subtotalMinor = subtotalMinor,
    discountMinor = discountMinor,
    taxMinor = taxMinor,
    totalMinor = totalMinor,
    couponCode = couponCode,
    paymentStatus = paymentStatus,
    placedAt = Instant.ofEpochMilli(createdAtMillis).toString(),
)
