package dev.orderproof.emberkitchen.candidate

import dev.orderproof.emberkitchen.domain.checkout.CheckoutKeyPolicy
import java.util.UUID

/**
 * ⚠ FAULTY CANDIDATE — DELIBERATE DEFECT FOR RELEASE-GATE DEMONSTRATION. DO NOT SHIP. ⚠
 *
 * This file exists only in the `faulty` flavor. It models a plausible regression: the key is
 * generated per HTTP send instead of per checkout attempt. Every screen still looks normal,
 * but a retry after a lost response (or a resend after process death) becomes a brand-new
 * order with a second simulated payment. The release gate must catch this from backend records.
 */
object CandidateBuild {
    const val LABEL = "faulty"
    const val IS_FAULTY = true

    /** DELIBERATE DEFECT: ignores the persisted key and mints a fresh one on every send. */
    val checkoutKeys = CheckoutKeyPolicy { _ -> UUID.randomUUID().toString() }
}
