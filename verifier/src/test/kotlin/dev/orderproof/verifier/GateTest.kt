package dev.orderproof.verifier

import com.google.common.truth.Truth.assertThat
import dev.orderproof.oracle.ViolationCode
import dev.orderproof.verifier.judge.Gate
import dev.orderproof.verifier.judge.Outcome
import dev.orderproof.verifier.judge.Verdict
import org.junit.Test

class GateTest {
    @Test
    fun threeCleanRunsPass() {
        val runs = (1..3).flatMap { run -> listOf(execution("a", run, Outcome.PASS), execution("b", run, Outcome.PASS)) }
        val gate = Gate.decide(runs, runs = 3, requiredCleanRuns = 3, expectFailure = null)
        assertThat(gate.verdict).isEqualTo(Verdict.PASS)
        assertThat(gate.cleanRuns).isEqualTo(3)
    }

    @Test
    fun tooFewRunsFail() {
        val gate = Gate.decide(listOf(execution("a", 1, Outcome.PASS)), runs = 1, requiredCleanRuns = 3, expectFailure = null)
        assertThat(gate.verdict).isEqualTo(Verdict.FAIL)
        assertThat(gate.failureCodes).containsExactly("INSUFFICIENT_RUNS")
    }

    @Test
    fun aPassingRerunMarksFlakinessButDoesNotPass() {
        val executions = listOf(
            execution("a", 1, Outcome.INCONCLUSIVE),
            execution("a", 1, Outcome.PASS, attempt = 2),
        )
        val gate = Gate.decide(executions, runs = 1, requiredCleanRuns = 1, expectFailure = null)
        assertThat(gate.verdict).isEqualTo(Verdict.FAIL)
        assertThat(gate.flaky.single()).startsWith("a: failed, then passed on rerun")
        assertThat(gate.firstFailure!!.evidence).isEqualTo("runs/run-01/a/attempt-1")
    }

    @Test
    fun theFirstFailureIsTheEarliestCountedOne() {
        val executions = listOf(
            execution("a", 1, Outcome.PASS),
            execution("b", 1, Outcome.PRODUCT_FAILURE, codes = listOf(ViolationCode.DUPLICATE_ORDER)),
            execution("a", 2, Outcome.INCONCLUSIVE),
        )
        val gate = Gate.decide(executions, runs = 2, requiredCleanRuns = 2, expectFailure = null)
        assertThat(gate.firstFailure!!.scenario).isEqualTo("b")
        assertThat(gate.flaky.single()).startsWith("a: outcomes differ across runs")
    }

    @Test
    fun theFaultyExpectationNeedsExactlyTheDuplicateReason() {
        val duplicate = listOf(execution("lost", 1, Outcome.PRODUCT_FAILURE, codes = listOf(ViolationCode.DUPLICATE_ORDER)), execution("ok", 1, Outcome.PASS))
        assertThat(Gate.decide(duplicate, 1, 1, "DUPLICATE_ORDER").expectation!!.met).isTrue()

        val alsoInconclusive = duplicate + execution("other", 1, Outcome.INCONCLUSIVE)
        assertThat(Gate.decide(alsoInconclusive, 1, 1, "DUPLICATE_ORDER").expectation!!.met).isFalse()

        val passing = listOf(execution("lost", 1, Outcome.PASS))
        assertThat(Gate.decide(passing, 1, 1, "DUPLICATE_ORDER").expectation!!.met).isFalse()
    }

    @Test
    fun missingEvidenceFailsTheGate() {
        val gate = Gate.decide(listOf(execution("a", 1, Outcome.PASS, missing = listOf("recording"))), 1, 1, null)
        assertThat(gate.verdict).isEqualTo(Verdict.FAIL)
        assertThat(gate.failureCodes).contains("MISSING_EVIDENCE")
    }

    @Test
    fun aBuildIdentityMismatchFailsTheGate() {
        val gate = Gate.decide(listOf(execution("a", 1, Outcome.PASS)), 1, 1, null, identityProblems = listOf("BUILD_IDENTITY_MISMATCH: x"))
        assertThat(gate.verdict).isEqualTo(Verdict.FAIL)
        assertThat(gate.failureCodes).containsExactly("BUILD_IDENTITY_MISMATCH")
    }
}
