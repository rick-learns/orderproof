package dev.orderproof.oracle

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Opens the backend database file read-only and selects rows with the oracle's own SQL. If the
 * backend's schema drifts, this fails loudly instead of silently trusting backend code.
 */
object SqliteLedgerSource {
    fun read(databaseFile: Path): Ledger {
        require(Files.isRegularFile(databaseFile)) { "No database at $databaseFile" }
        val props = java.util.Properties().apply { setProperty("open_mode", "1") } // SQLITE_OPEN_READONLY
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.toAbsolutePath()}", props).use { conn ->
            conn.autoCommit = false // one read transaction = one consistent snapshot
            return Ledger(orders(conn), payments(conn), idempotency(conn), stock(conn)).also { conn.rollback() }
        }
    }

    private fun orders(conn: Connection): List<Ledger.Order> {
        val lines = conn.rows(
            "SELECT order_id, item_id, option_ids, quantity, unit_price_minor, line_total_minor FROM order_lines ORDER BY order_id, line_no",
        ) { rs ->
            rs.getString(1) to Ledger.Line(rs.getString(2), splitIds(rs.getString(3)), rs.getInt(4), rs.getLong(5), rs.getLong(6))
        }.groupBy({ it.first }, { it.second })
        return conn.rows(
            """SELECT order_id, order_number, idempotency_key, checkout_attempt_id, subtotal_minor, discount_minor,
                      tax_minor, total_minor, coupon_code FROM orders ORDER BY created_at_millis, order_number""",
        ) { rs ->
            val id = rs.getString(1)
            Ledger.Order(
                id, rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8),
                rs.getString(9), lines[id].orEmpty(),
            )
        }
    }

    private fun payments(conn: Connection) =
        conn.rows("SELECT payment_id, order_id, amount_minor, status, method FROM payments ORDER BY created_at_millis, payment_id") { rs ->
            Ledger.Payment(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5))
        }

    private fun idempotency(conn: Connection) =
        conn.rows("SELECT idempotency_key, order_id, replay_count FROM idempotency_records ORDER BY idempotency_key") { rs ->
            Ledger.IdempotencyRecord(rs.getString(1), rs.getString(2), rs.getInt(3))
        }

    private fun stock(conn: Connection) = conn.rows("SELECT id, stock FROM menu_items ORDER BY id") { rs -> rs.getString(1) to rs.getInt(2) }.toMap()

    /** Storage format (pipe-separated ids, empty for none) is part of the documented schema contract. */
    private fun splitIds(raw: String): List<String> = if (raw.isEmpty()) emptyList() else raw.split('|')

    private fun <T> Connection.rows(sql: String, map: (java.sql.ResultSet) -> T): List<T> =
        prepareStatement(sql).use { st -> st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } } }
}
