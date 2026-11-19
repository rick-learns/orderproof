package dev.orderproof.backend.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.orderproof.contract.ApiError
import dev.orderproof.contract.ErrorCode
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/** Hook invoked before routing; may short-circuit (auth, injected outages). */
typealias Interceptor = (HttpExchange) -> HttpResult?

/** Called once per finished exchange, used for logs and the event journal. */
typealias Completion = (exchange: HttpExchange, outcome: String) -> Unit

object HttpServers {
    fun start(
        host: String,
        port: Int,
        router: Router,
        interceptor: Interceptor = { null },
        onComplete: Completion = { _, _ -> },
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress(host, port), 0)
        // Held responses park a thread each, so the pool must grow rather than starve.
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { exchange -> serve(exchange, router, interceptor, onComplete) }
        server.start()
        return server
    }

    private fun serve(exchange: HttpExchange, router: Router, interceptor: Interceptor, onComplete: Completion) {
        val result = try {
            interceptor(exchange) ?: router.match(exchange)?.let { (handler, call) -> handler(call) }
                ?: jsonResult(404, ApiError(ErrorCode.NOT_FOUND, "No route for ${exchange.requestMethod} ${exchange.requestURI.path}"))
        } catch (e: BadRequestException) {
            jsonResult(400, ApiError(ErrorCode.INVALID_REQUEST, e.message.orEmpty()))
        } catch (e: Exception) {
            System.err.println("Unhandled error for ${exchange.requestURI}: $e")
            jsonResult(500, ApiError(ErrorCode.INTERNAL_ERROR, "Internal error"))
        }
        val outcome = write(exchange, result)
        onComplete(exchange, outcome)
    }

    private fun write(exchange: HttpExchange, result: HttpResult): String = when (result) {
        HttpResult.DropConnection -> {
            // Request body must be drained before close so the JDK server aborts the connection
            // instead of trying to reuse it; no status line is ever written.
            runCatching { exchange.requestBody.readAllBytes() }
            exchange.close()
            "DROPPED"
        }
        is HttpResult.Json -> try {
            val bytes = result.body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            result.headers.forEach { (k, v) -> exchange.responseHeaders.add(k, v) }
            exchange.sendResponseHeaders(result.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            result.status.toString()
        } catch (e: IOException) {
            exchange.close()
            "${result.status} (client gone: ${e.message})"
        }
    }
}
