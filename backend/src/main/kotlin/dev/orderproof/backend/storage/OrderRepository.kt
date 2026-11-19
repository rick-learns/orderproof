package dev.orderproof.backend.storage

import dev.orderproof.backend.domain.OrderState
import dev.orderproof.backend.domain.StoredLine
import dev.orderproof.backend.domain.StoredOrder
import java.sql.Connection
import java.sql.ResultSet

object OrderRepository {
    private const val FIRST_ORDER_NUMBER = 1001

    fun nextOrderNumber(conn: Connection): String {
        var count = 0
        conn.query("SELECT COUNT(*) AS n FROM orders") { count = it.getInt("n") }
        return "EK-${FIRST_ORDER_NUMBER + count}"
    }

    fun insert(conn: Connection, order: StoredOrder) {
        conn.update(
            """INSERT INTO orders (order_id, order_number, idempotency_key, checkout_attempt_id, status,
               subtotal_minor, discount_minor, tax_minor, total_minor, coupon_code, created_at_millis)
               VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
            order.orderId, order.orderNumber, order.idempotencyKey, order.checkoutAttemptId, order.status.name,
            order.subtotalMinor, order.discountMinor, order.taxMinor, order.totalMinor, order.couponCode,
            order.createdAtMillis,
        )
        order.lines.forEachIndexed { i, line ->
            conn.update(
                """INSERT INTO order_lines (order_id, line_no, item_id, item_name, option_ids, option_names,
                   quantity, unit_price_minor, line_total_minor) VALUES (?,?,?,?,?,?,?,?,?)""",
                order.orderId, i, line.itemId, line.itemName, line.optionIds.joinStored(),
                line.optionNames.joinStored(), line.quantity, line.unitPriceMinor, line.lineTotalMinor,
            )
        }
    }

    fun find(conn: Connection, orderId: String): StoredOrder? =
        conn.queryList("SELECT * FROM orders WHERE order_id = ?", orderId) { it.toOrderHeader() }
            .singleOrNull()?.withLines(conn)

    fun findByIdempotencyKey(conn: Connection, key: String): StoredOrder? =
        conn.queryList("SELECT * FROM orders WHERE idempotency_key = ?", key) { it.toOrderHeader() }
            .singleOrNull()?.withLines(conn)

    fun all(conn: Connection): List<StoredOrder> =
        conn.queryList("SELECT * FROM orders ORDER BY created_at_millis, order_number") { it.toOrderHeader() }
            .map { it.withLines(conn) }

    fun updateStatus(conn: Connection, orderId: String, status: OrderState) {
        conn.update("UPDATE orders SET status = ? WHERE order_id = ?", status.name, orderId)
    }

    private fun StoredOrder.withLines(conn: Connection): StoredOrder = copy(
        lines = conn.queryList("SELECT * FROM order_lines WHERE order_id = ? ORDER BY line_no", orderId) { rs ->
            StoredLine(
                itemId = rs.getString("item_id"),
                itemName = rs.getString("item_name"),
                optionIds = rs.getString("option_ids").splitStored(),
                optionNames = rs.getString("option_names").splitStored(),
                quantity = rs.getInt("quantity"),
                unitPriceMinor = rs.getLong("unit_price_minor"),
                lineTotalMinor = rs.getLong("line_total_minor"),
            )
        },
    )

    private fun ResultSet.toOrderHeader() = StoredOrder(
        orderId = getString("order_id"),
        orderNumber = getString("order_number"),
        idempotencyKey = getString("idempotency_key"),
        checkoutAttemptId = getString("checkout_attempt_id"),
        status = OrderState.valueOf(getString("status")),
        lines = emptyList(),
        subtotalMinor = getLong("subtotal_minor"),
        discountMinor = getLong("discount_minor"),
        taxMinor = getLong("tax_minor"),
        totalMinor = getLong("total_minor"),
        couponCode = getString("coupon_code"),
        createdAtMillis = getLong("created_at_millis"),
    )
}
