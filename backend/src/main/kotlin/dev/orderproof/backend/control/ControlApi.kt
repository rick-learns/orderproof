package dev.orderproof.backend.control

import com.sun.net.httpserver.HttpExchange
import dev.orderproof.backend.domain.CheckoutService
import dev.orderproof.backend.domain.toOrderDto
import dev.orderproof.backend.faults.FaultInjector
import dev.orderproof.backend.faults.HoldRegistry
import dev.orderproof.backend.http.HttpResult
import dev.orderproof.backend.http.Interceptor
import dev.orderproof.backend.http.Router
import dev.orderproof.backend.http.jsonResult
import dev.orderproof.backend.seed.SeedCatalog
import dev.orderproof.backend.seed.Seeder
import dev.orderproof.backend.storage.Database
import dev.orderproof.backend.storage.EventLog
import dev.orderproof.backend.storage.MenuRepository
import dev.orderproof.backend.time.BackendClock
import dev.orderproof.backend.time.FixedClock
import dev.orderproof.contract.ApiError
import dev.orderproof.contract.ErrorCode
import dev.orderproof.control.AdvanceClockRequest
import dev.orderproof.control.AdvanceOrderRequest
import dev.orderproof.control.ArmFaultRequest
import dev.orderproof.control.AwaitHoldRequest
import dev.orderproof.control.ClockState
import dev.orderproof.control.ControlHeaders
import dev.orderproof.control.ControlPaths
import dev.orderproof.control.EventsResponse
import dev.orderproof.control.FaultState
import dev.orderproof.control.ResetResponse
import dev.orderproof.control.SetStockRequest
import java.security.MessageDigest

/**
 * Privileged test-environment API: reset, fault injection, sync points, clock and ledger.
 * Served on a separate loopback-only port and guarded by a per-run bearer token.
 */
class ControlApi(
    private val token: String,
    private val db: Database,
    private val clock: BackendClock,
    private val checkout: CheckoutService,
    private val faults: FaultInjector,
    private val holds: HoldRegistry,
    private val events: EventLog,
    private val ledger: LedgerReader,
) {
    val interceptor: Interceptor = { exchange: HttpExchange ->
        if (authorized(exchange)) null else jsonResult(401, ApiError(ErrorCode.INVALID_REQUEST, "Control token required."))
    }

    val router = Router().apply {
        post(ControlPaths.RESET) { reset() }
        get(ControlPaths.FAULTS) { jsonResult(200, faultState()) }
        post(ControlPaths.FAULTS) { call ->
            val request = call.body<ArmFaultRequest>()
            faults.arm(request.kind, request.times)
            log("FAULT_ARMED", "${request.kind} x${request.times}")
            jsonResult(200, faultState())
        }
        delete(ControlPaths.FAULTS) {
            faults.clear()
            log("FAULTS_CLEARED", "")
            jsonResult(200, faultState())
        }
        post(ControlPaths.AWAIT_HOLD) { call ->
            holds.awaitHeld(call.body<AwaitHoldRequest>().timeoutMillis.coerceIn(0, 60_000))
            jsonResult(200, faultState())
        }
        post("${ControlPaths.HOLDS}/release") {
            val released = holds.releaseAll()
            log("HOLDS_RELEASE_REQUESTED", released.joinToString())
            jsonResult(200, faultState())
        }
        post(ControlPaths.STOCK) { call ->
            val request = call.body<SetStockRequest>()
            val found = db.transaction { MenuRepository.setStock(it, request.itemId, request.stock) }
            if (!found) return@post jsonResult(404, ApiError(ErrorCode.UNKNOWN_ITEM, "Unknown item ${request.itemId}"))
            log("STOCK_SET", "${request.itemId}=${request.stock}")
            jsonResult(200, request)
        }
        get(ControlPaths.CLOCK) { jsonResult(200, ClockState(clock.nowMillis(), clock.isFixed)) }
        post("${ControlPaths.CLOCK}/advance") { call ->
            val fixed = clock as? FixedClock
                ?: return@post jsonResult(409, ApiError(ErrorCode.INVALID_REQUEST, "Clock is not fixed; start with --clock fixed."))
            val by = call.body<AdvanceClockRequest>().byMillis
            if (by < 0) return@post jsonResult(400, ApiError(ErrorCode.INVALID_REQUEST, "Clock only moves forward."))
            fixed.advance(by)
            log("CLOCK_ADVANCED", "by=${by}ms now=${fixed.nowMillis()}")
            jsonResult(200, ClockState(fixed.nowMillis(), true))
        }
        get(ControlPaths.LEDGER) { jsonResult(200, ledger.snapshot()) }
        get(ControlPaths.EVENTS) { call ->
            val after = call.query("after")?.toLongOrNull() ?: 0
            jsonResult(200, EventsResponse(db.transaction { events.after(it, after) }))
        }
        post("${ControlPaths.ORDER_STATUS}/advance") { call ->
            val order = checkout.advanceStatus(call.body<AdvanceOrderRequest>().orderId)
                ?: return@post jsonResult(404, ApiError(ErrorCode.NOT_FOUND, "No such order"))
            jsonResult(200, order.toOrderDto(checkout.paymentStatus(order.orderId)))
        }
    }

    private fun reset(): HttpResult {
        faults.clear()
        holds.reset()
        (clock as? FixedClock)?.set(FixedClock.SEED_EPOCH_MILLIS)
        db.transaction { conn ->
            Seeder.reset(conn, clock.nowMillis())
            events.append(conn, "RESET", detail = "seed catalog restored; clock=${clock.nowMillis()}")
        }
        return jsonResult(200, ResetResponse(clock.nowMillis(), SeedCatalog.menu.size))
    }

    private fun faultState() = FaultState(faults.snapshot(), holds.heldNow(), holds.releasedIds())

    private fun log(kind: String, detail: String) = db.transaction { events.append(it, kind, detail = detail) }

    private fun authorized(exchange: HttpExchange): Boolean {
        val header = exchange.requestHeaders.getFirst(ControlHeaders.AUTHORIZATION) ?: return false
        if (!header.startsWith(ControlHeaders.BEARER_PREFIX)) return false
        val presented = header.removePrefix(ControlHeaders.BEARER_PREFIX).toByteArray()
        return MessageDigest.isEqual(presented, token.toByteArray())
    }
}
