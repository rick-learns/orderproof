package dev.orderproof.backend.seed

import dev.orderproof.backend.storage.CouponRepository
import dev.orderproof.backend.storage.MenuRepository
import dev.orderproof.backend.storage.Schema
import dev.orderproof.backend.storage.update
import java.sql.Connection

object Seeder {
    fun isEmpty(conn: Connection): Boolean = MenuRepository.load(conn).isEmpty()

    /** Wipes every table (orders, payments, events included) and restores the seed catalog. */
    fun reset(conn: Connection, nowMillis: Long) {
        Schema.tablesInDeleteOrder.forEach { conn.update("DELETE FROM $it") }
        conn.update("DELETE FROM sqlite_sequence WHERE name = 'events'")
        SeedCatalog.menu.forEachIndexed { i, item -> MenuRepository.insert(conn, item, i) }
        SeedCatalog.coupons(nowMillis).forEach { CouponRepository.insert(conn, it) }
    }
}
