package dev.orderproof.control

import kotlinx.serialization.Serializable

@Serializable
enum class FaultKind {
    /** Commit the order and payment, then close the connection without sending a response. */
    DROP_RESPONSE_AFTER_COMMIT,

    /** Commit, then park the response until released through the control API (a sync point). */
    HOLD_RESPONSE_AFTER_COMMIT,

    /** Refuse public requests with HTTP 503 before doing any work. */
    SERVICE_UNAVAILABLE,
}

/** [times] = how many matching requests the fault consumes; -1 means until cleared. */
@Serializable
data class ArmFaultRequest(
    val kind: FaultKind,
    val times: Int = 1,
)

@Serializable
data class ArmedFault(
    val kind: FaultKind,
    val remaining: Int,
)

@Serializable
data class HeldResponse(
    val holdId: String,
    val idempotencyKey: String,
    val orderId: String,
)

@Serializable
data class FaultState(
    val armed: List<ArmedFault>,
    val held: List<HeldResponse>,
    val releasedHoldIds: List<String>,
)
