@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.orderproof.emberkitchen.domain.checkout

import com.google.common.truth.Truth.assertThat
import dev.orderproof.contract.ApiError
import dev.orderproof.contract.ErrorCode
import dev.orderproof.contract.QuoteResponse
import dev.orderproof.emberkitchen.domain.ApiOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Coordinator behavior with the key policy the contract requires (reuse the persisted key).
 * The candidate flavors' own policies are judged end to end by the release gate, not here.
 */
class CheckoutCoordinatorTest {
    private val attempts = FakeAttemptStore()
    private val cart = FakeCartStore(Samples.cart)
    private val activeOrder = FakeActiveOrderStore()
    private val gateway = FakeGateway(attempts)
    private val logs = mutableListOf<String>()
    private var ids = 0

    private fun TestScope.coordinator() = CheckoutCoordinator(
        gateway = gateway,
        attempts = attempts,
        cart = cart,
        activeOrder = activeOrder,
        keys = CheckoutKeyPolicy { it.idempotencyKey },
        scope = this,
        log = { logs += it },
        newId = { "id-${ids++}" },
        nowMillis = { 0 },
    )

    @Test
    fun attemptIsPersistedWithItsKeyBeforeTheFirstSend() = runTest {
        gateway.placeOutcomes += ApiOutcome.Success(Samples.order())
        coordinator().placeOrder(Samples.lines, null, 1402)
        advanceUntilIdle()

        val send = gateway.sends.single()
        val persisted = checkNotNull(send.persistedAtSend) { "nothing was persisted before the send" }
        assertThat(persisted.idempotencyKey).isEqualTo(send.key)
        assertThat(persisted.phase).isEqualTo(AttemptPhase.SENT)
        assertThat(send.request.expectedTotalMinor).isEqualTo(1402)
    }

    @Test
    fun successClearsAttemptAndCartAndRemembersTheOrder() = runTest {
        gateway.placeOutcomes += ApiOutcome.Success(Samples.order())
        val coordinator = coordinator()
        coordinator.placeOrder(Samples.lines, null, 1402)
        advanceUntilIdle()

        assertThat(coordinator.state.value).isEqualTo(CheckoutState.Confirmed(Samples.order()))
        assertThat(attempts.stored).isNull()
        assertThat(cart.state.value.lines).isEmpty()
        assertThat(activeOrder.state.value!!.orderNumber).isEqualTo("EK-1001")
    }

    @Test
    fun tapsWhileASubmissionIsInFlightAreIgnored() = runTest {
        gateway.gate = CompletableDeferred()
        gateway.placeOutcomes += ApiOutcome.Success(Samples.order())
        val coordinator = coordinator()

        val accepted = (1..5).map { coordinator.placeOrder(Samples.lines, null, 1402) }
        advanceUntilIdle()
        gateway.gate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(accepted).containsExactly(true, false, false, false, false).inOrder()
        assertThat(gateway.sends).hasSize(1)
    }

    @Test
    fun aLateTapAfterConfirmationDoesNotPlaceAnotherOrder() = runTest {
        gateway.placeOutcomes += ApiOutcome.Success(Samples.order())
        val coordinator = coordinator()
        coordinator.placeOrder(Samples.lines, null, 1402)
        advanceUntilIdle()

        assertThat(coordinator.placeOrder(Samples.lines, null, 1402)).isFalse()
        advanceUntilIdle()
        assertThat(gateway.sends).hasSize(1)
    }

    @Test
    fun retryAfterLostResponseResendsTheSameKeyAndAttempt() = runTest {
        gateway.placeOutcomes += ApiOutcome.Unknown("SocketException: Connection reset")
        gateway.placeOutcomes += ApiOutcome.Success(Samples.order())
        val coordinator = coordinator()
        coordinator.placeOrder(Samples.lines, null, 1402)
        advanceUntilIdle()

        val open = coordinator.state.value as CheckoutState.NeedsRetry
        assertThat(open.reason).isEqualTo(RetryReason.OUTCOME_UNKNOWN)
        assertThat(attempts.stored!!.phase).isEqualTo(AttemptPhase.OUTCOME_UNKNOWN)

        coordinator.retry()
        advanceUntilIdle()

        val (first, second) = gateway.sends
        assertThat(second.key).isEqualTo(first.key)
        assertThat(second.attemptId).isEqualTo(first.attemptId)
        assertThat(second.request).isEqualTo(first.request)
        assertThat(second.persistedAtSend!!.sendCount).isEqualTo(2)
        assertThat(coordinator.state.value).isInstanceOf(CheckoutState.Confirmed::class.java)
    }

