package dev.orderproof.backend.storage

import dev.orderproof.backend.domain.StoredPayment
import java.sql.Connection
import java.sql.ResultSet

object PaymentRepository {
    fun insert(conn: Connection, payment: StoredPayment) {
        conn.update(
            "INSERT INTO payments (payment_id, order_id, amount_minor, status, method, created_at_millis) VALUES (?,?,?,?,?,?)",
            payment.paymentId, payment.orderId, payment.amountMinor, payment.status, payment.method,
            payment.createdAtMillis,
        )
    }

    fun findByOrder(conn: Connection, orderId: String): StoredPayment? =
        conn.queryList("SELECT * FROM payments WHERE order_id = ?", orderId) { it.toPayment() }.singleOrNull()

    fun all(conn: Connection): List<StoredPayment> =
        conn.queryList("SELECT * FROM payments ORDER BY created_at_millis, payment_id") { it.toPayment() }

    private fun ResultSet.toPayment() = StoredPayment(
        paymentId = getString("payment_id"),
        orderId = getString("order_id"),
        amountMinor = getLong("amount_minor"),
        status = getString("status"),
        method = getString("method"),
        createdAtMillis = getLong("created_at_millis"),
    )
}
