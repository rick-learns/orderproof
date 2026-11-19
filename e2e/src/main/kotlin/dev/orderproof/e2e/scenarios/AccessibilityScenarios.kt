package dev.orderproof.e2e.scenarios

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.orderproof.e2e.harness.DisplaySettings
import dev.orderproof.e2e.harness.Journey
import dev.orderproof.e2e.harness.ScenarioRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityScenarios {
    @get:Rule val rule = ScenarioRule()
    private val journey = Journey(rule)
    private val app get() = rule.app
    private val evidence get() = rule.evidence

    /** 200% font on a 720×1280 @320dpi screen; the whole journey stays operable. 1 × honey cake: $8.61. */
    @Test
    fun largeTextSmallScreen() {
        DisplaySettings.apply(fontScale = "2.0", size = "720x1280", density = 320)
        journey.openAndConnect()
        journey.awaitMenu()
        evidence.screenshot("menu-large-text")
        journey.addItem("burnt-honey-cake")
        assertOnScreen("view_cart")
        journey.openCart()
        evidence.screenshot("cart-large-text")
        assertOnScreen("review_order")
        journey.review()
        val total = app.scrollTo("summary_total", "checkout_list")
        evidence.check("total readable", "$8.61" in app.text("summary_total"), app.text("summary_total"))
        evidence.check("total row on screen", total.visibleBounds.height() > 0, total.visibleBounds.toShortString())
        assertOnScreen("place_order")
        evidence.screenshot("review-large-text")
        journey.placeOrder()
        journey.awaitConfirmation()
        assertOnScreen("order_number")
        evidence.check("confirmation total readable", "$8.61" in app.text("order_total"), app.text("order_total"))
        evidence.screenshot("confirmation-large-text")
    }

    private fun assertOnScreen(tag: String) {
        val bounds = app.await(tag).visibleBounds
        val screen = Rect(0, 0, app.device.displayWidth, app.device.displayHeight)
        val ok = !bounds.isEmpty && screen.contains(bounds) && bounds.height() >= MIN_TOUCH_PX
        evidence.check("$tag fully on screen and touchable", ok, "bounds=${bounds.toShortString()} screen=${screen.toShortString()}")
    }

    private companion object {
        /** 48dp at 320dpi. */
        const val MIN_TOUCH_PX = 96
    }
}