    @Test
    fun outageKeepsTheAttemptOpenForRetry() = runTest {
        gateway.placeOutcomes += ApiOutcome.Unavailable("HTTP 503")
        val coordinator = coordinator()
        coordinator.placeOrder(Samples.lines, null, 1402)
        advanceUntilIdle()

        assertThat((coordinator.state.value as CheckoutState.NeedsRetry).reason).isEqualTo(RetryReason.SERVICE_UNAVAILABLE)
        assertThat(attempts.stored).isNotNull()
        assertThat(cart.state.value.lines).isNotEmpty()
    }

    @Test
    fun definitiveRejectionClosesTheAttemptButKeepsTheCart() = runTest {
        gateway.placeOutcomes += ApiOutcome.Rejected(409, ApiError(ErrorCode.ITEM_UNAVAILABLE, "Sold out"))
        val coordinator = coordinator()
        coordinator.placeOrder(Samples.lines, null, 1402)
        advanceUntilIdle()

        assertThat((coordinator.state.value as CheckoutState.Rejected).code).isEqualTo(ErrorCode.ITEM_UNAVAILABLE)
        assertThat(attempts.stored).isNull()
        assertThat(cart.state.value.lines).isNotEmpty()
    }

    @Test
    fun totalChangedSurfacesTheFreshQuoteAndThePreviousTotal() = runTest {
        val fresh = QuoteResponse(emptyList(), 1295, 0, 107, 1402)
        gateway.placeOutcomes += ApiOutcome.Rejected(409, ApiError(ErrorCode.TOTAL_CHANGED, "Total changed", quote = fresh))
        val coordinator = coordinator()
        coordinator.placeOrder(Samples.lines, "EMBER10", 1262)
        advanceUntilIdle()

        assertThat(coordinator.state.value).isEqualTo(CheckoutState.TotalChanged(fresh, 1262))
    }

    @Test
    fun relaunchFindsTheCommittedOrderWithoutResending() = runTest {
        attempts.stored = Samples.attempt()
        gateway.lookupOutcomes += ApiOutcome.Success(Samples.order())
        val coordinator = coordinator()
        coordinator.reconcilePending()
        advanceUntilIdle()

        assertThat(gateway.lookups).containsExactly("persisted-key")
        assertThat(gateway.sends).isEmpty()
        assertThat(coordinator.state.value).isInstanceOf(CheckoutState.Confirmed::class.java)
        assertThat(attempts.stored).isNull()
    }

    @Test
    fun relaunchWithNothingCommittedOffersRetryWithTheSameKey() = runTest {
        attempts.stored = Samples.attempt()
        gateway.lookupOutcomes += ApiOutcome.Rejected(404, ApiError(ErrorCode.NOT_FOUND, "No checkout"))
        gateway.placeOutcomes += ApiOutcome.Success(Samples.order())
        val coordinator = coordinator()
        coordinator.reconcilePending()
        advanceUntilIdle()
        assertThat((coordinator.state.value as CheckoutState.NeedsRetry).reason).isEqualTo(RetryReason.NOT_PLACED_AFTER_RESTART)

        coordinator.retry()
        advanceUntilIdle()
        assertThat(gateway.sends.single().key).isEqualTo("persisted-key")
    }

    @Test
    fun anOpenAttemptForADifferentCartIsSettledBeforeANewOneStarts() = runTest {
        attempts.stored = Samples.attempt(total = 9999)
        gateway.lookupOutcomes += ApiOutcome.Success(Samples.order(total = 9999))
        val coordinator = coordinator()
        coordinator.placeOrder(Samples.lines, null, 1402)
        advanceUntilIdle()

        assertThat(gateway.sends).isEmpty()
        assertThat((coordinator.state.value as CheckoutState.Confirmed).order.totalMinor).isEqualTo(9999)
    }

    @Test
    fun anUncommittedStaleAttemptIsReplacedWithAFreshKey() = runTest {
        attempts.stored = Samples.attempt(total = 9999)
        gateway.lookupOutcomes += ApiOutcome.Rejected(404, ApiError(ErrorCode.NOT_FOUND, "No checkout"))
        gateway.placeOutcomes += ApiOutcome.Success(Samples.order())
        coordinator().placeOrder(Samples.lines, null, 1402)
        advanceUntilIdle()

        assertThat(gateway.sends.single().key).isNotEqualTo("persisted-key")
        assertThat(gateway.sends.single().request.expectedTotalMinor).isEqualTo(1402)
    }
}
