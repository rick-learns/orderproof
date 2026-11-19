package dev.orderproof.verifier.judge

import dev.orderproof.oracle.OracleVerdict
import dev.orderproof.oracle.ViolationCode
import dev.orderproof.verifier.run.E2eRecords
import dev.orderproof.verifier.run.InstrumentationResult
import dev.orderproof.verifier.run.TestStatus

/** Everything known about one execution before it is classified. */
data class Observations(
    val expectedCandidate: String,
    val reportedCandidates: List<String>,
    val instrumentation: InstrumentationResult,
    val records: E2eRecords,
    val oracle: OracleVerdict?,
    val missingEvidence: List<String>,
)

data class Classification(val outcome: Outcome, val reasons: List<String>)

/**
 * Rules, in order. The persisted ledger outranks the UI: a duplicate charge is a product failure
 * even when every screen looked right. A missing order alone is only trusted as a product signal
 * when the UI claimed success; if the run broke first, it is a consequence, not a cause.
 */
object Classifier {
    fun classify(o: Observations): Classification {
        val violations = o.oracle?.violations.orEmpty()
        val hard = violations.filter { it.code != ViolationCode.MISSING_ORDER }
        val uiFailures = o.records.assertions.filterNot { it.passed }
        val uiPassedNote = "customer-visible UI passed ${o.records.assertions.count { it.passed }} assertion(s)"

        val foreign = o.reportedCandidates - o.expectedCandidate
        if (foreign.isNotEmpty()) {
            val what = if (o.expectedCandidate in o.reportedCandidates) "another candidate ($foreign) was also running and may have touched the ledger"
            else "wrong build under test: the app reports $foreign, expected ${o.expectedCandidate}"
            return Classification(Outcome.INFRASTRUCTURE_FAILURE, listOf(what))
        }
        if (hard.isNotEmpty()) {
            val note = if (uiFailures.isEmpty()) listOf(uiPassedNote) else emptyList()
            return Classification(Outcome.PRODUCT_FAILURE, hard.map { "${it.code}: ${it.message}" } + note)
        }
        infrastructureReason(o)?.let { return Classification(Outcome.INFRASTRUCTURE_FAILURE, listOf(it)) }
        if (uiFailures.isNotEmpty()) {
            return Classification(Outcome.PRODUCT_FAILURE, uiFailures.map { "UI_ASSERTION: ${it.name}: ${it.detail}" } + violations.map { "${it.code}: ${it.message}" })
        }
        if (violations.isNotEmpty() && o.instrumentation.status == TestStatus.PASSED) {
            return Classification(Outcome.PRODUCT_FAILURE, violations.map { "${it.code}: ${it.message} (the UI reported success)" })
        }
        if (o.instrumentation.status != TestStatus.PASSED) {
            val why = o.records.endError ?: o.instrumentation.failure?.lineSequence()?.firstOrNull() ?: o.instrumentation.status.name
            return Classification(Outcome.INCONCLUSIVE, listOf("scenario did not complete: $why") + violations.map { "${it.code} (consequence): ${it.message}" })
        }
        if (o.missingEvidence.isNotEmpty()) {
            return Classification(Outcome.INCONCLUSIVE, o.missingEvidence.map { "missing evidence: $it" })
        }
        return Classification(Outcome.PASS, listOf("all ${o.records.assertions.size} UI assertions and ${o.oracle?.ordersSeen ?: 0} order(s) in the ledger match the contract"))
    }

    private fun infrastructureReason(o: Observations): String? = when {
        o.oracle == null -> "ledger could not be read"
        o.instrumentation.status == TestStatus.RUNNER_CRASHED -> "test process died: ${o.instrumentation.runnerMessage}"
        o.instrumentation.status == TestStatus.NOT_RUN -> "test never started (no instrumentation status)"
        !o.records.started -> "scenario never started (environment reset failed or runner broke)"
        o.records.endKind == "infrastructure" -> o.records.endError ?: "control channel failure"
        else -> null
    }
}
