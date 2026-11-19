package dev.orderproof.emberkitchen.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.orderproof.emberkitchen.domain.ActiveOrderStore
import dev.orderproof.emberkitchen.domain.AttemptStore
import dev.orderproof.emberkitchen.domain.CartStore
import dev.orderproof.emberkitchen.domain.OrderSummary
import dev.orderproof.emberkitchen.domain.cart.Cart
import dev.orderproof.emberkitchen.domain.checkout.CheckoutAttempt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreCartStore(dataStore: DataStore<Preferences>) : CartStore {
    private val slot = JsonSlot(dataStore, "cart", Cart.serializer())
    override val cart: Flow<Cart> = slot.flow.map { it ?: Cart() }
    override suspend fun update(transform: (Cart) -> Cart) = slot.update(Cart(), transform)
    override suspend fun clear() = slot.clear()
}

class DataStoreAttemptStore(dataStore: DataStore<Preferences>) : AttemptStore {
    private val slot = JsonSlot(dataStore, "checkout_attempt", CheckoutAttempt.serializer())
    override suspend fun load(): CheckoutAttempt? = slot.read()
    override suspend fun save(attempt: CheckoutAttempt) = slot.write(attempt)
    override suspend fun clear() = slot.clear()
}

class DataStoreActiveOrderStore(dataStore: DataStore<Preferences>) : ActiveOrderStore {
    private val slot = JsonSlot(dataStore, "active_order", OrderSummary.serializer())
    override val activeOrder: Flow<OrderSummary?> = slot.flow
    override suspend fun save(order: OrderSummary) = slot.write(order)
    override suspend fun clear() = slot.clear()
}
