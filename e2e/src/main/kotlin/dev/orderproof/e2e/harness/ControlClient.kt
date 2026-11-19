package dev.orderproof.e2e.harness

import dev.orderproof.control.AdvanceClockRequest
import dev.orderproof.control.AdvanceOrderRequest
import dev.orderproof.control.ArmFaultRequest
import dev.orderproof.control.AwaitHoldRequest
import dev.orderproof.control.ControlHeaders
import dev.orderproof.control.ControlPaths
import dev.orderproof.control.FaultKind
import dev.orderproof.control.FaultState
import dev.orderproof.control.LedgerSnapshot
import dev.orderproof.control.SetStockRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Test-side client for the loopback-only control API. The app under test never sees it. */
class ControlClient(private val baseUrl: String, private val token: String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder().readTimeout(70, TimeUnit.SECONDS).build()

    fun reset() = post(ControlPaths.RESET, "{}")

    fun arm(kind: FaultKind, times: Int = 1) = post(ControlPaths.FAULTS, json.encodeToString(ArmFaultRequest.serializer(), ArmFaultRequest(kind, times)))

    fun clearFaults() = send(Request.Builder().url(baseUrl + ControlPaths.FAULTS).delete())

    /** Blocks until the backend parks a committed response; a sync point, not a sleep. */
    fun awaitHold(timeoutMillis: Long): FaultState = decode(
        FaultState.serializer(),
        post(ControlPaths.AWAIT_HOLD, json.encodeToString(AwaitHoldRequest.serializer(), AwaitHoldRequest(timeoutMillis))),
    )

    fun releaseHolds() = post("${ControlPaths.HOLDS}/release", "{}")

    fun setStock(itemId: String, stock: Int) = post(ControlPaths.STOCK, json.encodeToString(SetStockRequest.serializer(), SetStockRequest(itemId, stock)))

    fun advanceClock(byMillis: Long) =
        post("${ControlPaths.CLOCK}/advance", json.encodeToString(AdvanceClockRequest.serializer(), AdvanceClockRequest(byMillis)))

    fun advanceOrder(orderId: String) =
        post("${ControlPaths.ORDER_STATUS}/advance", json.encodeToString(AdvanceOrderRequest.serializer(), AdvanceOrderRequest(orderId)))

    fun ledger(): LedgerSnapshot = decode(LedgerSnapshot.serializer(), send(Request.Builder().url(baseUrl + ControlPaths.LEDGER).get()))

    private fun post(path: String, body: String) =
        send(Request.Builder().url(baseUrl + path).post(body.toRequestBody("application/json".toMediaType())))

    private fun send(builder: Request.Builder): String {
        val request = builder.header(ControlHeaders.AUTHORIZATION, ControlHeaders.BEARER_PREFIX + token).build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            // A broken control channel is an environment problem, not a product verdict.
            if (!response.isSuccessful) throw InfrastructureError("control ${request.method} ${request.url.encodedPath} -> HTTP ${response.code}: $text")
            return text
        }
    }

    private fun <T> decode(serializer: KSerializer<T>, text: String): T = json.decodeFromString(serializer, text)
}

/** Raised for failures of the test environment itself; the verifier classifies them as infrastructure. */
class InfrastructureError(message: String, cause: Throwable? = null) : RuntimeException("INFRASTRUCTURE: $message", cause)
