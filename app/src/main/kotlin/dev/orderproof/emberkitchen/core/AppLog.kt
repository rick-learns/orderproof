package dev.orderproof.emberkitchen.core

import android.util.Log
import dev.orderproof.emberkitchen.candidate.CandidateBuild

/**
 * Structured `key=value` log lines under one tag. The verifier filters logcat on [TAG] and
 * joins these lines with backend events on `attempt=`, `key=` and `request=`.
 */
object AppLog {
    const val TAG = "OrderProof"

    fun i(message: String) {
        Log.i(TAG, "candidate=${CandidateBuild.LABEL} $message")
    }
}
