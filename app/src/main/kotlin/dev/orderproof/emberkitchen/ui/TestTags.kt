package dev.orderproof.emberkitchen.ui

/** Stable semantics tags shared by screens and instrumentation tests. */
object TestTags {
    const val CANDIDATE_BANNER = "candidate_banner"

    const val CONNECT_URL = "connect_url"
    const val CONNECT_TEST = "connect_test"
    const val CONNECT_SAVE = "connect_save"
    const val CONNECT_STATUS = "connect_status"
    const val CONNECT_FORM = "connect_form"

    const val MENU_LIST = "menu_list"
    const val MENU_ERROR = "menu_error"
    const val MENU_RETRY = "menu_retry"
    const val VIEW_CART = "view_cart"
    const val ACTIVE_ORDER_BANNER = "active_order_banner"
    fun menuItem(id: String) = "menu_item_$id"

    const val ITEM_DETAILS = "item_details"
    fun option(id: String) = "option_$id"
    const val QUANTITY_INCREASE = "quantity_increase"
    const val QUANTITY_DECREASE = "quantity_decrease"
    const val QUANTITY_VALUE = "quantity_value"
    const val ADD_TO_CART = "add_to_cart"

    const val CART_LIST = "cart_list"
    const val COUPON_FIELD = "coupon_field"
    const val COUPON_APPLY = "coupon_apply"
    const val COUPON_STATUS = "coupon_status"
    const val REVIEW_ORDER = "review_order"

    const val CHECKOUT_LIST = "checkout_list"
    const val SUMMARY_SUBTOTAL = "summary_subtotal"
    const val SUMMARY_DISCOUNT = "summary_discount"
    const val SUMMARY_TAX = "summary_tax"
    const val SUMMARY_TOTAL = "summary_total"
    const val PLACE_ORDER = "place_order"
    const val SUBMITTING = "submitting"
    const val TOTAL_CHANGED = "total_changed"
    const val CONFIRM_NEW_TOTAL = "confirm_new_total"
    const val CHECKOUT_PROBLEM = "checkout_problem"
    const val CHECKOUT_RETRY = "checkout_retry"
    const val BACK_TO_CART = "back_to_cart"

    const val ORDER_NUMBER = "order_number"
    const val ORDER_STATUS = "order_status"
    const val ORDER_TOTAL = "order_total"
    const val ORDER_REFRESH = "order_refresh"
    const val ORDER_DONE = "order_done"
}
