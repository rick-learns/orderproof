package dev.orderproof.e2e.harness

/**
 * Customer steps shared by scenarios. Tags mirror `ui/TestTags.kt` in the app; ids come from the
 * seed catalog in the behavior contract. These helpers only act and observe: no ledger access.
 */
class Journey(private val rule: ScenarioRule) {
    private val app get() = rule.app
    private val evidence get() = rule.evidence

    fun openAndConnect() {
        app.launch()
        connect()
    }

    fun connect() {
        evidence.step("connect to ${rule.args.backendUrl}")
        app.type("connect_url", rule.args.backendUrl, scrollIn = "connect_form")
        app.click("connect_save", scrollIn = "connect_form")
    }

    fun awaitMenu() = app.await("menu_list")

    fun addItem(itemId: String, options: List<String> = emptyList(), quantity: Int = 1) {
        evidence.step("add $quantity × $itemId $options")
        app.click("menu_item_$itemId", scrollIn = "menu_list")
        app.await("add_to_cart")
        // On a small screen (or with the faulty build's banner) these can sit below the fold.
        options.forEach { app.click("option_$it", scrollIn = "item_details") }
        repeat(quantity - 1) { app.click("quantity_increase", scrollIn = "item_details") }
        app.click("add_to_cart")
        awaitMenu()
    }

    fun openCart() {
        app.click("view_cart")
        app.await("cart_list")
    }

    fun applyCoupon(code: String) {
        evidence.step("apply coupon $code")
        app.type("coupon_field", code, scrollIn = "cart_list")
        app.click("coupon_apply", scrollIn = "cart_list")
        app.waitForText("coupon_status", code)
    }

    fun review() {
        app.click("review_order")
        app.await("checkout_list")
        app.await("summary_total")
    }

    fun shownTotal(): String = app.text("summary_total")

    fun placeOrder() {
        evidence.step("tap place order")
        app.click("place_order")
    }

    /** Waits for the confirmation screen and returns what the customer sees as the order number. */
    fun awaitConfirmation(): String = app.text("order_number")
}
