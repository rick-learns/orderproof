package dev.orderproof.oracle

import kotlinx.serialization.Serializable

@Serializable
enum class ViolationCode {
    /** One customer checkout produced more than one order (and charge). The centerpiece defect. */
    DUPLICATE_ORDER,
    MISSING_ORDER,
    UNEXPECTED_ORDER,
    ORDER_MISMATCH,
    ORDER_ARITHMETIC,
    ORDER_WITHOUT_PAYMENT,
    MULTIPLE_PAYMENTS,
    PAYMENT_MISMATCH,
    ORPHAN_PAYMENT,
    IDEMPOTENCY_RECORD_MISSING,
    STOCK_MISMATCH,
}

@Serializable
data class Violation(
    val code: ViolationCode,
    val message: String,
    /** Ledger identifiers (order ids, numbers, keys, attempts) that prove the violation. */
    val evidence: Map<String, String> = emptyMap(),
)

@Serializable
data class OracleVerdict(
    val violations: List<Violation>,
    val ordersSeen: Int,
    val paymentsSeen: Int,
) {
    val passed: Boolean get() = violations.isEmpty()
    val codes: Set<ViolationCode> get() = violations.map { it.code }.toSet()
}
