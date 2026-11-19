package dev.orderproof.oracle

import kotlinx.serialization.Serializable

/**
 * What a scenario must leave behind in the ledger. Every number is written by hand from the
 * behavior contract (docs/behavior-contract.md); nothing here is computed by production code.
 */
@Serializable
data class Expectation(
    val orders: List<ExpectedOrder>,
    /** Stock levels before the scenario placed orders, when a scenario overrides the seed. */
    val stockBaselineOverrides: Map<String, Int> = emptyMap(),
)

@Serializable
data class ExpectedOrder(
    val lines: List<ExpectedLine>,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val couponCode: String? = null,
)

@Serializable
data class ExpectedLine(val itemId: String, val optionIds: List<String>, val quantity: Int)

/** Hand-copied from the seed catalog in the behavior contract, independently of backend code. */
object SeedStock {
    val levels: Map<String, Int> = mapOf(
        "ember-smash-burger" to 20,
        "brisket-sandwich" to 5,
        "fire-roasted-cauliflower" to 20,
        "charcoal-fries" to 40,
        "smoked-lemonade" to 30,
        "burnt-honey-cake" to 8,
    )
}
