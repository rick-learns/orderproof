package dev.orderproof.e2e.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.orderproof.control.FaultKind
import dev.orderproof.e2e.harness.Journey
import dev.orderproof.e2e.harness.ScenarioRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Happy path, order tracking and recovery after the app is closed; and a burst of taps. */
@RunWith(AndroidJUnit4::class)
class CheckoutJourneyScenarios {
    @get:Rule val rule = ScenarioRule()
    private val journey = Journey(rule)
    private val app get() = rule.app
    private val evidence get() = rule.evidence

    /** 2 × burger (double, bacon, cheddar) + large fries: subtotal $48.85, tax $4.03, total $52.88. */
    @Test
    fun normalCheckout() {
        journey.openAndConnect()
        journey.awaitMenu()
        evidence.screenshot("menu")
        journey.addItem("ember-smash-burger", listOf("patty-double", "addon-bacon", "addon-cheddar"), quantity = 2)
        journey.addItem("charcoal-fries", listOf("fries-large"))
        journey.openCart()
        journey.review()
        evidence.check("subtotal shown", "$48.85" in app.text("summary_subtotal"), app.text("summary_subtotal"))
        evidence.check("tax shown", "$4.03" in app.text("summary_tax"), app.text("summary_tax"))
        evidence.check("total shown", "$52.88" in journey.shownTotal(), journey.shownTotal())
        evidence.screenshot("review")

        journey.placeOrder()
        val number = journey.awaitConfirmation()
        evidence.check("confirmation shows order number", number.startsWith("EK-"), number)
        evidence.check("confirmation shows total", "$52.88" in app.text("order_total"), app.text("order_total"))
        evidence.screenshot("confirmation")

        val orderId = rule.control.ledger().orders.single { it.orderNumber == number }.orderId
        rule.control.advanceOrder(orderId)
        app.click("order_refresh")
        evidence.check("status update visible", "being prepared" in app.waitForText("order_status", "being prepared"), app.text("order_status"))

        evidence.step("close the app (process killed) and reopen it")
        app.forceStop()
        app.launch()
        val recovered = journey.awaitConfirmation()
        evidence.check("order recovered after reopen", recovered == number, "before=$number after=$recovered")
        evidence.check("recovered status still current", "being prepared" in app.waitForText("order_status", "being prepared"), app.text("order_status"))
        evidence.screenshot("recovered-after-reopen")
    }

    /** Five taps on Place order while the first submission is in flight: 1 × lemonade, $4.87. */
    @Test
    fun repeatedTaps() {
        journey.openAndConnect()
        journey.awaitMenu()
        journey.addItem("smoked-lemonade")
        journey.openCart()
        journey.review()
        evidence.check("total shown", "$4.87" in journey.shownTotal(), journey.shownTotal())

        // Park the committed response so every extra tap lands while the first send is in flight.
        rule.control.arm(FaultKind.HOLD_RESPONSE_AFTER_COMMIT)
        evidence.step("burst of 5 taps on place order")
        app.burstClick("place_order", times = 5)
        val held = rule.control.awaitHold(rule.args.waitMillis)
        evidence.check("first submission reached the backend", held.held.size == 1, "held=${held.held.size}")
        evidence.screenshot("submitting")
        rule.control.releaseHolds()

        val number = journey.awaitConfirmation()
        evidence.check("confirmation shows total", "$4.87" in app.text("order_total"), app.text("order_total"))
        evidence.screenshot("confirmation-$number")
    }
}
