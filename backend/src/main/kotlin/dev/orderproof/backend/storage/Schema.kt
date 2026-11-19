package dev.orderproof.backend.storage

import java.sql.Connection

internal object Schema {
    private val statements = listOf(
        """
        CREATE TABLE IF NOT EXISTS menu_items (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            description TEXT NOT NULL,
            category TEXT NOT NULL,
            base_price_minor INTEGER NOT NULL CHECK (base_price_minor >= 0),
            stock INTEGER NOT NULL CHECK (stock >= 0),
            sort INTEGER NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS option_groups (
            id TEXT PRIMARY KEY,
            item_id TEXT NOT NULL REFERENCES menu_items(id),
            name TEXT NOT NULL,
            required INTEGER NOT NULL,
            max_selections INTEGER NOT NULL,
            sort INTEGER NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS options (
            id TEXT PRIMARY KEY,
            group_id TEXT NOT NULL REFERENCES option_groups(id),
            name TEXT NOT NULL,
            price_delta_minor INTEGER NOT NULL CHECK (price_delta_minor >= 0),
            sort INTEGER NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS coupons (
            code TEXT PRIMARY KEY,
            kind TEXT NOT NULL CHECK (kind IN ('PERCENT', 'FIXED')),
            value INTEGER NOT NULL,
            min_subtotal_minor INTEGER NOT NULL,
            expires_at_millis INTEGER
        )""",
        """
        CREATE TABLE IF NOT EXISTS orders (
            order_id TEXT PRIMARY KEY,
            order_number TEXT NOT NULL UNIQUE,
            idempotency_key TEXT NOT NULL UNIQUE,
            checkout_attempt_id TEXT,
            status TEXT NOT NULL,
            subtotal_minor INTEGER NOT NULL,
            discount_minor INTEGER NOT NULL,
            tax_minor INTEGER NOT NULL,
            total_minor INTEGER NOT NULL,
            coupon_code TEXT,
            created_at_millis INTEGER NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS order_lines (
            order_id TEXT NOT NULL REFERENCES orders(order_id),
            line_no INTEGER NOT NULL,
            item_id TEXT NOT NULL,
            item_name TEXT NOT NULL,
            option_ids TEXT NOT NULL,
            option_names TEXT NOT NULL,
            quantity INTEGER NOT NULL CHECK (quantity > 0),
            unit_price_minor INTEGER NOT NULL,
            line_total_minor INTEGER NOT NULL,
            PRIMARY KEY (order_id, line_no)
        )""",
        """
        CREATE TABLE IF NOT EXISTS payments (
            payment_id TEXT PRIMARY KEY,
            order_id TEXT NOT NULL UNIQUE REFERENCES orders(order_id),
            amount_minor INTEGER NOT NULL,
            status TEXT NOT NULL,
            method TEXT NOT NULL,
            created_at_millis INTEGER NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS idempotency_records (
            idempotency_key TEXT PRIMARY KEY,
            order_id TEXT NOT NULL REFERENCES orders(order_id),
            request_hash TEXT NOT NULL,
            replay_count INTEGER NOT NULL DEFAULT 0
        )""",
        """
        CREATE TABLE IF NOT EXISTS events (
            seq INTEGER PRIMARY KEY AUTOINCREMENT,
            at_millis INTEGER NOT NULL,
            wall_clock_millis INTEGER NOT NULL,
            kind TEXT NOT NULL,
            request_id TEXT,
            idempotency_key TEXT,
            checkout_attempt_id TEXT,
            order_id TEXT,
            detail TEXT NOT NULL
        )""",
    )

    /** Child tables first so foreign keys never block a wipe. */
    val tablesInDeleteOrder = listOf(
        "events", "idempotency_records", "payments", "order_lines", "orders",
        "coupons", "options", "option_groups", "menu_items",
    )

    fun create(connection: Connection) {
        connection.createStatement().use { st -> statements.forEach { st.execute(it.trimIndent()) } }
    }
}
