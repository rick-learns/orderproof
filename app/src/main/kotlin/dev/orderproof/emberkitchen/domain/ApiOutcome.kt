package dev.orderproof.emberkitchen.domain

import dev.orderproof.contract.ApiError
import dev.orderproof.contract.OrderDto
import dev.orderproof.contract.PlaceOrderRequest

/**
 * Every network call ends in exactly one of these. The split between [Unavailable] and
 * [Unknown] matters: only [Unknown] means "the server may have committed something".
 */
sealed interface ApiOutcome<out T> {
    data class Success<T>(val value: T) : ApiOutcome<T>

    /** A definitive refusal from the server; nothing was committed. */
    data class Rejected(val httpStatus: Int, val error: ApiError) : ApiOutcome<Nothing>

    /** The service refused before doing work (HTTP 503) or could not be reached at all. */
    data class Unavailable(val detail: String) : ApiOutcome<Nothing>

    /** The request may have been processed but no answer arrived (lost response, timeout, 5xx). */
    data class Unknown(val detail: String) : ApiOutcome<Nothing>
}

interface CheckoutGateway {
    suspend fun placeOrder(request: PlaceOrderRequest, idempotencyKey: String, attemptId: String): ApiOutcome<OrderDto>

    /** Finds the order committed under [idempotencyKey]; [ApiOutcome.Rejected] with 404 if none. */
    suspend fun lookupCheckout(idempotencyKey: String): ApiOutcome<OrderDto>
}
