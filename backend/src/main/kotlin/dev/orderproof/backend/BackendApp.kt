package dev.orderproof.backend

import com.sun.net.httpserver.HttpServer
import dev.orderproof.backend.config.BackendConfig
import dev.orderproof.backend.config.ClockMode
import dev.orderproof.backend.control.ControlApi
import dev.orderproof.backend.control.LedgerReader
import dev.orderproof.backend.domain.CheckoutService
import dev.orderproof.backend.domain.PricingEngine
import dev.orderproof.backend.faults.FaultInjector
import dev.orderproof.backend.faults.HoldRegistry
import dev.orderproof.backend.http.HttpServers
import dev.orderproof.backend.http.PublicApi
import dev.orderproof.backend.seed.SeedCatalog
import dev.orderproof.backend.seed.Seeder
import dev.orderproof.backend.storage.Database
import dev.orderproof.backend.storage.EventContext
import dev.orderproof.backend.storage.EventLog
import dev.orderproof.backend.time.BackendClock
import dev.orderproof.backend.time.FixedClock
import dev.orderproof.backend.time.SystemClock
import java.time.Instant

/** Composition root. Tests build it with port 0 and read the bound ports back. */
class BackendApp(private val config: BackendConfig, private val controlToken: String?) : AutoCloseable {
    val clock: BackendClock = when (config.clockMode) {
        ClockMode.SYSTEM -> SystemClock()
        ClockMode.FIXED -> FixedClock(FixedClock.SEED_EPOCH_MILLIS)
    }
    val db = Database(config.databaseFile)
    private val events = EventLog(clock::nowMillis)
    private val faults = FaultInjector()
    private val holds = HoldRegistry()
    val checkout = CheckoutService(db, clock, PricingEngine(SeedCatalog.TAX_BASIS_POINTS.toLong()), events)

    private lateinit var publicServer: HttpServer
    private var controlServer: HttpServer? = null

    val publicPort: Int get() = publicServer.address.port
    val controlPort: Int? get() = controlServer?.address?.port

    fun start(): BackendApp {
        db.transaction { conn ->
            if (config.resetOnStart || Seeder.isEmpty(conn)) {
                Seeder.reset(conn, clock.nowMillis())
                events.append(conn, "RESET", detail = "seeded on start")
            }
        }
        val publicApi = PublicApi(checkout, faults, holds, ::journal)
        publicServer = HttpServers.start(
            config.publicHost, config.publicPort, publicApi.router, publicApi.interceptor, ::logPublic,
        )
        if (controlToken != null) {
            val control = ControlApi(controlToken, db, clock, checkout, faults, holds, events, LedgerReader(db, clock::nowMillis))
            controlServer = HttpServers.start(BackendConfig.CONTROL_HOST, config.controlPort, control.router, control.interceptor) { ex, outcome ->
                println("${Instant.now()} control ${ex.requestMethod} ${ex.requestURI.path} -> $outcome")
            }
        }
        return this
    }

    private fun journal(kind: String, context: EventContext, orderId: String?, detail: String) =
        db.transaction { events.append(it, kind, context, orderId, detail) }

    private fun logPublic(exchange: com.sun.net.httpserver.HttpExchange, outcome: String) {
        val context = PublicApi.contextOf(exchange)
        val line = "${exchange.requestMethod} ${exchange.requestURI.path} -> $outcome"
        println("${Instant.now()} public $line request=${context.requestId} attempt=${context.checkoutAttemptId} key=${context.idempotencyKey}")
        journal("HTTP_EXCHANGE", context, null, line)
    }

    override fun close() {
        controlServer?.stop(0)
        if (::publicServer.isInitialized) publicServer.stop(0)
        db.close()
    }
}
