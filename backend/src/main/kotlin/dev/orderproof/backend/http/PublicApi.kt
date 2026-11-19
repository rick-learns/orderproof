package dev.orderproof.backend.http

import dev.orderproof.backend.domain.CheckoutService
import dev.orderproof.backend.domain.PlaceOrderResult
import dev.orderproof.backend.domain.PricingException
import dev.orderproof.backend.domain.toMenuResponse
import dev.orderproof.backend.domain.toOrderDto
import dev.orderproof.backend.domain.toQuoteResponse
import dev.orderproof.backend.faults.FaultInjector
import dev.orderproof.backend.faults.HoldRegistry
import dev.orderproof.backend.storage.EventContext
import dev.orderproof.contract.ApiError
import dev.orderproof.contract.ApiHeaders
import dev.orderproof.contract.ApiPaths
import dev.orderproof.contract.ErrorCode
import dev.orderproof.contract.HealthResponse
import dev.orderproof.contract.PlaceOrderRequest
import dev.orderproof.contract.QuoteRequest
import dev.orderproof.control.FaultKind
import dev.orderproof.control.HeldResponse
import com.sun.net.httpserver.HttpExchange

/** Customer-facing API. This is the only surface a phone on the LAN can reach. */
class PublicApi(
    private val checkout: CheckoutService,
    private val faults: FaultInjector,
    private val holds: HoldRegistry,
    private val journal: (kind: String, context: EventContext, orderId: String?, detail: String) -> Unit,
) {
    val router = Router().apply {
        get(ApiPaths.HEALTH) { jsonResult(200, HealthResponse("ok", "orderproof-backend", VERSION)) }
        get(ApiPaths.MENU) { jsonResult(200, checkout.menu().toMenuResponse()) }
        post(ApiPaths.QUOTE) { call -> quote(call) }
        post(ApiPaths.ORDERS) { call -> placeOrder(call) }
        get("${ApiPaths.ORDERS}/{orderId}") { call ->
            orderResult(checkout.findOrder(call.pathParams.getValue("orderId")))
        }
        get("${ApiPaths.CHECKOUTS}/{key}") { call ->
            orderResult(checkout.findByIdempotencyKey(call.pathParams.getValue("key")))
        }
    }

    /** Injected outage: refuse before any work so nothing can be committed. */
    val interceptor: Interceptor = { exchange: HttpExchange ->
        if (faults.consume(FaultKind.SERVICE_UNAVAILABLE)) {
            journal("FAULT_SERVICE_UNAVAILABLE", contextOf(exchange), null, "${exchange.requestMethod} ${exchange.requestURI.path} -> 503")
            jsonResult(503, ApiError(ErrorCode.SERVICE_UNAVAILABLE, "Ember Kitchen is temporarily unavailable."), mapOf("Retry-After" to "5"))
        } else {
            null
        }
    }

    private fun quote(call: HttpCall): HttpResult = try {
        jsonResult(200, checkout.quote(call.body<QuoteRequest>(), call.context()).toQuoteResponse())
    } catch (e: PricingException) {
        jsonResult(422, ApiError(e.code, e.message.orEmpty()))
    }

    private fun placeOrder(call: HttpCall): HttpResult {
        val key = call.header(ApiHeaders.IDEMPOTENCY_KEY)
            ?: return jsonResult(400, ApiError(ErrorCode.MISSING_IDEMPOTENCY_KEY, "Idempotency-Key header is required."))
        val context = call.context()
        return when (val result = checkout.placeOrder(call.body<PlaceOrderRequest>(), key, context)) {
            is PlaceOrderResult.Rejected -> jsonResult(
                status = rejectionStatus(result.code),
                value = ApiError(result.code, result.message, result.cart?.toQuoteResponse(), result.cart?.unavailableItemIds.orEmpty()),
            )
            is PlaceOrderResult.Committed -> {
                if (!result.replayed) applyPostCommitFaults(result, key, context)?.let { return it }
                jsonResult(
                    status = if (result.replayed) 200 else 201,
                    value = result.order.toOrderDto(checkout.paymentStatus(result.order.orderId)),
                    headers = mapOf(ApiHeaders.IDEMPOTENT_REPLAY to result.replayed.toString()),
                )
            }
        }
    }

    private fun applyPostCommitFaults(result: PlaceOrderResult.Committed, key: String, context: EventContext): HttpResult? {
        val orderId = result.order.orderId
        if (faults.consume(FaultKind.DROP_RESPONSE_AFTER_COMMIT)) {
            journal("FAULT_RESPONSE_DROPPED", context, orderId, "order ${result.order.orderNumber} committed; connection closed without response")
            return HttpResult.DropConnection
        }
        if (faults.consume(FaultKind.HOLD_RESPONSE_AFTER_COMMIT)) {
            val holdId = "hold-$orderId"
            journal("FAULT_HOLD_ENTERED", context, orderId, "order ${result.order.orderNumber} committed; response held ($holdId)")
            val released = holds.hold(HeldResponse(holdId, key, orderId))
            journal("FAULT_HOLD_RELEASED", context, orderId, if (released) "released by control" else "safety timeout")
        }
        return null
    }

    private fun orderResult(order: dev.orderproof.backend.domain.StoredOrder?): HttpResult =
        if (order == null) {
            jsonResult(404, ApiError(ErrorCode.NOT_FOUND, "No such order."))
        } else {
            jsonResult(200, order.toOrderDto(checkout.paymentStatus(order.orderId)))
        }

    private fun rejectionStatus(code: ErrorCode): Int = when (code) {
        ErrorCode.ITEM_UNAVAILABLE, ErrorCode.TOTAL_CHANGED -> 409
        ErrorCode.IDEMPOTENCY_KEY_REUSED, ErrorCode.UNKNOWN_ITEM, ErrorCode.INVALID_OPTIONS -> 422
        else -> 400
    }

    private fun HttpCall.context() = EventContext(
        requestId = header(ApiHeaders.REQUEST_ID),
        idempotencyKey = header(ApiHeaders.IDEMPOTENCY_KEY),
        checkoutAttemptId = header(ApiHeaders.CHECKOUT_ATTEMPT),
    )

    companion object {
        const val VERSION = "1.0.0"

        fun contextOf(exchange: HttpExchange) = EventContext(
            requestId = exchange.requestHeaders.getFirst(ApiHeaders.REQUEST_ID),
            idempotencyKey = exchange.requestHeaders.getFirst(ApiHeaders.IDEMPOTENCY_KEY),
            checkoutAttemptId = exchange.requestHeaders.getFirst(ApiHeaders.CHECKOUT_ATTEMPT),
        )
    }
}
