package dev.orderproof.contract

import kotlinx.serialization.json.Json

object ApiHeaders {
    const val IDEMPOTENCY_KEY = "Idempotency-Key"
    const val REQUEST_ID = "X-Request-Id"

    /** Diagnostic only: lets evidence correlate retries of one customer checkout. */
    const val CHECKOUT_ATTEMPT = "X-Checkout-Attempt"
    const val IDEMPOTENT_REPLAY = "Idempotent-Replay"
}

object ApiPaths {
    const val HEALTH = "/v1/health"
    const val MENU = "/v1/menu"
    const val QUOTE = "/v1/quote"
    const val ORDERS = "/v1/orders"

    /** `GET /v1/checkouts/{idempotencyKey}` returns the committed order for a key, or 404. */
    const val CHECKOUTS = "/v1/checkouts"
}

/** One JSON configuration for every component so wire formats cannot drift. */
val ContractJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
