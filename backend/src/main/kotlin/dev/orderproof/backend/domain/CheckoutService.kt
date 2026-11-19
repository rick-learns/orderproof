package dev.orderproof.backend.domain

import dev.orderproof.backend.storage.CouponRepository
import dev.orderproof.backend.storage.Database
import dev.orderproof.backend.storage.EventContext
import dev.orderproof.backend.storage.EventLog
import dev.orderproof.backend.storage.IdempotencyRepository
import dev.orderproof.backend.storage.MenuRepository
import dev.orderproof.backend.storage.OrderRepository
import dev.orderproof.backend.storage.PaymentRepository
import dev.orderproof.backend.time.BackendClock
import dev.orderproof.contract.ErrorCode
import dev.orderproof.contract.PlaceOrderRequest
import dev.orderproof.contract.QuoteRequest
import java.sql.Connection
import java.util.UUID

sealed interface PlaceOrderResult {
    data class Committed(val order: StoredOrder, val replayed: Boolean) : PlaceOrderResult
    data class Rejected(val code: ErrorCode, val message: String, val cart: PricedCart? = null) : PlaceOrderResult
}

/**
 * Owns the one transaction that matters: idempotency lookup, validation, order + payment
 * insert, stock decrement and key registration all commit together or not at all.
 */
class CheckoutService(
    private val db: Database,
    private val clock: BackendClock,
    private val pricing: PricingEngine,
    private val events: EventLog,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun menu(): List<MenuItem> = db.transaction { MenuRepository.load(it) }

    fun quote(request: QuoteRequest, context: EventContext): PricedCart = db.transaction { conn ->
        val cart = price(conn, request.lines, request.couponCode)
        events.append(conn, "QUOTE_COMPUTED", context, detail = "total=${cart.totalMinor} coupon=${cart.coupon?.state}")
        cart
    }

    fun placeOrder(request: PlaceOrderRequest, key: String, context: EventContext): PlaceOrderResult =
        db.transaction { conn ->
            val fingerprint = RequestFingerprint.of(request)
            IdempotencyRepository.find(conn, key)?.let { existing ->
                return@transaction replay(conn, existing.orderId, existing.requestHash == fingerprint, key, context)
            }
            if (!request.paymentToken.startsWith(SIMULATED_TOKEN_PREFIX)) {
                return@transaction reject(conn, ErrorCode.INVALID_REQUEST, "Only simulated payment tokens are accepted.", context)
            }
            val cart = try {
                price(conn, request.lines, request.couponCode)
            } catch (e: PricingException) {
                return@transaction reject(conn, e.code, e.message.orEmpty(), context)
            }
            if (cart.unavailableItemIds.isNotEmpty()) {
                val names = cart.lines.filter { it.item.id in cart.unavailableItemIds }.map { it.item.name }.distinct()
                return@transaction reject(
                    conn, ErrorCode.ITEM_UNAVAILABLE, "${names.joinToString()} just sold out. You have not been charged.",
                    context, cart,
                )
            }
            if (cart.totalMinor != request.expectedTotalMinor) {
                return@transaction reject(
                    conn, ErrorCode.TOTAL_CHANGED,
                    "Your total changed from ${request.expectedTotalMinor} to ${cart.totalMinor}. Please confirm the new total.",
                    context, cart,
                )
            }
            PlaceOrderResult.Committed(commit(conn, cart, key, fingerprint, context), replayed = false)
        }

    fun findOrder(orderId: String): StoredOrder? = db.transaction { OrderRepository.find(it, orderId) }

    fun findByIdempotencyKey(key: String): StoredOrder? = db.transaction { OrderRepository.findByIdempotencyKey(it, key) }

    fun paymentStatus(orderId: String): String =
        db.transaction { PaymentRepository.findByOrder(it, orderId)?.status } ?: "NONE"

    fun advanceStatus(orderId: String): StoredOrder? = db.transaction { conn ->
        val order = OrderRepository.find(conn, orderId) ?: return@transaction null
        val next = OrderState.entries.getOrNull(order.status.ordinal + 1) ?: order.status
        OrderRepository.updateStatus(conn, orderId, next)
        events.append(conn, "ORDER_STATUS_CHANGED", orderId = orderId, detail = "${order.status} -> $next")
        order.copy(status = next)
    }

    private fun price(conn: Connection, lines: List<dev.orderproof.contract.OrderLineRequest>, couponCode: String?) =
        pricing.price(MenuRepository.load(conn), lines, couponCode, { CouponRepository.find(conn, it) }, clock.nowMillis())

    private fun replay(conn: Connection, orderId: String, sameBody: Boolean, key: String, context: EventContext): PlaceOrderResult {
        if (!sameBody) {
            return reject(conn, ErrorCode.IDEMPOTENCY_KEY_REUSED, "This checkout key was already used for a different order.", context)
        }
        IdempotencyRepository.recordReplay(conn, key)
        events.append(conn, "IDEMPOTENT_REPLAY", context, orderId, "returned existing order; no new order or payment")
        return PlaceOrderResult.Committed(checkNotNull(OrderRepository.find(conn, orderId)), replayed = true)
    }

    private fun commit(conn: Connection, cart: PricedCart, key: String, fingerprint: String, context: EventContext): StoredOrder {
        val now = clock.nowMillis()
        val order = StoredOrder(
            orderId = newId(),
            orderNumber = OrderRepository.nextOrderNumber(conn),
            idempotencyKey = key,
            checkoutAttemptId = context.checkoutAttemptId,
            status = OrderState.PLACED,
            lines = cart.lines.map { line ->
                StoredLine(
                    itemId = line.item.id,
                    itemName = line.item.name,
                    optionIds = line.selected.map { it.id },
                    optionNames = line.selected.map { it.name },
                    quantity = line.quantity,
                    unitPriceMinor = line.unitPriceMinor,
                    lineTotalMinor = line.lineTotalMinor,
                )
            },
            subtotalMinor = cart.subtotalMinor,
            discountMinor = cart.discountMinor,
            taxMinor = cart.taxMinor,
            totalMinor = cart.totalMinor,
            couponCode = cart.coupon?.takeIf { it.discountMinor > 0 }?.code,
            createdAtMillis = now,
        )
        OrderRepository.insert(conn, order)
        cart.lines.groupBy { it.item.id }.forEach { (itemId, lines) ->
            MenuRepository.decrementStock(conn, itemId, lines.sumOf { it.quantity })
        }
        val payment = StoredPayment(newId(), order.orderId, order.totalMinor, "CAPTURED", "SIMULATED_CARD", now)
        PaymentRepository.insert(conn, payment)
        IdempotencyRepository.insert(conn, key, order.orderId, fingerprint)
        events.append(conn, "ORDER_COMMITTED", context, order.orderId, "number=${order.orderNumber} total=${order.totalMinor}")
        events.append(conn, "PAYMENT_CAPTURED", context, order.orderId, "payment=${payment.paymentId} amount=${payment.amountMinor}")
        return order
    }

    private fun reject(
        conn: Connection,
        code: ErrorCode,
        message: String,
        context: EventContext,
        cart: PricedCart? = null,
    ): PlaceOrderResult.Rejected {
        events.append(conn, "ORDER_REJECTED", context, detail = "$code: $message")
        return PlaceOrderResult.Rejected(code, message, cart)
    }

    companion object {
        const val SIMULATED_TOKEN_PREFIX = "sim_"
    }
}
