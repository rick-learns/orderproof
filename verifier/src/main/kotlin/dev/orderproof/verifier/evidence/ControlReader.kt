package dev.orderproof.verifier.evidence

import dev.orderproof.control.BackendEvent
import dev.orderproof.control.ControlHeaders
import dev.orderproof.control.ControlPaths
import dev.orderproof.control.EventsResponse
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Reads the backend's own event journal for the evidence bundle. The ledger is read by the oracle, not here. */
class ControlReader(private val baseUrl: String, private val token: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Events since the last reset (a reset restarts the sequence), i.e. exactly one scenario's worth. */
    fun events(): List<BackendEvent> {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl${ControlPaths.EVENTS}?after=0"))
            .header(ControlHeaders.AUTHORIZATION, ControlHeaders.BEARER_PREFIX + token)
            .timeout(Duration.ofSeconds(10))
            .GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "events -> HTTP ${response.statusCode()}" }
        return json.decodeFromString(EventsResponse.serializer(), response.body()).events
    }
}
