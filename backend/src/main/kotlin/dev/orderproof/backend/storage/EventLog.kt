package dev.orderproof.backend.storage

import dev.orderproof.control.BackendEvent
import java.sql.Connection

/** Context copied from request headers so every event can be joined with app logs. */
data class EventContext(
    val requestId: String? = null,
    val idempotencyKey: String? = null,
    val checkoutAttemptId: String? = null,
)

class EventLog(private val nowMillis: () -> Long) {
    fun append(
        conn: Connection,
        kind: String,
        context: EventContext = EventContext(),
        orderId: String? = null,
        detail: String = "",
    ) {
        conn.update(
            """INSERT INTO events (at_millis, wall_clock_millis, kind, request_id, idempotency_key,
               checkout_attempt_id, order_id, detail) VALUES (?,?,?,?,?,?,?,?)""",
            nowMillis(), System.currentTimeMillis(), kind, context.requestId, context.idempotencyKey,
            context.checkoutAttemptId, orderId, detail,
        )
    }

    fun after(conn: Connection, seq: Long): List<BackendEvent> =
        conn.queryList("SELECT * FROM events WHERE seq > ? ORDER BY seq", seq) { rs ->
            BackendEvent(
                seq = rs.getLong("seq"),
                atMillis = rs.getLong("at_millis"),
                wallClockMillis = rs.getLong("wall_clock_millis"),
                kind = rs.getString("kind"),
                requestId = rs.getString("request_id"),
                idempotencyKey = rs.getString("idempotency_key"),
                checkoutAttemptId = rs.getString("checkout_attempt_id"),
                orderId = rs.getString("order_id"),
                detail = rs.getString("detail"),
            )
        }
}
