package dev.orderproof.e2e.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.orderproof.control.FaultKind
import dev.orderproof.e2e.harness.Journey
import dev.orderproof.e2e.harness.ScenarioRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The world changes under the customer: stock runs out, a coupon expires, the backend goes down. */
@RunWith(AndroidJUnit4::class)
class ChangedWorldScenarios {
    @get:Rule val rule = ScenarioRule()
    private val journey = Journey(rule)
    private val app get() = rule.app
    private val evidence get() = rule.evidence

    /** 1 × brisket ($15.70) sells out between review and submit: nothing placed, nothing charged. */
    @Test
    fun itemUnavailableBeforeSubmit() {
        journey.openAndConnect()
        journey.awaitMenu()
        journey.addItem("brisket-sandwich")
        journey.openCart()
        journey.review()
        evidence.check("total shown", "$15.70" in journey.shownTotal(), journey.shownTotal())

        evidence.step("brisket sells out in the kitchen")
        rule.control.setStock("brisket-sandwich", 0)
        journey.placeOrder()
        app.await("back_to_cart")
        val notice = app.text("checkout_list")
        evidence.check("customer told it sold out", "sold out" in notice, notice)
        evidence.check("customer told they were not charged", "weren't charged" in notice, notice)
        evidence.screenshot("sold-out")

        app.click("back_to_cart")
        app.await("cart_list")
        val cart = app.text("cart_list")
        evidence.check("cart marks the item sold out", "Sold out" in cart, cart)
        evidence.check("review blocked while sold out", !app.isEnabled("review_order"), "review_order enabled=${app.isEnabled("review_order")}")
        evidence.screenshot("cart-sold-out")
    }

    /**
     * 2 × burger (single) with EMBER10: $25.23. While the app is in the background the coupon
     * expires; on return the new total $28.04 must be confirmed explicitly before paying.
     */
    @Test
    fun couponExpiresWhileBackgrounded() {
        journey.openAndConnect()
        journey.awaitMenu()
        journey.addItem("ember-smash-burger", quantity = 2)
        journey.openCart()
        journey.applyCoupon("EMBER10")
        journey.review()
        evidence.check("discounted total shown", "$25.23" in journey.shownTotal(), journey.shownTotal())
        evidence.screenshot("review-with-coupon")

        evidence.step("background the app; 2h01m pass on the backend clock")
        app.pressHome()
        rule.control.advanceClock((2 * 60 + 1) * 60_000L)
        app.launch()

        val notice = app.text("total_changed")
        evidence.check("change explained with both totals", "$25.23" in notice && "$28.04" in notice, notice)
        evidence.check("place order blocked until confirmed", !app.isEnabled("place_order"), "place_order enabled=${app.isEnabled("place_order")}")
        evidence.screenshot("total-changed")

        app.click("confirm_new_total")
        evidence.check("new total shown", "$28.04" in journey.shownTotal(), journey.shownTotal())
        journey.placeOrder()
        journey.awaitConfirmation()
        evidence.check("charged the confirmed total", "$28.04" in app.text("order_total"), app.text("order_total"))
        evidence.screenshot("confirmation")
    }

    /** Menu and checkout both hit an outage; each recovers by retry. 1 × cauliflower: $11.91. */
    @Test
    fun backendOutageRecovers() {
        rule.control.arm(FaultKind.SERVICE_UNAVAILABLE, times = -1)
        journey.openAndConnect()
        val menuError = app.text("menu_error")
        evidence.check("menu outage explained", "temporarily unavailable" in menuError, menuError)
        evidence.screenshot("menu-outage")

        rule.control.clearFaults()
        app.click("menu_retry")
        journey.awaitMenu()
        journey.addItem("fire-roasted-cauliflower")
        journey.openCart()
        journey.review()
        evidence.check("total shown", "$11.91" in journey.shownTotal(), journey.shownTotal())

        rule.control.arm(FaultKind.SERVICE_UNAVAILABLE, times = -1)
        journey.placeOrder()
        val problem = app.text("checkout_problem")
        evidence.check("checkout outage says nothing was charged", "weren't charged" in problem, problem)
        evidence.screenshot("checkout-outage")

        rule.control.clearFaults()
        app.click("checkout_retry")
        journey.awaitConfirmation()
        evidence.check("confirmation shows total", "$11.91" in app.text("order_total"), app.text("order_total"))
        evidence.screenshot("recovered")
    }
}
