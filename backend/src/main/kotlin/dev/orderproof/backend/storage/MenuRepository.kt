package dev.orderproof.backend.storage

import dev.orderproof.backend.domain.MenuItem
import dev.orderproof.backend.domain.MenuOption
import dev.orderproof.backend.domain.OptionGroup
import java.sql.Connection

object MenuRepository {
    fun load(conn: Connection): List<MenuItem> {
        val options = mutableMapOf<String, MutableList<MenuOption>>()
        conn.query("SELECT id, group_id, name, price_delta_minor FROM options ORDER BY sort") { rs ->
            options.getOrPut(rs.getString("group_id")) { mutableListOf() } +=
                MenuOption(rs.getString("id"), rs.getString("name"), rs.getLong("price_delta_minor"))
        }
        val groups = mutableMapOf<String, MutableList<OptionGroup>>()
        conn.query("SELECT id, item_id, name, required, max_selections FROM option_groups ORDER BY sort") { rs ->
            val id = rs.getString("id")
            groups.getOrPut(rs.getString("item_id")) { mutableListOf() } += OptionGroup(
                id = id,
                name = rs.getString("name"),
                required = rs.getInt("required") == 1,
                maxSelections = rs.getInt("max_selections"),
                options = options[id].orEmpty(),
            )
        }
        val items = mutableListOf<MenuItem>()
        conn.query("SELECT * FROM menu_items ORDER BY sort") { rs ->
            val id = rs.getString("id")
            items += MenuItem(
                id = id,
                name = rs.getString("name"),
                description = rs.getString("description"),
                category = rs.getString("category"),
                basePriceMinor = rs.getLong("base_price_minor"),
                stock = rs.getInt("stock"),
                optionGroups = groups[id].orEmpty(),
            )
        }
        return items
    }

    fun insert(conn: Connection, item: MenuItem, sort: Int) {
        conn.update(
            "INSERT INTO menu_items (id, name, description, category, base_price_minor, stock, sort) VALUES (?,?,?,?,?,?,?)",
            item.id, item.name, item.description, item.category, item.basePriceMinor, item.stock, sort,
        )
        item.optionGroups.forEachIndexed { gi, group ->
            conn.update(
                "INSERT INTO option_groups (id, item_id, name, required, max_selections, sort) VALUES (?,?,?,?,?,?)",
                group.id, item.id, group.name, if (group.required) 1 else 0, group.maxSelections, gi,
            )
            group.options.forEachIndexed { oi, option ->
                conn.update(
                    "INSERT INTO options (id, group_id, name, price_delta_minor, sort) VALUES (?,?,?,?,?)",
                    option.id, group.id, option.name, option.priceDeltaMinor, oi,
                )
            }
        }
    }

    /** Returns false when the item does not exist. */
    fun setStock(conn: Connection, itemId: String, stock: Int): Boolean =
        conn.update("UPDATE menu_items SET stock = ? WHERE id = ?", stock, itemId) == 1

    fun decrementStock(conn: Connection, itemId: String, quantity: Int) {
        val changed = conn.update(
            "UPDATE menu_items SET stock = stock - ? WHERE id = ? AND stock >= ?",
            quantity, itemId, quantity,
        )
        check(changed == 1) { "Stock for $itemId would go negative" }
    }
}
