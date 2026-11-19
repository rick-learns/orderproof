package dev.orderproof.backend.faults

import dev.orderproof.control.HeldResponse
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Synchronization point for "commit happened, response not yet delivered". A request thread
 * parks in [hold]; tests observe it with [awaitHeld] and let it go with [releaseAll]. All waits
 * are condition waits with an explicit deadline, never fixed sleeps.
 */
class HoldRegistry(private val safetyTimeoutMillis: Long = 120_000) {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val held = linkedMapOf<String, HeldResponse>()
    private val released = mutableListOf<String>()

    /** Blocks the caller until released. Returns false if the safety timeout fired instead. */
    fun hold(response: HeldResponse): Boolean = lock.withLock {
        held[response.holdId] = response
        changed.signalAll()
        var nanos = TimeUnit.MILLISECONDS.toNanos(safetyTimeoutMillis)
        while (response.holdId in held && nanos > 0) nanos = changed.awaitNanos(nanos)
        val wasReleased = response.holdId !in held
        if (!wasReleased) held.remove(response.holdId)
        changed.signalAll()
        wasReleased
    }

    fun awaitHeld(timeoutMillis: Long): List<HeldResponse> = lock.withLock {
        var nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (held.isEmpty() && nanos > 0) nanos = changed.awaitNanos(nanos)
        held.values.toList()
    }

    fun releaseAll(): List<String> = lock.withLock {
        val ids = held.keys.toList()
        held.clear()
        released += ids
        changed.signalAll()
        ids
    }

    fun reset() = lock.withLock {
        held.clear()
        released.clear()
        changed.signalAll()
    }

    fun heldNow(): List<HeldResponse> = lock.withLock { held.values.toList() }

    fun releasedIds(): List<String> = lock.withLock { released.toList() }
}
