package dev.orderproof.emberkitchen.domain.checkout

/**
 * Decides which idempotency key accompanies each send of a checkout attempt.
 *
 * Contract (docs/behavior-contract.md, C3): every send of one attempt, including retries after
 * a lost response and resends after process death, must carry the key persisted in the attempt.
 * Each build flavor supplies its implementation in `candidate/CandidateBuild.kt`.
 */
fun interface CheckoutKeyPolicy {
    fun keyForSend(attempt: CheckoutAttempt): String
}
