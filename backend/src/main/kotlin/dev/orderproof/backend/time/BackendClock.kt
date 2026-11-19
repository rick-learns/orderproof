package dev.orderproof.backend.time

import java.util.concurrent.atomic.AtomicLong

/** Business time. Tests use [FixedClock] so coupon expiry never depends on real elapsed time. */
interface BackendClock {
    val isFixed: Boolean
    fun nowMillis(): Long
}

class SystemClock : BackendClock {
    override val isFixed = false
    override fun nowMillis(): Long = System.currentTimeMillis()
}

class FixedClock(startMillis: Long) : BackendClock {
    private val now = AtomicLong(startMillis)
    override val isFixed = true
    override fun nowMillis(): Long = now.get()
    fun advance(byMillis: Long): Long = now.addAndGet(byMillis)
    fun set(millis: Long) = now.set(millis)

    companion object {
        /** 2026-06-01T17:00:00Z, a Monday dinner service at Ember Kitchen. */
        const val SEED_EPOCH_MILLIS = 1_780_333_200_000L
    }
}
