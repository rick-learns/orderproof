package dev.orderproof.emberkitchen.domain

import dev.orderproof.emberkitchen.domain.cart.Cart
import dev.orderproof.emberkitchen.domain.checkout.CheckoutAttempt
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Persistent stores. Writes return only once the value is durable on disk. */
interface CartStore {
    val cart: Flow<Cart>
    suspend fun update(transform: (Cart) -> Cart)
    suspend fun clear()
}

interface AttemptStore {
    suspend fun load(): CheckoutAttempt?
    suspend fun save(attempt: CheckoutAttempt)
    suspend fun clear()
}

@Serializable
data class OrderSummary(
    val orderId: String,
    val orderNumber: String,
    val totalMinor: Long,
)

interface ActiveOrderStore {
    val activeOrder: Flow<OrderSummary?>
    suspend fun save(order: OrderSummary)
    suspend fun clear()
}
