package dev.orderproof.backend.storage

import java.sql.Connection

data class IdempotencyRecord(
    val idempotencyKey: String,
    val orderId: String,
    val requestHash: String,
    val replayCount: Int,
)

object IdempotencyRepository {
    fun find(conn: Connection, key: String): IdempotencyRecord? =
        conn.queryList("SELECT * FROM idempotency_records WHERE idempotency_key = ?", key) { it.toRecord() }
            .singleOrNull()

    fun insert(conn: Connection, key: String, orderId: String, requestHash: String) {
        conn.update(
            "INSERT INTO idempotency_records (idempotency_key, order_id, request_hash) VALUES (?,?,?)",
            key, orderId, requestHash,
        )
    }

    fun recordReplay(conn: Connection, key: String) {
        conn.update("UPDATE idempotency_records SET replay_count = replay_count + 1 WHERE idempotency_key = ?", key)
    }

    fun all(conn: Connection): List<IdempotencyRecord> =
        conn.queryList("SELECT * FROM idempotency_records ORDER BY idempotency_key") { it.toRecord() }

    private fun java.sql.ResultSet.toRecord() = IdempotencyRecord(
        idempotencyKey = getString("idempotency_key"),
        orderId = getString("order_id"),
        requestHash = getString("request_hash"),
        replayCount = getInt("replay_count"),
    )
}
