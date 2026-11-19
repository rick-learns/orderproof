package dev.orderproof.emberkitchen.domain.checkout

import dev.orderproof.contract.ErrorCode
import dev.orderproof.contract.OrderDto
import dev.orderproof.contract.OrderLineRequest
import dev.orderproof.emberkitchen.domain.ActiveOrderStore
import dev.orderproof.emberkitchen.domain.ApiOutcome
import dev.orderproof.emberkitchen.domain.AttemptStore
import dev.orderproof.emberkitchen.domain.CartStore
import dev.orderproof.emberkitchen.domain.CheckoutGateway
import dev.orderproof.emberkitchen.domain.OrderSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-scoped owner of checkout submission. It outlives screens, refuses overlapping
 * submissions synchronously (so a burst of taps sends once), and persists the attempt before
 * any network I/O so a retry or a relaunched process can refer to the same operation.
 */
class CheckoutCoordinator(
    private val gateway: CheckoutGateway,
    private val attempts: AttemptStore,
    private val cart: CartStore,
    private val activeOrder: ActiveOrderStore,
    private val keys: CheckoutKeyPolicy,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow<CheckoutState>(CheckoutState.Idle)
    val state: StateFlow<CheckoutState> = mutableState.asStateFlow()

    private val busy = AtomicBoolean(false)

    /**
     * Returns false when ignored: a submission is already in flight, or the previous one was
     * just confirmed and the UI has not acknowledged it yet (a late tap must not reorder).
     */
    fun placeOrder(lines: List<OrderLineRequest>, couponCode: String?, confirmedTotalMinor: Long): Boolean {
        if (mutableState.value is CheckoutState.Confirmed) {
            log("placeOrder.ignored reason=already_confirmed")
            return false
        }
        return launchExclusive("placeOrder") {
            val existing = attempts.load()
            if (existing != null && !existing.matches(lines, couponCode, confirmedTotalMinor)) {
                // Never abandon an attempt that may have committed: settle it first.
                if (settleIfCommitted(existing)) return@launchExclusive
                attempts.clear()
            }
            val attempt = existing?.takeIf { it.matches(lines, couponCode, confirmedTotalMinor) } ?: CheckoutAttempt(
                attemptId = newId(),
                idempotencyKey = newId(),
                lines = lines,
                couponCode = couponCode,
                expectedTotalMinor = confirmedTotalMinor,
                paymentToken = CheckoutAttempt.SIMULATED_CARD_TOKEN,
                phase = AttemptPhase.PREPARED,
                sendCount = 0,
                createdAtMillis = nowMillis(),
            ).also {
                attempts.save(it)
                log("attempt.created attempt=${it.attemptId} persistedKey=${it.idempotencyKey} total=${it.expectedTotalMinor}")
            }
            send(attempt)
        }
    }

    fun retry(): Boolean = launchExclusive("retry") {
        val attempt = attempts.load()
        if (attempt == null) mutableState.value = CheckoutState.Idle else send(attempt)
    }

    /** Called on launch: resolves an attempt that was in flight when the process died. */
    fun reconcilePending(): Boolean = launchExclusive("reconcile") {
        val attempt = attempts.load() ?: return@launchExclusive
        mutableState.value = CheckoutState.Submitting(attempt, reconciling = true)
        log("reconcile.start attempt=${attempt.attemptId} phase=${attempt.phase} persistedKey=${attempt.idempotencyKey}")
        mutableState.value = when (val outcome = gateway.lookupCheckout(attempt.idempotencyKey)) {
            is ApiOutcome.Success -> return@launchExclusive complete(outcome.value, "reconcile")
            is ApiOutcome.Rejected -> if (outcome.httpStatus == 404) {
                log("reconcile.not_found attempt=${attempt.attemptId}")
                CheckoutState.NeedsRetry(attempt, RetryReason.NOT_PLACED_AFTER_RESTART)
            } else {
                CheckoutState.NeedsRetry(attempt, RetryReason.RECONCILE_FAILED)
            }
            else -> CheckoutState.NeedsRetry(attempt, RetryReason.RECONCILE_FAILED)
        }
    }

    /** Leave a terminal state (confirmed, rejected, total changed) once the UI has handled it. */
    fun acknowledge() {
        if (!busy.get() && mutableState.value !is CheckoutState.NeedsRetry) mutableState.value = CheckoutState.Idle
    }

    private suspend fun send(attempt: CheckoutAttempt) {
        val sending = attempt.copy(phase = AttemptPhase.SENT, sendCount = attempt.sendCount + 1)
        attempts.save(sending)
        mutableState.value = CheckoutState.Submitting(sending)
        val key = keys.keyForSend(sending)
        log("submit.send attempt=${sending.attemptId} send=${sending.sendCount} key=$key persistedKey=${sending.idempotencyKey}")
        when (val outcome = gateway.placeOrder(sending.toRequest(), key, sending.attemptId)) {
            is ApiOutcome.Success -> complete(outcome.value, "submit")
            is ApiOutcome.Rejected -> reject(sending, outcome)
            is ApiOutcome.Unavailable -> keepOpen(sending, RetryReason.SERVICE_UNAVAILABLE, outcome.detail)
            is ApiOutcome.Unknown -> keepOpen(sending, RetryReason.OUTCOME_UNKNOWN, outcome.detail)
        }
    }

    private suspend fun settleIfCommitted(attempt: CheckoutAttempt): Boolean {
        val outcome = gateway.lookupCheckout(attempt.idempotencyKey)
        if (outcome is ApiOutcome.Success) {
            complete(outcome.value, "settle")
            return true
        }
        return false
    }

    private suspend fun complete(order: OrderDto, via: String) {
        activeOrder.save(OrderSummary(order.orderId, order.orderNumber, order.totalMinor))
        cart.clear()
        attempts.clear()
        log("$via.confirmed order=${order.orderId} number=${order.orderNumber} total=${order.totalMinor}")
        mutableState.value = CheckoutState.Confirmed(order)
    }

    private suspend fun reject(attempt: CheckoutAttempt, outcome: ApiOutcome.Rejected) {
        attempts.clear()
        log("submit.rejected attempt=${attempt.attemptId} code=${outcome.error.code}")
        val quote = outcome.error.quote
        mutableState.value = if (outcome.error.code == ErrorCode.TOTAL_CHANGED && quote != null) {
            CheckoutState.TotalChanged(quote, attempt.expectedTotalMinor)
        } else {
            CheckoutState.Rejected(outcome.error.code, outcome.error.message)
        }
    }

    private suspend fun keepOpen(attempt: CheckoutAttempt, reason: RetryReason, detail: String) {
        val open = attempt.copy(phase = AttemptPhase.OUTCOME_UNKNOWN)
        attempts.save(open)
        log("submit.open attempt=${attempt.attemptId} reason=$reason detail=$detail")
        mutableState.value = CheckoutState.NeedsRetry(open, reason)
    }

    private fun launchExclusive(name: String, block: suspend () -> Unit): Boolean {
        if (!busy.compareAndSet(false, true)) {
            log("$name.ignored reason=submission_in_flight")
            return false
        }
        scope.launch {
            try {
                block()
            } finally {
                busy.set(false)
            }
        }
        return true
    }
}
