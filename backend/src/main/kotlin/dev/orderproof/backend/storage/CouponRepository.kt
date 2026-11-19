package dev.orderproof.backend.storage

import dev.orderproof.backend.domain.Coupon
import dev.orderproof.backend.domain.CouponKind
import java.sql.Connection

object CouponRepository {
    fun find(conn: Connection, code: String): Coupon? =
        conn.queryList("SELECT * FROM coupons WHERE code = ?", code) { rs ->
            Coupon(
                code = rs.getString("code"),
                kind = CouponKind.valueOf(rs.getString("kind")),
                value = rs.getLong("value"),
                minSubtotalMinor = rs.getLong("min_subtotal_minor"),
                expiresAtMillis = rs.getLongOrNull("expires_at_millis"),
            )
        }.singleOrNull()

    fun insert(conn: Connection, coupon: Coupon) {
        conn.update(
            "INSERT INTO coupons (code, kind, value, min_subtotal_minor, expires_at_millis) VALUES (?,?,?,?,?)",
            coupon.code, coupon.kind.name, coupon.value, coupon.minSubtotalMinor, coupon.expiresAtMillis,
        )
    }
}
