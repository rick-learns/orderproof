package dev.orderproof.backend.storage

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

internal fun PreparedStatement.bind(params: Array<out Any?>) {
    params.forEachIndexed { i, value ->
        val index = i + 1
        when (value) {
            null -> setNull(index, Types.NULL)
            is String -> setString(index, value)
            is Int -> setInt(index, value)
            is Long -> setLong(index, value)
            is Boolean -> setInt(index, if (value) 1 else 0)
            else -> error("Unsupported JDBC parameter type: ${value::class}")
        }
    }
}

internal fun Connection.update(sql: String, vararg params: Any?): Int =
    prepareStatement(sql).use { st ->
        st.bind(params)
        st.executeUpdate()
    }

internal fun Connection.query(sql: String, vararg params: Any?, row: (ResultSet) -> Unit) {
    prepareStatement(sql).use { st ->
        st.bind(params)
        st.executeQuery().use { rs -> while (rs.next()) row(rs) }
    }
}

internal fun <T> Connection.queryList(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> {
    val out = mutableListOf<T>()
    query(sql, *params) { out += map(it) }
    return out
}

internal fun ResultSet.getLongOrNull(column: String): Long? = getLong(column).takeUnless { wasNull() }

/** Option ids and names never contain the separator; seed data enforces this. */
internal const val LIST_SEPARATOR = "|"

internal fun List<String>.joinStored(): String = joinToString(LIST_SEPARATOR)

internal fun String.splitStored(): List<String> = if (isEmpty()) emptyList() else split(LIST_SEPARATOR)
