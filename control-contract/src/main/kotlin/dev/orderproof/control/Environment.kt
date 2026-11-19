package dev.orderproof.control

import kotlinx.serialization.Serializable

object ControlPaths {
    const val RESET = "/control/reset"
    const val FAULTS = "/control/faults"
    const val HOLDS = "/control/holds"
    const val STOCK = "/control/stock"
    const val CLOCK = "/control/clock"
    const val LEDGER = "/control/ledger"
    const val EVENTS = "/control/events"
    const val ORDER_STATUS = "/control/orders"
    const val AWAIT_HOLD = "/control/holds/await"
}

object ControlHeaders {
    const val AUTHORIZATION = "Authorization"
    const val BEARER_PREFIX = "Bearer "
}

@Serializable
data class ResetResponse(
    val clockEpochMillis: Long,
    val menuItems: Int,
)

@Serializable
data class SetStockRequest(
    val itemId: String,
    val stock: Int,
)

/** Only allowed when the backend runs with a fixed (test) clock. */
@Serializable
data class AdvanceClockRequest(
    val byMillis: Long,
)

@Serializable
data class ClockState(
    val epochMillis: Long,
    val fixed: Boolean,
)

@Serializable
data class AdvanceOrderRequest(
    val orderId: String,
)

/** Long-poll: block until a held response exists or [timeoutMillis] passes. */
@Serializable
data class AwaitHoldRequest(
    val timeoutMillis: Long,
)
