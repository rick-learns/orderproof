package dev.orderproof.backend.http

import com.sun.net.httpserver.HttpExchange

typealias Handler = (HttpCall) -> HttpResult

/** Minimal method + path-template router (`/v1/orders/{id}`). */
class Router {
    private data class Route(val method: String, val segments: List<String>, val handler: Handler)

    private val routes = mutableListOf<Route>()

    fun get(pattern: String, handler: Handler) = add("GET", pattern, handler)
    fun post(pattern: String, handler: Handler) = add("POST", pattern, handler)
    fun delete(pattern: String, handler: Handler) = add("DELETE", pattern, handler)

    private fun add(method: String, pattern: String, handler: Handler) {
        routes += Route(method, pattern.trim('/').split('/'), handler)
    }

    /** Returns null when no route matches. */
    fun match(exchange: HttpExchange): Pair<Handler, HttpCall>? {
        val parts = exchange.requestURI.path.trim('/').split('/')
        for (route in routes) {
            if (route.method != exchange.requestMethod || route.segments.size != parts.size) continue
            val params = mutableMapOf<String, String>()
            val matches = route.segments.zip(parts).all { (template, actual) ->
                if (template.startsWith("{") && template.endsWith("}")) {
                    params[template.substring(1, template.length - 1)] = actual
                    actual.isNotEmpty()
                } else {
                    template == actual
                }
            }
            if (matches) return route.handler to HttpCall(exchange, params)
        }
        return null
    }
}
