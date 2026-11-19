package dev.orderproof.emberkitchen.data.net

import dev.orderproof.contract.ApiError
import dev.orderproof.contract.ApiHeaders
import dev.orderproof.contract.ApiPaths
import dev.orderproof.contract.ContractJson
import dev.orderproof.contract.ErrorCode
import dev.orderproof.contract.HealthResponse
import dev.orderproof.contract.MenuResponse
import dev.orderproof.contract.OrderDto
import dev.orderproof.contract.PlaceOrderRequest
import dev.orderproof.contract.QuoteRequest
import dev.orderproof.contract.QuoteResponse
import dev.orderproof.emberkitchen.domain.ApiOutcome
import dev.orderproof.emberkitchen.domain.CheckoutGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.UUID

/** Public Ember Kitchen API. Knows nothing about the control API or test credentials. */
class EmberApi(
    private val client: OkHttpClient,
    private val baseUrl: suspend () -> String?,
) : CheckoutGateway {

    suspend fun health(url: String): ApiOutcome<HealthResponse> =
        execute(url, "GET", ApiPaths.HEALTH, null, HealthResponse.serializer())

    suspend fun menu(): ApiOutcome<MenuResponse> = call("GET", ApiPaths.MENU, null, MenuResponse.serializer())

    suspend fun quote(request: QuoteRequest): ApiOutcome<QuoteResponse> =
        call("POST", ApiPaths.QUOTE, ContractJson.encodeToString(QuoteRequest.serializer(), request), QuoteResponse.serializer())

    suspend fun order(orderId: String): ApiOutcome<OrderDto> =
        call("GET", "${ApiPaths.ORDERS}/$orderId", null, OrderDto.serializer())

    override suspend fun placeOrder(request: PlaceOrderRequest, idempotencyKey: String, attemptId: String): ApiOutcome<OrderDto> =
        call(
            method = "POST",
            path = ApiPaths.ORDERS,
            body = ContractJson.encodeToString(PlaceOrderRequest.serializer(), request),
            serializer = OrderDto.serializer(),
            headers = mapOf(ApiHeaders.IDEMPOTENCY_KEY to idempotencyKey, ApiHeaders.CHECKOUT_ATTEMPT to attemptId),
        )

    override suspend fun lookupCheckout(idempotencyKey: String): ApiOutcome<OrderDto> =
        call("GET", "${ApiPaths.CHECKOUTS}/$idempotencyKey", null, OrderDto.serializer())

    private suspend fun <T> call(
        method: String,
        path: String,
        body: String?,
        serializer: KSerializer<T>,
        headers: Map<String, String> = emptyMap(),
    ): ApiOutcome<T> {
        val base = baseUrl() ?: return ApiOutcome.Unavailable("Backend address is not configured")
        return execute(base, method, path, body, serializer, headers)
    }

    private suspend fun <T> execute(
        base: String,
        method: String,
        path: String,
        body: String?,
        serializer: KSerializer<T>,
        headers: Map<String, String> = emptyMap(),
    ): ApiOutcome<T> = withContext(Dispatchers.IO) {
        val request = try {
            Request.Builder()
                .url(base.trimEnd('/') + path)
                .method(method, body?.toRequestBody(JSON))
                .header(ApiHeaders.REQUEST_ID, UUID.randomUUID().toString())
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
        } catch (e: IllegalArgumentException) {
            return@withContext ApiOutcome.Unavailable("Invalid backend address: ${e.message}")
        }
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                when {
                    response.isSuccessful -> decodeSuccess(text, serializer)
                    response.code == 503 -> ApiOutcome.Unavailable("HTTP 503")
                    response.code >= 500 -> ApiOutcome.Unknown("HTTP ${response.code}")
                    else -> ApiOutcome.Rejected(response.code, parseError(text, response.code))
                }
            }
        } catch (e: ConnectException) {
            ApiOutcome.Unavailable("Could not connect: ${e.message}")
        } catch (e: UnknownHostException) {
            ApiOutcome.Unavailable("Unknown host: ${e.message}")
        } catch (e: NoRouteToHostException) {
            ApiOutcome.Unavailable("No route to host: ${e.message}")
        } catch (e: IOException) {
            ApiOutcome.Unknown("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** A 2xx we cannot read may still mean the server committed, so it is Unknown, not Unavailable. */
    private fun <T> decodeSuccess(text: String, serializer: KSerializer<T>): ApiOutcome<T> =
        runCatching { ContractJson.decodeFromString(serializer, text) }
            .fold({ ApiOutcome.Success(it) }, { ApiOutcome.Unknown("Unreadable success body: ${it.message}") })

    private fun parseError(text: String, status: Int): ApiError =
        runCatching { ContractJson.decodeFromString(ApiError.serializer(), text) }
            .getOrElse { ApiError(if (status == 404) ErrorCode.NOT_FOUND else ErrorCode.INVALID_REQUEST, "HTTP $status") }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
