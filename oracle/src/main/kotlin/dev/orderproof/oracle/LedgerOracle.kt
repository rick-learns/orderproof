package dev.orderproof.oracle

/**
 * Judges a persisted ledger. Two layers:
 *  1. invariants that hold for every scenario (one captured payment per order, consistent
 *     arithmetic, an idempotency record per order, stock moved exactly by what was ordered);
 *  2. the scenario's hand-written expectation (which orders, at which totals, and no others).
 */
object LedgerOracle {
    fun judge(ledger: Ledger, expectation: Expectation): OracleVerdict {
        val violations = mutableListOf<Violation>()
        violations += paymentInvariants(ledger)
        violations += ledger.orders.flatMap(::arithmetic)
        violations += idempotencyInvariants(ledger)
        val duplicates = duplicatesByAttempt(ledger)
        violations += duplicates.violations
        violations += matchExpected(ledger.orders.filterNot { it.orderId in duplicates.extraOrderIds }, expectation, ledger)
        violations += stockInvariants(ledger, expectation)
        return OracleVerdict(violations, ledger.orders.size, ledger.payments.size)
    }

    private fun paymentInvariants(ledger: Ledger): List<Violation> = buildList {
        val byOrder = ledger.payments.groupBy { it.orderId }
        ledger.orders.forEach { order ->
            val payments = byOrder[order.orderId].orEmpty()
            when {
                payments.isEmpty() -> add(Violation(ViolationCode.ORDER_WITHOUT_PAYMENT, "Order ${order.orderNumber} has no payment.", order.ids()))
                payments.size > 1 -> add(Violation(ViolationCode.MULTIPLE_PAYMENTS, "Order ${order.orderNumber} has ${payments.size} payments.", order.ids()))
            }
            payments.filter { it.amountMinor != order.totalMinor || it.status != "CAPTURED" || it.method != "SIMULATED_CARD" }.forEach {
                add(
                    Violation(
                        ViolationCode.PAYMENT_MISMATCH,
                        "Payment ${it.paymentId} is ${it.status}/${it.method} for ${it.amountMinor}; order ${order.orderNumber} totals ${order.totalMinor}.",
                        order.ids() + ("paymentId" to it.paymentId),
                    ),
                )
            }
        }
        val orderIds = ledger.orders.map { it.orderId }.toSet()
        ledger.payments.filter { it.orderId !in orderIds }.forEach {
            add(Violation(ViolationCode.ORPHAN_PAYMENT, "Payment ${it.paymentId} references missing order ${it.orderId}.", mapOf("paymentId" to it.paymentId)))
        }
    }

    private fun arithmetic(order: Ledger.Order): List<Violation> {
        val problems = buildList {
            order.lines.filter { it.unitPriceMinor * it.quantity != it.lineTotalMinor }.forEach { add("line ${it.itemId} unit×qty≠line total") }
            if (order.lines.sumOf { it.lineTotalMinor } != order.subtotalMinor) add("lines do not sum to subtotal")
            if (order.subtotalMinor - order.discountMinor + order.taxMinor != order.totalMinor) add("subtotal − discount + tax ≠ total")
            if (order.discountMinor !in 0..order.subtotalMinor || order.taxMinor < 0) add("discount or tax out of range")
            if (order.lines.isEmpty()) add("order has no lines")
        }
        return problems.map { Violation(ViolationCode.ORDER_ARITHMETIC, "Order ${order.orderNumber}: $it.", order.ids()) }
    }

    private fun idempotencyInvariants(ledger: Ledger): List<Violation> {
        val records = ledger.idempotency.associateBy { it.key }
        return ledger.orders.filter { records[it.idempotencyKey]?.orderId != it.orderId }.map {
            Violation(ViolationCode.IDEMPOTENCY_RECORD_MISSING, "Order ${it.orderNumber} has no idempotency record for its key.", it.ids())
        }
    }

    private class Duplicates(val violations: List<Violation>, val extraOrderIds: Set<String>)

