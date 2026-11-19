package dev.orderproof.backend.faults

import dev.orderproof.control.ArmedFault
import dev.orderproof.control.FaultKind

/**
 * Explicitly armed, count-based faults. Nothing here is random or time-based: a fault fires on
 * exactly the next N matching requests after a test arms it, and never otherwise.
 */
class FaultInjector {
    private val remaining = mutableMapOf<FaultKind, Int>()

    @Synchronized
    fun arm(kind: FaultKind, times: Int) {
        require(times == UNTIL_CLEARED || times > 0) { "times must be positive or $UNTIL_CLEARED" }
        remaining[kind] = times
    }

    /** Returns true if [kind] is armed, consuming one use. */
    @Synchronized
    fun consume(kind: FaultKind): Boolean {
        val left = remaining[kind] ?: return false
        when {
            left == UNTIL_CLEARED -> Unit
            left <= 1 -> remaining.remove(kind)
            else -> remaining[kind] = left - 1
        }
        return true
    }

    @Synchronized
    fun clear() = remaining.clear()

    @Synchronized
    fun snapshot(): List<ArmedFault> = remaining.map { (kind, left) -> ArmedFault(kind, left) }

    companion object {
        const val UNTIL_CLEARED = -1
    }
}
