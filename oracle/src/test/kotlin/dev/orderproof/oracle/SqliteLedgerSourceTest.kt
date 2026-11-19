package dev.orderproof.oracle

import com.google.common.truth.Truth.assertThat
import dev.orderproof.backend.BackendApp
import dev.orderproof.backend.config.BackendConfig
import dev.orderproof.backend.config.ClockMode
import dev.orderproof.backend.storage.EventContext
import dev.orderproof.contract.OrderLineRequest
import dev.orderproof.contract.PlaceOrderRequest
import org.junit.After
import org.junit.Test
import java.nio.file.Files

/** The oracle reads a database written by the real backend, with its own SQL, while it is open. */
class SqliteLedgerSourceTest {
    private val db = Files.createTempDirectory("oracle-test").resolve("orderproof.db")
    private val backend = BackendApp(
        BackendConfig(publicHost = "127.0.0.1", publicPort = 0, controlPort = 0, databaseFile = db, clockMode = ClockMode.FIXED),
        controlToken = null,
    ).start()

    @After
    fun tearDown() = backend.close()

    /** 2 × burger (double, bacon, cheddar) + large fries: 4040 + 845 = 4885; tax 403 (403.01); total 5288. */
    private val bigOrder = PlaceOrderRequest(
        lines = listOf(
            OrderLineRequest("ember-smash-burger", listOf("patty-double", "addon-bacon", "addon-cheddar"), 2),
            OrderLineRequest("charcoal-fries", listOf("fries-large"), 1),
        ),
        expectedTotalMinor = 5288,
        paymentToken = "sim_visa_4242",
    )
    private val bigExpectation = Expectation(
        listOf(
            ExpectedOrder(
                lines = listOf(
                    ExpectedLine("ember-smash-burger", listOf("patty-double", "addon-bacon", "addon-cheddar"), 2),
                    ExpectedLine("charcoal-fries", listOf("fries-large"), 1),
                ),
                subtotalMinor = 4885, discountMinor = 0, taxMinor = 403, totalMinor = 5288,
            ),
        ),
    )

    @Test
    fun keyedRetriesLeaveOneOrderThatPassesTheOracle() {
        repeat(3) { backend.checkout.placeOrder(bigOrder, "same-key", EventContext(checkoutAttemptId = "attempt-1")) }

        val ledger = SqliteLedgerSource.read(db)
        assertThat(ledger.orders).hasSize(1)
        assertThat(ledger.idempotency.single().replayCount).isEqualTo(2)
        assertThat(LedgerOracle.judge(ledger, bigExpectation).violations).isEmpty()
    }

    @Test
    fun freshKeysPerRetryAreJudgedAsDuplicates() {
        backend.checkout.placeOrder(bigOrder, "key-a", EventContext(checkoutAttemptId = "attempt-1"))
        backend.checkout.placeOrder(bigOrder, "key-b", EventContext(checkoutAttemptId = "attempt-1"))

        val verdict = LedgerOracle.judge(SqliteLedgerSource.read(db), bigExpectation)
        assertThat(verdict.codes).containsExactly(ViolationCode.DUPLICATE_ORDER)
        assertThat(verdict.paymentsSeen).isEqualTo(2)
    }
}
