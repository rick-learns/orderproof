package dev.orderproof.backend.http

import com.sun.net.httpserver.HttpExchange
import dev.orderproof.contract.ContractJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/** A request as seen by a route handler. */
class HttpCall(
    private val exchange: HttpExchange,
    val pathParams: Map<String, String>,
) {
    val method: String = exchange.requestMethod
    val path: String = exchange.requestURI.path
    val bodyText: String by lazy { exchange.requestBody.readBytes().decodeToString() }

    fun header(name: String): String? = exchange.requestHeaders.getFirst(name)?.takeIf { it.isNotBlank() }

    fun query(name: String): String? = exchange.requestURI.rawQuery
        ?.split('&')
        ?.map { it.split('=', limit = 2) }
        ?.firstOrNull { it[0] == name }
        ?.getOrNull(1)

    fun <T> body(serializer: KSerializer<T>): T = try {
        ContractJson.decodeFromString(serializer, bodyText)
    } catch (e: IllegalArgumentException) {
        throw BadRequestException("Malformed request body: ${e.message}")
    }

    inline fun <reified T> body(): T = body(serializer<T>())
}

class BadRequestException(message: String) : Exception(message)

/** What a handler wants the server to do with the connection. */
sealed interface HttpResult {
    data class Json(val status: Int, val body: String, val headers: Map<String, String> = emptyMap()) : HttpResult

    /** Close the socket without writing a response: the client sees the response as lost. */
    data object DropConnection : HttpResult
}

inline fun <reified T> jsonResult(status: Int, value: T, headers: Map<String, String> = emptyMap()) =
    HttpResult.Json(status, ContractJson.encodeToString(serializer<T>(), value), headers)
