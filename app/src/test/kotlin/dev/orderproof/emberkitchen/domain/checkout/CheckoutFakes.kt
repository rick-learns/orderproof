package dev.orderproof.emberkitchen.domain.checkout

import dev.orderproof.contract.OrderDto
import dev.orderproof.contract.OrderLineRequest
import dev.orderproof.contract.OrderStatus
import dev.orderproof.contract.PlaceOrderRequest
import dev.orderproof.emberkitchen.domain.ActiveOrderStore
import dev.orderproof.emberkitchen.domain.ApiOutcome
import dev.orderproof.emberkitchen.domain.AttemptStore
import dev.orderproof.emberkitchen.domain.CartStore
import dev.orderproof.emberkitchen.domain.CheckoutGateway
import dev.orderproof.emberkitchen.domain.OrderSummary
import dev.orderproof.emberkitchen.domain.cart.Cart
import dev.orderproof.emberkitchen.domain.cart.CartLine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAttemptStore(var stored: CheckoutAttempt? = null) : AttemptStore {
    override suspend fun load() = stored
    override suspend fun save(attempt: CheckoutAttempt) {
        stored = attempt
    }
    override suspend fun clear() {
        stored = null
    }
}

class FakeCartStore(initial: Cart) : CartStore {
    val state = MutableStateFlow(initial)
    override val cart: Flow<Cart> = state
    override suspend fun update(transform: (Cart) -> Cart) {
        state.value = transform(state.value)
    }
    override suspend fun clear() {
        state.value = Cart()
    }
}

class FakeActiveOrderStore : ActiveOrderStore {
    val state = MutableStateFlow<OrderSummary?>(null)
    override val activeOrder: Flow<OrderSummary?> = state
    override suspend fun save(order: OrderSummary) {
        state.value = order
    }
    override suspend fun clear() {
        state.value = null
    }
}

data class Send(val key: String, val attemptId: String, val request: PlaceOrderRequest, val persistedAtSend: CheckoutAttempt?)

/** Scripted gateway. Each send consumes the next scripted outcome; [gate] can hold a send open. */
class FakeGateway(private val attempts: FakeAttemptStore) : CheckoutGateway {
    val sends = mutableListOf<Send>()
    val lookups = mutableListOf<String>()
    val placeOutcomes = ArrayDeque<ApiOutcome<OrderDto>>()
    val lookupOutcomes = ArrayDeque<ApiOutcome<OrderDto>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun placeOrder(request: PlaceOrderRequest, idempotencyKey: String, attemptId: String): ApiOutcome<OrderDto> {
        sends += Send(idempotencyKey, attemptId, request, attempts.stored)
        gate?.await()
        return placeOutcomes.removeFirst()
    }

    override suspend fun lookupCheckout(idempotencyKey: String): ApiOutcome<OrderDto> {
        lookups += idempotencyKey
        return lookupOutcomes.removeFirst()
    }
}

object Samples {
    val lines = listOf(OrderLineRequest("burger", listOf("patty-single"), 1))
    val cart = Cart(listOf(CartLine("line-1", "burger", "Ember Burger", listOf("patty-single"), listOf("Single"), 1)))

    fun order(id: String = "ord-1", number: String = "EK-1001", total: Long = 1402) = OrderDto(
        orderId = id,
        orderNumber = number,
        status = OrderStatus.PLACED,
        lines = emptyList(),
        subtotalMinor = 1295,
        discountMinor = 0,
        taxMinor = 107,
        totalMinor = total,
        paymentStatus = "CAPTURED",
        placedAt = "2026-06-01T17:00:00Z",
    )

    fun attempt(key: String = "persisted-key", total: Long = 1402, phase: AttemptPhase = AttemptPhase.SENT) = CheckoutAttempt(
        attemptId = "attempt-0",
        idempotencyKey = key,
        lines = lines,
        couponCode = null,
        expectedTotalMinor = total,
        paymentToken = CheckoutAttempt.SIMULATED_CARD_TOKEN,
        phase = phase,
        sendCount = 1,
        createdAtMillis = 0,
    )
}