    /** Several orders carrying one checkout attempt id means one customer intent was charged repeatedly. */
    private fun duplicatesByAttempt(ledger: Ledger): Duplicates {
        val groups = ledger.orders.filter { it.checkoutAttemptId != null }.groupBy { it.checkoutAttemptId!! }.filterValues { it.size > 1 }
        val payments = ledger.payments.groupBy { it.orderId }
        val violations = groups.map { (attempt, orders) ->
            val charged = orders.flatMap { payments[it.orderId].orEmpty() }
            Violation(
                ViolationCode.DUPLICATE_ORDER,
                "Checkout attempt $attempt produced ${orders.size} orders (${orders.joinToString { it.orderNumber }}) and " +
                    "${charged.size} captured payments totalling ${charged.sumOf { it.amountMinor }} for one customer intent.",
                mapOf(
                    "checkoutAttemptId" to attempt,
                    "orderNumbers" to orders.joinToString(",") { it.orderNumber },
                    "orderIds" to orders.joinToString(",") { it.orderId },
                    "idempotencyKeys" to orders.joinToString(",") { it.idempotencyKey },
                    "paymentIds" to charged.joinToString(",") { it.paymentId },
                ),
            )
        }
        return Duplicates(violations, groups.values.flatMap { it.drop(1) }.map { it.orderId }.toSet())
    }

    private fun matchExpected(orders: List<Ledger.Order>, expectation: Expectation, ledger: Ledger): List<Violation> = buildList {
        val unmatched = orders.toMutableList()
        expectation.orders.forEachIndexed { index, expected ->
            val found = unmatched.firstOrNull { shape(it.lines) == expectedShape(expected.lines) }
            if (found == null) {
                add(Violation(ViolationCode.MISSING_ORDER, "Expected order #${index + 1} (total ${expected.totalMinor}) was not persisted."))
                return@forEachIndexed
            }
            unmatched.remove(found)
            val actual = listOf(found.subtotalMinor, found.discountMinor, found.taxMinor, found.totalMinor)
            val wanted = listOf(expected.subtotalMinor, expected.discountMinor, expected.taxMinor, expected.totalMinor)
            if (actual != wanted || found.couponCode != expected.couponCode) {
                add(
                    Violation(
                        ViolationCode.ORDER_MISMATCH,
                        "Order ${found.orderNumber} has subtotal/discount/tax/total $actual coupon=${found.couponCode}; " +
                            "expected $wanted coupon=${expected.couponCode}.",
                        found.ids(),
                    ),
                )
            }
        }
        val expectedShapes = expectation.orders.map { expectedShape(it.lines) }.toSet()
        unmatched.forEach { extra ->
            // Same cart as an expected order but under another attempt id (or none): still a double charge.
            val code = if (shape(extra.lines) in expectedShapes) ViolationCode.DUPLICATE_ORDER else ViolationCode.UNEXPECTED_ORDER
            val charged = ledger.payments.filter { it.orderId == extra.orderId }.sumOf { it.amountMinor }
            add(Violation(code, "Order ${extra.orderNumber} (total ${extra.totalMinor}, charged $charged) was not expected by this scenario.", extra.ids()))
        }
    }

    private fun stockInvariants(ledger: Ledger, expectation: Expectation): List<Violation> {
        val baseline = SeedStock.levels + expectation.stockBaselineOverrides
        val ordered = ledger.orders.flatMap { it.lines }.groupBy { it.itemId }.mapValues { (_, lines) -> lines.sumOf { it.quantity } }
        return baseline.mapNotNull { (item, start) ->
            val want = start - (ordered[item] ?: 0)
            val have = ledger.stock[item]
            if (have == want) null else Violation(ViolationCode.STOCK_MISMATCH, "Stock of $item is $have; expected $want.", mapOf("itemId" to item))
        }
    }

    private fun shape(lines: List<Ledger.Line>) = lines.map { Triple(it.itemId, it.optionIds.sorted(), it.quantity) }.sortedBy { it.toString() }
    private fun expectedShape(lines: List<ExpectedLine>) = lines.map { Triple(it.itemId, it.optionIds.sorted(), it.quantity) }.sortedBy { it.toString() }

    private fun Ledger.Order.ids() = mapOf(
        "orderId" to orderId,
        "orderNumber" to orderNumber,
        "idempotencyKey" to idempotencyKey,
        "checkoutAttemptId" to (checkoutAttemptId ?: "-"),
    )
}
