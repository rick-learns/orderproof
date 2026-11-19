package dev.orderproof.oracle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Hand-built ledgers: each test breaks exactly one rule and expects exactly that violation. */
class LedgerOracleTest {
    private val burgerLine = Ledger.Line("ember-smash-burger", listOf("patty-single"), 1, 1295, 1295)
    private val oneBurger = Expectation(listOf(ExpectedOrder(listOf(ExpectedLine("ember-smash-burger", listOf("patty-single"), 1)), 1295, 0, 107, 1402)))

    private fun order(n: Int, attempt: String? = "attempt-1", total: Long = 1402, tax: Long = 107) = Ledger.Order(
        "o$n", "EK-${1000 + n}", "key-$n", attempt, 1295, 0, tax, total, null, listOf(burgerLine),
    )

    private fun ledger(vararg orders: Ledger.Order, payments: List<Ledger.Payment>? = null, burgerStock: Int = 20 - orders.size) = Ledger(
        orders = orders.toList(),
        payments = payments ?: orders.map { Ledger.Payment("p-${it.orderId}", it.orderId, it.totalMinor, "CAPTURED", "SIMULATED_CARD") },
        idempotency = orders.map { Ledger.IdempotencyRecord(it.idempotencyKey, it.orderId, 0) },
        stock = SeedStock.levels + ("ember-smash-burger" to burgerStock),
    )

    @Test
    fun aSingleCorrectOrderPasses() {
        assertThat(LedgerOracle.judge(ledger(order(1)), oneBurger).violations).isEmpty()
    }

    @Test
    fun twoOrdersForOneAttemptAreADuplicateAndNothingElse() {
        val verdict = LedgerOracle.judge(ledger(order(1), order(2)), oneBurger)
        assertThat(verdict.codes).containsExactly(ViolationCode.DUPLICATE_ORDER)
        val evidence = verdict.violations.single().evidence
        assertThat(evidence["orderNumbers"]).isEqualTo("EK-1001,EK-1002")
        assertThat(evidence["idempotencyKeys"]).isEqualTo("key-1,key-2")
    }

    @Test
    fun aSecondIdenticalOrderWithoutAttemptIdIsStillADuplicate() {
        val verdict = LedgerOracle.judge(ledger(order(1, attempt = null), order(2, attempt = null)), oneBurger)
        assertThat(verdict.codes).containsExactly(ViolationCode.DUPLICATE_ORDER)
    }

    @Test
    fun noOrderWhenOneWasExpectedIsMissing() {
        assertThat(LedgerOracle.judge(ledger(burgerStock = 20), oneBurger).codes).containsExactly(ViolationCode.MISSING_ORDER)
    }

    @Test
    fun anOrderWhenNoneWasExpectedIsUnexpected() {
        assertThat(LedgerOracle.judge(ledger(order(1)), Expectation(emptyList())).codes).containsExactly(ViolationCode.UNEXPECTED_ORDER)
    }

    @Test
    fun wrongTotalsAreReportedAgainstTheHandWrittenExpectation() {
        val verdict = LedgerOracle.judge(ledger(order(1, total = 1403, tax = 108)), oneBurger)
        assertThat(verdict.codes).containsExactly(ViolationCode.ORDER_MISMATCH)
    }

    @Test
    fun anOrderWithoutPaymentIsCaught() {
        val verdict = LedgerOracle.judge(ledger(order(1), payments = emptyList()), oneBurger)
        assertThat(verdict.codes).containsExactly(ViolationCode.ORDER_WITHOUT_PAYMENT)
    }

    @Test
    fun aPaymentForTheWrongAmountIsCaught() {
        val verdict = LedgerOracle.judge(ledger(order(1), payments = listOf(Ledger.Payment("p", "o1", 1300, "CAPTURED", "SIMULATED_CARD"))), oneBurger)
        assertThat(verdict.codes).containsExactly(ViolationCode.PAYMENT_MISMATCH)
    }

    @Test
    fun inconsistentArithmeticIsCaught() {
        val broken = order(1).copy(subtotalMinor = 1300)
        assertThat(LedgerOracle.judge(ledger(broken), Expectation(emptyList())).codes).contains(ViolationCode.ORDER_ARITHMETIC)
    }

    @Test
    fun stockThatMovedDifferentlyFromOrdersIsCaught() {
        assertThat(LedgerOracle.judge(ledger(order(1), burgerStock = 18), oneBurger).codes).containsExactly(ViolationCode.STOCK_MISMATCH)
    }

    @Test
    fun aMissingIdempotencyRecordIsCaught() {
        val l = ledger(order(1)).copy(idempotency = emptyList())
        assertThat(LedgerOracle.judge(l, oneBurger).codes).containsExactly(ViolationCode.IDEMPOTENCY_RECORD_MISSING)
    }
}
