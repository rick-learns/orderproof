package dev.orderproof.verifier

import com.google.common.truth.Truth.assertThat
import dev.orderproof.oracle.ViolationCode
import dev.orderproof.verifier.judge.Classifier
import dev.orderproof.verifier.judge.Outcome
import dev.orderproof.verifier.run.TestStatus
import org.junit.Test

class ClassifierTest {
    @Test
    fun cleanUiAndLedgerPass() {
        assertThat(Classifier.classify(observations()).outcome).isEqualTo(Outcome.PASS)
    }

    @Test
    fun aDuplicateBehindANormalLookingUiIsAProductFailure() {
        val result = Classifier.classify(observations(oracle = verdict(ViolationCode.DUPLICATE_ORDER)))
        assertThat(result.outcome).isEqualTo(Outcome.PRODUCT_FAILURE)
        assertThat(result.reasons.first()).startsWith("DUPLICATE_ORDER")
        assertThat(result.reasons).contains("customer-visible UI passed 1 assertion(s)")
    }

    @Test
    fun aDuplicateOutranksABrokenRunner() {
        val result = Classifier.classify(observations(status = TestStatus.RUNNER_CRASHED, oracle = verdict(ViolationCode.DUPLICATE_ORDER)))
        assertThat(result.outcome).isEqualTo(Outcome.PRODUCT_FAILURE)
    }

    @Test
    fun aCrashedRunnerIsInfrastructure() {
        assertThat(Classifier.classify(observations(status = TestStatus.RUNNER_CRASHED)).outcome).isEqualTo(Outcome.INFRASTRUCTURE_FAILURE)
    }

    @Test
    fun anUnreadableLedgerIsInfrastructure() {
        assertThat(Classifier.classify(observations(oracle = null)).outcome).isEqualTo(Outcome.INFRASTRUCTURE_FAILURE)
    }

    @Test
    fun theWrongBuildIsInfrastructureEvenWithViolations() {
        val result = Classifier.classify(observations(reported = listOf("faulty"), oracle = verdict(ViolationCode.DUPLICATE_ORDER)))
        assertThat(result.outcome).isEqualTo(Outcome.INFRASTRUCTURE_FAILURE)
        assertThat(result.reasons.single()).contains("wrong build")
    }

    @Test
    fun anotherCandidateRunningAlongsideInvalidatesTheRun() {
        val result = Classifier.classify(observations(reported = listOf("corrected", "faulty")))
        assertThat(result.outcome).isEqualTo(Outcome.INFRASTRUCTURE_FAILURE)
        assertThat(result.reasons.single()).contains("another candidate")
    }

    @Test
    fun aFailedUiAssertionIsAProductFailure() {
        val ui = records(
            """{"type":"scenario.start","scenario":"s"}""",
            """{"type":"assertion","scenario":"s","name":"total shown","passed":false,"detail":"Total $14.03"}""",
            """{"type":"scenario.end","scenario":"s","result":"failed","kind":"assertion","error":"AssertionError: UI assertion"}""",
        )
        val result = Classifier.classify(observations(status = TestStatus.FAILED, records = ui))
        assertThat(result.outcome).isEqualTo(Outcome.PRODUCT_FAILURE)
        assertThat(result.reasons.first()).startsWith("UI_ASSERTION: total shown")
    }

    @Test
    fun aTimeoutWithAMissingOrderIsInconclusive() {
        val timedOut = records(
            """{"type":"scenario.start","scenario":"s"}""",
            """{"type":"scenario.end","scenario":"s","result":"failed","kind":"assertion","error":"AssertionError: Timed out after 20000ms"}""",
        )
        val result = Classifier.classify(observations(status = TestStatus.FAILED, records = timedOut, oracle = verdict(ViolationCode.MISSING_ORDER)))
        assertThat(result.outcome).isEqualTo(Outcome.INCONCLUSIVE)
    }

    @Test
    fun aConfirmedUiWithNoPersistedOrderIsAProductFailure() {
        assertThat(Classifier.classify(observations(oracle = verdict(ViolationCode.MISSING_ORDER))).outcome).isEqualTo(Outcome.PRODUCT_FAILURE)
    }

    @Test
    fun missingEvidenceIsNeverAPass() {
        assertThat(Classifier.classify(observations(missing = listOf("screenshots"))).outcome).isEqualTo(Outcome.INCONCLUSIVE)
    }

    @Test
    fun infrastructureMarkedByTheHarnessIsInfrastructure() {
        val infra = records(
            """{"type":"scenario.start","scenario":"s"}""",
            """{"type":"scenario.end","scenario":"s","result":"failed","kind":"infrastructure","error":"INFRASTRUCTURE: control POST /control/reset -> HTTP 401"}""",
        )
        assertThat(Classifier.classify(observations(status = TestStatus.FAILED, records = infra)).outcome).isEqualTo(Outcome.INFRASTRUCTURE_FAILURE)
    }
}
