package dev.orderproof.e2e.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.orderproof.control.FaultKind
import dev.orderproof.e2e.harness.Journey
import dev.orderproof.e2e.harness.ScenarioRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The order and payment commit, but the customer never hears back. The screens look the same
 * for a correct and a duplicating build; only the ledger (judged on the host) tells them apart.
 */
@RunWith(AndroidJUnit4::class)
class LostAnswerScenarios {
    @get:Rule val rule = ScenarioRule()
    private val journey = Journey(rule)
    private val app get() = rule.app
    private val evidence get() = rule.evidence

    /** Central scenario. 1 × burger (single): $14.02. Response dropped after commit; customer retries. */
    @Test
    fun lostResponseAfterCommit() {
        journey.openAndConnect()
        journey.awaitMenu()
        journey.addItem("ember-smash-burger")
        journey.openCart()
        journey.review()
        evidence.check("total shown", "$14.02" in journey.shownTotal(), journey.shownTotal())
        evidence.screenshot("review")

        rule.control.arm(FaultKind.DROP_RESPONSE_AFTER_COMMIT)
        journey.placeOrder()
        val problem = app.text("checkout_problem")
        evidence.check("customer told the outcome is unknown", "connection dropped" in problem, problem)
        evidence.screenshot("response-lost")

        evidence.step("customer taps try again")
        app.click("checkout_retry")
        val number = journey.awaitConfirmation()
        evidence.check("confirmation shows order number", number.startsWith("EK-"), number)
        evidence.check("confirmation shows total", "$14.02" in app.text("order_total"), app.text("order_total"))
        evidence.screenshot("confirmation-looks-normal")
    }

    /** 1 × fries (regular): $6.44. The app is killed while the committed response is held. */
    @Test
    fun processDeathDuringSubmission() {
        journey.openAndConnect()
        journey.awaitMenu()
        journey.addItem("charcoal-fries")
        journey.openCart()
        journey.review()
        evidence.check("total shown", "$6.44" in journey.shownTotal(), journey.shownTotal())

        rule.control.arm(FaultKind.HOLD_RESPONSE_AFTER_COMMIT)
        journey.placeOrder()
        val held = rule.control.awaitHold(rule.args.waitMillis)
        evidence.check("order committed and response held", held.held.size == 1, "held=${held.held.size}")
        evidence.screenshot("submitting")

        evidence.step("kill the app process mid-submission, then let the stale response go")
        app.forceStop()
        rule.control.releaseHolds()
        app.launch()

        // A customer reopening the app either sees the order or is offered a retry; both are allowed here.
        when (app.awaitAny("order_number", "checkout_retry")) {
            "checkout_retry" -> {
                evidence.record("observation", "detail" to "app offered retry after relaunch")
                evidence.screenshot("relaunch-offers-retry")
                app.click("checkout_retry")
            }
            else -> evidence.record("observation", "detail" to "app reconciled the order on relaunch")
        }
        val number = journey.awaitConfirmation()
        evidence.check("confirmation shows total", "$6.44" in app.text("order_total"), app.text("order_total"))
        evidence.screenshot("after-relaunch-$number")
    }
}
