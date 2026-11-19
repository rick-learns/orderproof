package dev.orderproof.verifier.scenario

import dev.orderproof.oracle.ExpectedLine
import dev.orderproof.oracle.ExpectedOrder
import dev.orderproof.oracle.Expectation

/** One release scenario: the UI test that drives it and what the ledger must hold afterwards. */
data class Scenario(
    val id: String,
    val testClass: String,
    val title: String,
    val expectation: Expectation,
    /** The centerpiece: always screen-recorded, and its recording is required evidence. */
    val central: Boolean = false,
) {
    val testName: String get() = "dev.orderproof.e2e.scenarios.$testClass#$id"
}

/**
 * Expected ledgers, written by hand from docs/behavior-contract.md. Tax is 8.25% of the
 * discounted subtotal, rounded half-up to the cent. Nothing here calls production pricing.
 */
object ScenarioCatalog {
    private fun line(item: String, vararg options: String, quantity: Int = 1) = ExpectedLine(item, options.toList(), quantity)
    private fun order(vararg lines: ExpectedLine, subtotal: Long, discount: Long = 0, tax: Long, total: Long) =
        Expectation(listOf(ExpectedOrder(lines.toList(), subtotal, discount, tax, total)))

    val all: List<Scenario> = listOf(
        // (1295 + 350 + 225 + 150) × 2 = 4040; fries 595 + 250 = 845; 4885 × 8.25% = 403.01 → 403.
        Scenario(
            "normalCheckout", "CheckoutJourneyScenarios", "Normal checkout, tracked and recovered after reopen",
            order(
                line("ember-smash-burger", "patty-double", "addon-bacon", "addon-cheddar", quantity = 2),
                line("charcoal-fries", "fries-large"),
                subtotal = 4885, tax = 403, total = 5288,
            ),
        ),
        // 1295 × 8.25% = 106.84 → 107.
        Scenario(
            "lostResponseAfterCommit", "LostAnswerScenarios", "Response lost after commit; customer retries",
            order(line("ember-smash-burger", "patty-single"), subtotal = 1295, tax = 107, total = 1402),
            central = true,
        ),
        // 595 × 8.25% = 49.09 → 49.
        Scenario(
            "processDeathDuringSubmission", "LostAnswerScenarios", "App killed mid-submission; reconciled on relaunch",
            order(line("charcoal-fries", "fries-regular"), subtotal = 595, tax = 49, total = 644),
        ),
        // 450 × 8.25% = 37.13 → 37.
        Scenario(
            "repeatedTaps", "CheckoutJourneyScenarios", "Five taps on Place order",
            order(line("smoked-lemonade", "lemonade-regular"), subtotal = 450, tax = 37, total = 487),
        ),
        Scenario(
            "itemUnavailableBeforeSubmit", "ChangedWorldScenarios", "Item sells out before submit",
            Expectation(orders = emptyList(), stockBaselineOverrides = mapOf("brisket-sandwich" to 0)),
        ),
        // Coupon expired: no discount. 2590 × 8.25% = 213.68 → 214.
        Scenario(
            "couponExpiresWhileBackgrounded", "ChangedWorldScenarios", "Coupon expires in background; new total confirmed",
            order(line("ember-smash-burger", "patty-single", quantity = 2), subtotal = 2590, tax = 214, total = 2804),
        ),
        // 1100 × 8.25% = 90.75 → 91.
        Scenario(
            "backendOutageRecovers", "ChangedWorldScenarios", "Backend outage with recoverable errors",
            order(line("fire-roasted-cauliflower", "heat-mild"), subtotal = 1100, tax = 91, total = 1191),
        ),
        // 795 × 8.25% = 65.59 → 66.
        Scenario(
            "largeTextSmallScreen", "AccessibilityScenarios", "200% text on a 720×1280 screen",
            order(line("burnt-honey-cake"), subtotal = 795, tax = 66, total = 861),
        ),
    )

    fun select(ids: List<String>?): List<Scenario> {
        if (ids.isNullOrEmpty()) return all
        val unknown = ids - all.map { it.id }.toSet()
        require(unknown.isEmpty()) { "Unknown scenarios: $unknown. Known: ${all.map { it.id }}" }
        return all.filter { it.id in ids }
    }
}
