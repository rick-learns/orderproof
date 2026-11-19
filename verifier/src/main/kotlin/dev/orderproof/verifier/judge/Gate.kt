package dev.orderproof.verifier.judge

import kotlinx.serialization.Serializable

@Serializable
enum class Verdict { PASS, FAIL }

@Serializable
data class FailureRef(val scenario: String, val run: Int, val outcome: Outcome, val reasons: List<String>, val evidence: String)

@Serializable
data class ExpectationCheck(val expectedFailureCode: String, val met: Boolean, val explanation: String)

@Serializable
data class GateResult(
    val verdict: Verdict,
    val reasons: List<String>,
    /** Why it failed, as stable codes: oracle violation codes, UI_ASSERTION, INFRASTRUCTURE, INCONCLUSIVE, MISSING_EVIDENCE, INSUFFICIENT_RUNS, BUILD_IDENTITY_MISMATCH. */
    val failureCodes: List<String>,
    val runs: Int,
    val cleanRuns: Int,
    val requiredCleanRuns: Int,
    /** The earliest counted failure, preserved as found; reruns never replace it. */
    val firstFailure: FailureRef?,
    val flaky: List<String>,
    val expectation: ExpectationCheck?,
)

/**
 * The release gate. It passes only if every counted execution in every run passed with
 * complete evidence and there were enough runs. Reruns of a failure are diagnostics: they can
 * reveal flakiness but never turn a failure into a pass.
 */
object Gate {
    fun decide(
        executions: List<ScenarioExecution>,
        runs: Int,
        requiredCleanRuns: Int,
        expectFailure: String?,
        identityProblems: List<String> = emptyList(),
    ): GateResult {
        val counted = executions.filter { it.attempt == 1 }
        val failures = counted.filter { it.outcome != Outcome.PASS }
        val cleanRuns = (1..runs).count { run -> counted.filter { it.run == run }.let { r -> r.isNotEmpty() && r.all { it.outcome == Outcome.PASS } } }

        val codes = linkedSetOf<String>()
        failures.forEach { codes += codesOf(it) }
        if (counted.any { it.missingEvidence.isNotEmpty() }) codes += "MISSING_EVIDENCE"
        if (cleanRuns < requiredCleanRuns && failures.isEmpty()) codes += "INSUFFICIENT_RUNS"
        if (identityProblems.isNotEmpty()) codes += "BUILD_IDENTITY_MISMATCH"

        val reasons = failures.map { "${it.scenario} (run ${it.run}) ${it.outcome}: ${it.reasons.joinToString("; ")}" } +
            counted.filter { it.missingEvidence.isNotEmpty() && it.outcome == Outcome.PASS }.map { "${it.scenario} (run ${it.run}) missing evidence: ${it.missingEvidence}" } +
            (if ("INSUFFICIENT_RUNS" in codes) listOf("only $cleanRuns clean run(s); $requiredCleanRuns required") else emptyList()) +
            identityProblems

        val verdict = if (codes.isEmpty() && cleanRuns >= requiredCleanRuns) Verdict.PASS else Verdict.FAIL
        val first = failures.minWithOrNull(compareBy({ it.run }, { it.startedAt }))
        return GateResult(
            verdict = verdict,
            reasons = if (verdict == Verdict.PASS) listOf("$cleanRuns/$runs runs clean; every scenario passed its UI assertions and ledger oracle") else reasons,
            failureCodes = codes.toList(),
            runs = runs,
            cleanRuns = cleanRuns,
            requiredCleanRuns = requiredCleanRuns,
            firstFailure = first?.let { FailureRef(it.scenario, it.run, it.outcome, it.reasons, it.evidence.directory) },
            flaky = flaky(executions),
            expectation = expectFailure?.let { check(verdict, codes, it) },
        )
    }

    private fun codesOf(e: ScenarioExecution): List<String> = when (e.outcome) {
        Outcome.PASS -> emptyList()
        Outcome.INFRASTRUCTURE_FAILURE -> listOf("INFRASTRUCTURE")
        Outcome.INCONCLUSIVE -> listOf("INCONCLUSIVE")
        Outcome.PRODUCT_FAILURE -> e.oracle?.violations.orEmpty().map { it.code.name }.ifEmpty { listOf("UI_ASSERTION") } +
            (if (e.uiAssertions.any { !it.passed }) listOf("UI_ASSERTION") else emptyList())
    }.distinct()

    /** Flaky: a counted failure whose rerun passed, or a scenario whose counted outcomes differ across runs. */
    private fun flaky(executions: List<ScenarioExecution>): List<String> = executions.groupBy { it.scenario }.mapNotNull { (scenario, list) ->
        val counted = list.filter { it.attempt == 1 }
        val rerunPassed = list.any { it.attempt > 1 && it.outcome == Outcome.PASS }
        val mixed = counted.map { it.outcome }.distinct().size > 1
        when {
            rerunPassed -> "$scenario: failed, then passed on rerun (${list.joinToString { "run ${it.run}#${it.attempt}=${it.outcome}" }})"
            mixed -> "$scenario: outcomes differ across runs (${counted.joinToString { "run ${it.run}=${it.outcome}" }})"
            else -> null
        }
    }

    private fun check(verdict: Verdict, codes: Set<String>, expected: String): ExpectationCheck {
        val met = verdict == Verdict.FAIL && codes == setOf(expected)
        val explanation = when {
            verdict == Verdict.PASS -> "expected the gate to fail with $expected, but it passed"
            met -> "gate failed for exactly the expected reason: $expected"
            else -> "gate failed, but for $codes rather than only $expected"
        }
        return ExpectationCheck(expected, met, explanation)
    }
}
