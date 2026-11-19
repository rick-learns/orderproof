package dev.orderproof.emberkitchen.data.net

import dev.orderproof.contract.ApiHeaders
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClients {
    /**
     * Transport retries are disabled on purpose: OkHttp may otherwise silently resend a POST
     * after a dropped connection. Every resend must be an explicit, keyed checkout decision.
     */
    fun create(log: (String) -> Unit): OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor(log))
        .build()

    private fun loggingInterceptor(log: (String) -> Unit) = Interceptor { chain ->
        val request = chain.request()
        val ids = "request=${request.header(ApiHeaders.REQUEST_ID)} attempt=${request.header(ApiHeaders.CHECKOUT_ATTEMPT)} " +
            "key=${request.header(ApiHeaders.IDEMPOTENCY_KEY)}"
        log("http.start ${request.method} ${request.url.encodedPath} $ids")
        try {
            chain.proceed(request).also { response ->
                log("http.end ${request.method} ${request.url.encodedPath} status=${response.code} $ids")
            }
        } catch (e: java.io.IOException) {
            log("http.fail ${request.method} ${request.url.encodedPath} error=${e.javaClass.simpleName}:${e.message} $ids")
            throw e
        }
    }
}
