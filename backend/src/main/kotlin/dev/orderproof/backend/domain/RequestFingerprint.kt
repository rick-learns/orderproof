package dev.orderproof.backend.domain

import dev.orderproof.contract.PlaceOrderRequest
import java.security.MessageDigest

/**
 * Canonical hash of a place-order body. Reusing an idempotency key with a different body is a
 * client bug, so the server refuses it instead of silently replaying a different order.
 */
object RequestFingerprint {
    fun of(request: PlaceOrderRequest): String {
        val canonical = buildString {
            request.lines.forEach { line ->
                append(line.itemId).append(':').append(line.optionIds.sorted().joinToString(","))
                append('x').append(line.quantity).append(';')
            }
            append("coupon=").append(request.couponCode?.trim()?.uppercase().orEmpty())
            append(";total=").append(request.expectedTotalMinor)
            append(";pay=").append(request.paymentToken)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
