package dev.orderproof.emberkitchen.candidate

import dev.orderproof.emberkitchen.domain.checkout.CheckoutKeyPolicy

/** CORRECTED candidate: the build intended for release and for the physical-phone handoff. */
object CandidateBuild {
    const val LABEL = "corrected"
    const val IS_FAULTY = false

    /** Reuses the key that was persisted before the first send. */
    val checkoutKeys = CheckoutKeyPolicy { attempt -> attempt.idempotencyKey }
}
