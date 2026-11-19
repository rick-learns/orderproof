package dev.orderproof.backend.storage

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * One SQLite connection behind one lock. Every unit of work is a serialized transaction, which
 * makes "check idempotency key, then commit order + payment" atomic even under concurrent retries.
 * Throughput is irrelevant for a local test backend; correctness is not.
 */
class Database(file: Path) : AutoCloseable {
    private val lock = ReentrantLock()
    private val connection: Connection

    init {
        file.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        connection = DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}")
        connection.createStatement().use { st ->
            st.execute("PRAGMA journal_mode=WAL")
            st.execute("PRAGMA foreign_keys=ON")
            st.execute("PRAGMA synchronous=FULL")
        }
        transaction { Schema.create(it) }
    }

    fun <T> transaction(block: (Connection) -> T): T = lock.withLock {
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = true
        }
    }

    override fun close() = lock.withLock { connection.close() }
}
