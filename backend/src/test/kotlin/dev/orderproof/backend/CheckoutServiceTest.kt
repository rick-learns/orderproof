package dev.orderproof.backend

import com.google.common.truth.Truth.assertThat
import dev.orderproof.backend.control.LedgerReader
import dev.orderproof.backend.domain.PlaceOrderResult
import dev.orderproof.backend.storage.EventContext
import dev.orderproof.backend.storage.MenuRepository
import dev.orderproof.backend.time.FixedClock
import dev.orderproof.contract.ErrorCode
import dev.orderproof.contract.OrderLineRequest
import dev.orderproof.contract.PlaceOrderRequest
import org.junit.After
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CheckoutServiceTest {
    private val db = tempDb()
    private var app = startTestBackend(db, withControl = false)
    private val ledger get() = LedgerReader(app.db) { 0 }.snapshot()
    private val ctx = EventContext(requestId = "r1", checkoutAttemptId = "attempt-1")

    @After
    fun tearDown() = app.close()

    @Test
    fun `commit writes exactly one order and one matching payment`() {
        val result = app.checkout.placeOrder(singleBurgerOrder(), "key-1", ctx)

        assertThat(result).isInstanceOf(PlaceOrderResult.Committed::class.java)
        val snapshot = ledger
        assertThat(snapshot.orders).hasSize(1)
        assertThat(snapshot.payments).hasSize(1)
        assertThat(snapshot.payments.single().orderId).isEqualTo(snapshot.orders.single().orderId)
        assertThat(snapshot.payments.single().amountMinor).isEqualTo(1402)
        assertThat(snapshot.orders.single().checkoutAttemptId).isEqualTo("attempt-1")
        assertThat(snapshot.stock.first { it.itemId == "ember-smash-burger" }.stock).isEqualTo(19)
    }

    @Test
    fun `same key and body replays without new effects`() {
        val first = app.checkout.placeOrder(singleBurgerOrder(), "key-1", ctx) as PlaceOrderResult.Committed
        val second = app.checkout.placeOrder(singleBurgerOrder(), "key-1", ctx) as PlaceOrderResult.Committed

        assertThat(second.replayed).isTrue()
        assertThat(second.order.orderId).isEqualTo(first.order.orderId)
        assertThat(ledger.orders).hasSize(1)
        assertThat(ledger.payments).hasSize(1)
        assertThat(ledger.idempotencyRecords.single().replayCount).isEqualTo(1)
    }

    @Test
    fun `same key with a different body is refused`() {
        app.checkout.placeOrder(singleBurgerOrder(), "key-1", ctx)
        val changed = singleBurgerOrder().copy(lines = listOf(singleBurger.copy(quantity = 2)), expectedTotalMinor = 2804)

        val result = app.checkout.placeOrder(changed, "key-1", ctx)

        assertThat((result as PlaceOrderResult.Rejected).code).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED)
        assertThat(ledger.orders).hasSize(1)
    }

    @Test
    fun `unavailable item is rejected with no order, payment or stock change`() {
        app.db.transaction { MenuRepository.setStock(it, "ember-smash-burger", 0) }

        val result = app.checkout.placeOrder(singleBurgerOrder(), "key-1", ctx)

        assertThat((result as PlaceOrderResult.Rejected).code).isEqualTo(ErrorCode.ITEM_UNAVAILABLE)
        assertThat(ledger.orders).isEmpty()
        assertThat(ledger.payments).isEmpty()
        assertThat(ledger.idempotencyRecords).isEmpty()
    }

    @Test
    fun `stale expected total is rejected with the fresh quote`() {
        val result = app.checkout.placeOrder(singleBurgerOrder(expectedTotal = 999), "key-1", ctx)

        val rejected = result as PlaceOrderResult.Rejected
        assertThat(rejected.code).isEqualTo(ErrorCode.TOTAL_CHANGED)
        assertThat(rejected.cart?.totalMinor).isEqualTo(1402)
        assertThat(ledger.orders).isEmpty()
    }

    @Test
    fun `rejected key can be reused once the customer confirms the new total`() {
        app.checkout.placeOrder(singleBurgerOrder(expectedTotal = 999), "key-1", ctx)
        val result = app.checkout.placeOrder(singleBurgerOrder(), "key-1", ctx)
        assertThat(result).isInstanceOf(PlaceOrderResult.Committed::class.java)
        assertThat(ledger.orders).hasSize(1)
    }

    @Test
    fun `non simulated payment tokens are refused`() {
        val result = app.checkout.placeOrder(singleBurgerOrder().copy(paymentToken = "tok_live_123"), "key-1", ctx)
        assertThat((result as PlaceOrderResult.Rejected).code).isEqualTo(ErrorCode.INVALID_REQUEST)
        assertThat(ledger.payments).isEmpty()
    }

    @Test
    fun `concurrent requests with one key commit exactly once`() {
        val results = runConcurrently(16) { app.checkout.placeOrder(singleBurgerOrder(), "shared-key", ctx) }

        val committed = results.filterIsInstance<PlaceOrderResult.Committed>()
        assertThat(committed).hasSize(16)
        assertThat(committed.count { !it.replayed }).isEqualTo(1)
        assertThat(committed.map { it.order.orderId }.toSet()).hasSize(1)
        assertThat(ledger.orders).hasSize(1)
        assertThat(ledger.payments).hasSize(1)
    }

    @Test
    fun `concurrent distinct checkouts never oversell limited stock`() {
        // Brisket stock is 5 in the seed catalog.
        val brisket = PlaceOrderRequest(
            listOf(OrderLineRequest("brisket-sandwich", listOf("bread-brioche"), 1)),
            expectedTotalMinor = 1570, // 1450 + 120 tax (119.625 rounds up)
            paymentToken = "sim_visa_4242",
        )
        val results = runConcurrently(12) { i -> app.checkout.placeOrder(brisket, "key-$i", ctx) }

        assertThat(results.count { it is PlaceOrderResult.Committed }).isEqualTo(5)
        assertThat(results.filterIsInstance<PlaceOrderResult.Rejected>().map { it.code }.toSet()).containsExactly(ErrorCode.ITEM_UNAVAILABLE)
        assertThat(ledger.orders).hasSize(5)
        assertThat(ledger.payments).hasSize(5)
        assertThat(ledger.stock.first { it.itemId == "brisket-sandwich" }.stock).isEqualTo(0)
    }

    @Test
    fun `orders survive a backend restart on the same database`() {
        app.checkout.placeOrder(singleBurgerOrder(), "key-1", ctx)
        app.close()
        app = startTestBackend(db, withControl = false)

        assertThat(ledger.orders).hasSize(1)
        val replay = app.checkout.placeOrder(singleBurgerOrder(), "key-1", ctx) as PlaceOrderResult.Committed
        assertThat(replay.replayed).isTrue()
        assertThat(ledger.payments).hasSize(1)
    }

    @Test
    fun `fixed clock starts at the documented seed instant`() {
        assertThat(app.clock.nowMillis()).isEqualTo(FixedClock.SEED_EPOCH_MILLIS)
    }

    private fun <T> runConcurrently(n: Int, block: (Int) -> T): List<T> {
        val pool = Executors.newFixedThreadPool(n)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until n).map { i -> pool.submit<T> { start.await(); block(i) } }
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
