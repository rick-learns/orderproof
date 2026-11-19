package dev.orderproof.emberkitchen.data.net

import com.google.common.truth.Truth.assertThat
import dev.orderproof.contract.ApiHeaders
import dev.orderproof.contract.ErrorCode
import dev.orderproof.contract.OrderLineRequest
import dev.orderproof.contract.PlaceOrderRequest
import dev.orderproof.emberkitchen.domain.ApiOutcome
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Maps transport results onto outcomes; the Unknown/Unavailable split decides whether a retry is safe. */
class EmberApiTest {
    private val server = MockWebServer()
    private lateinit var api: EmberApi
    private val request = PlaceOrderRequest(listOf(OrderLineRequest("fries", emptyList(), 1)), null, 644, "sim_visa_4242")

    @Before
    fun setUp() {
        server.start()
        val base = server.url("/").toString()
        api = EmberApi(HttpClients.create { }) { base }
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun sendsIdempotencyKeyAttemptAndRequestIds() = runTest {
        server.enqueue(MockResponse.Builder().code(201).body(ORDER_JSON).build())
        val outcome = api.placeOrder(request, "key-1", "attempt-1")

        assertThat(outcome).isInstanceOf(ApiOutcome.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.headers[ApiHeaders.IDEMPOTENCY_KEY]).isEqualTo("key-1")
        assertThat(recorded.headers[ApiHeaders.CHECKOUT_ATTEMPT]).isEqualTo("attempt-1")
        assertThat(recorded.headers[ApiHeaders.REQUEST_ID]).isNotEmpty()
    }

    @Test
    fun droppedConnectionAfterRequestIsUnknownAndNotRetriedByTransport() = runTest {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.ShutdownConnection).build())
        server.enqueue(MockResponse.Builder().code(201).body(ORDER_JSON).build())

        assertThat(api.placeOrder(request, "key-1", "attempt-1")).isInstanceOf(ApiOutcome.Unknown::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun serviceUnavailableMeansNothingWasPlaced() = runTest {
        server.enqueue(MockResponse.Builder().code(503).body("""{"code":"SERVICE_UNAVAILABLE","message":"down"}""").build())
        assertThat(api.placeOrder(request, "key-1", "attempt-1")).isInstanceOf(ApiOutcome.Unavailable::class.java)
    }

    @Test
    fun otherServerErrorsAreUnknown() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        assertThat(api.placeOrder(request, "key-1", "attempt-1")).isInstanceOf(ApiOutcome.Unknown::class.java)
    }

    @Test
    fun unreadableSuccessBodyIsUnknownNotUnavailable() = runTest {
        server.enqueue(MockResponse.Builder().code(201).body("{not json").build())
        assertThat(api.placeOrder(request, "key-1", "attempt-1")).isInstanceOf(ApiOutcome.Unknown::class.java)
    }

    @Test
    fun clientErrorsAreDefinitiveRejectionsWithTheirCode() = runTest {
        server.enqueue(MockResponse.Builder().code(409).body("""{"code":"ITEM_UNAVAILABLE","message":"Sold out"}""").build())
        val outcome = api.placeOrder(request, "key-1", "attempt-1") as ApiOutcome.Rejected
        assertThat(outcome.httpStatus).isEqualTo(409)
        assertThat(outcome.error.code).isEqualTo(ErrorCode.ITEM_UNAVAILABLE)
    }

    @Test
    fun refusedConnectionIsUnavailable() = runTest {
        val port = server.port
        server.close()
        val offline = EmberApi(HttpClients.create { }) { "http://127.0.0.1:$port" }
        assertThat(offline.menu()).isInstanceOf(ApiOutcome.Unavailable::class.java)
    }

    @Test
    fun missingAddressIsUnavailable() = runTest {
        val unconfigured = EmberApi(HttpClients.create { }) { null }
        assertThat(unconfigured.menu()).isInstanceOf(ApiOutcome.Unavailable::class.java)
    }

    private companion object {
        val ORDER_JSON = """
            {"orderId":"o1","orderNumber":"EK-1001","status":"PLACED","lines":[],"subtotalMinor":595,
             "discountMinor":0,"taxMinor":49,"totalMinor":644,"paymentStatus":"CAPTURED","placedAt":"2026-06-01T17:00:00Z"}
        """.trimIndent()
    }
}
