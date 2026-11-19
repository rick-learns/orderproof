package dev.orderproof.backend

import com.google.common.truth.Truth.assertThat
import dev.orderproof.contract.ApiHeaders
import dev.orderproof.contract.ApiPaths
import dev.orderproof.contract.ContractJson
import dev.orderproof.contract.CouponState
import dev.orderproof.contract.OrderDto
import dev.orderproof.contract.PlaceOrderRequest
import dev.orderproof.contract.QuoteRequest
import dev.orderproof.contract.QuoteResponse
import dev.orderproof.control.AdvanceClockRequest
import dev.orderproof.control.ArmFaultRequest
import dev.orderproof.control.AwaitHoldRequest
import dev.orderproof.control.ControlPaths
import dev.orderproof.control.EventsResponse
import dev.orderproof.control.FaultKind
import dev.orderproof.control.FaultState
import dev.orderproof.control.LedgerSnapshot
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class HttpApiTest {
    private val app = startTestBackend()
    private val http = HttpClient.newHttpClient()
    private val public = "http://127.0.0.1:${app.publicPort}"
    private val control = "http://127.0.0.1:${app.controlPort}"

    @After
    fun tearDown() = app.close()

    @Test
    fun `dropped response after commit, then same-key retry, yields one order`() {
        control("POST", ControlPaths.FAULTS, ContractJson.encodeToString(ArmFaultRequest(FaultKind.DROP_RESPONSE_AFTER_COMMIT)))

        assertThrows(IOException::class.java) { placeOrder("key-A") }
        assertThat(ledger().orders).hasSize(1)

        val retry = placeOrder("key-A")
        assertThat(retry.statusCode()).isEqualTo(200)
        assertThat(retry.headers().firstValue(ApiHeaders.IDEMPOTENT_REPLAY).get()).isEqualTo("true")
        assertThat(ledger().orders).hasSize(1)
        assertThat(ledger().payments).hasSize(1)
        assertThat(events()).containsAtLeast("FAULT_RESPONSE_DROPPED", "IDEMPOTENT_REPLAY").inOrder()
    }

    @Test
    fun `retry with a fresh key after a lost response duplicates the order`() {
        // The backend cannot know two keys are one intent; this is why the client must persist its key.
        control("POST", ControlPaths.FAULTS, ContractJson.encodeToString(ArmFaultRequest(FaultKind.DROP_RESPONSE_AFTER_COMMIT)))
        assertThrows(IOException::class.java) { placeOrder("key-A") }
        assertThat(placeOrder("key-B").statusCode()).isEqualTo(201)
        assertThat(ledger().orders).hasSize(2)
        assertThat(ledger().payments).hasSize(2)
    }

    @Test
    fun `held response is a sync point the test controls`() {
        control("POST", ControlPaths.FAULTS, ContractJson.encodeToString(ArmFaultRequest(FaultKind.HOLD_RESPONSE_AFTER_COMMIT)))
        val pending = CompletableFuture.supplyAsync { placeOrder("key-H") }

        val held = ContractJson.decodeFromString<FaultState>(
            control("POST", ControlPaths.AWAIT_HOLD, ContractJson.encodeToString(AwaitHoldRequest(10_000))).body(),
        ).held
        assertThat(held.map { it.idempotencyKey }).containsExactly("key-H")
        assertThat(ledger().orders).hasSize(1)
        assertThat(pending.isDone).isFalse()

        control("POST", "${ControlPaths.HOLDS}/release", "")
        assertThat(pending.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(201)
    }

    @Test
    fun `service unavailable refuses work until cleared`() {
        control("POST", ControlPaths.FAULTS, ContractJson.encodeToString(ArmFaultRequest(FaultKind.SERVICE_UNAVAILABLE, times = -1)))
        assertThat(get(ApiPaths.MENU).statusCode()).isEqualTo(503)
        assertThat(placeOrder("key-U").statusCode()).isEqualTo(503)
        assertThat(ledger().orders).isEmpty()

        control("DELETE", ControlPaths.FAULTS, null)
        assertThat(get(ApiPaths.MENU).statusCode()).isEqualTo(200)
        assertThat(placeOrder("key-U").statusCode()).isEqualTo(201)
    }

    @Test
    fun `checkout lookup by key supports reconciliation`() {
        assertThat(get("${ApiPaths.CHECKOUTS}/key-R").statusCode()).isEqualTo(404)
        val created = ContractJson.decodeFromString<OrderDto>(placeOrder("key-R").body())
        val found = ContractJson.decodeFromString<OrderDto>(get("${ApiPaths.CHECKOUTS}/key-R").body())
        assertThat(found.orderId).isEqualTo(created.orderId)
        assertThat(found.paymentStatus).isEqualTo("CAPTURED")
    }

    @Test
    fun `advancing the fixed clock expires a coupon`() {
        val body = ContractJson.encodeToString(QuoteRequest(listOf(singleBurger.copy(quantity = 2)), "EMBER10"))
        assertThat(quote(body).coupon?.state).isEqualTo(CouponState.APPLIED)
        control("POST", "${ControlPaths.CLOCK}/advance", ContractJson.encodeToString(AdvanceClockRequest(3 * 60 * 60 * 1000L)))
        assertThat(quote(body).coupon?.state).isEqualTo(CouponState.EXPIRED)
    }

    @Test
    fun `reset restores seed state`() {
        placeOrder("key-1")
        control("POST", ControlPaths.RESET, "")
        val snapshot = ledger()
        assertThat(snapshot.orders).isEmpty()
        assertThat(snapshot.payments).isEmpty()
        assertThat(snapshot.stock.first { it.itemId == "ember-smash-burger" }.stock).isEqualTo(20)
    }

    @Test
    fun `control API requires the bearer token and is not exposed publicly`() {
        val noToken = http.send(HttpRequest.newBuilder(URI("$control${ControlPaths.LEDGER}")).build(), HttpResponse.BodyHandlers.ofString())
        assertThat(noToken.statusCode()).isEqualTo(401)
        val wrongToken = http.send(
            HttpRequest.newBuilder(URI("$control${ControlPaths.LEDGER}")).header("Authorization", "Bearer nope").build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertThat(wrongToken.statusCode()).isEqualTo(401)
        assertThat(get(ControlPaths.LEDGER).statusCode()).isEqualTo(404)
    }

    @Test
    fun `missing idempotency key is rejected`() {
        val request = HttpRequest.newBuilder(URI("$public${ApiPaths.ORDERS}"))
            .POST(HttpRequest.BodyPublishers.ofString(ContractJson.encodeToString(singleBurgerOrder()))).build()
        assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400)
    }

    private fun placeOrder(key: String, body: PlaceOrderRequest = singleBurgerOrder()): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("$public${ApiPaths.ORDERS}"))
            .header(ApiHeaders.IDEMPOTENCY_KEY, key)
            .POST(HttpRequest.BodyPublishers.ofString(ContractJson.encodeToString(body)))
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun quote(body: String): QuoteResponse {
        val request = HttpRequest.newBuilder(URI("$public${ApiPaths.QUOTE}")).POST(HttpRequest.BodyPublishers.ofString(body)).build()
        return ContractJson.decodeFromString(http.send(request, HttpResponse.BodyHandlers.ofString()).body())
    }

    private fun get(path: String) = http.send(HttpRequest.newBuilder(URI("$public$path")).build(), HttpResponse.BodyHandlers.ofString())

    private fun control(method: String, path: String, body: String?): HttpResponse<String> {
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        val request = HttpRequest.newBuilder(URI("$control$path"))
            .header("Authorization", "Bearer $TEST_TOKEN")
            .method(method, publisher)
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString()).also {
            check(it.statusCode() == 200) { "control $method $path -> ${it.statusCode()} ${it.body()}" }
        }
    }

    private fun ledger(): LedgerSnapshot = ContractJson.decodeFromString(control("GET", ControlPaths.LEDGER, null).body())

    private fun events(): List<String> =
        ContractJson.decodeFromString<EventsResponse>(control("GET", ControlPaths.EVENTS, null).body()).events.map { it.kind }
}
