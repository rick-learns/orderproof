package dev.orderproof.verifier

import dev.orderproof.oracle.OracleVerdict
import dev.orderproof.oracle.Violation
import dev.orderproof.oracle.ViolationCode
import dev.orderproof.verifier.judge.EvidenceIndex
import dev.orderproof.verifier.judge.Observations
import dev.orderproof.verifier.judge.Outcome
import dev.orderproof.verifier.judge.ScenarioExecution
import dev.orderproof.verifier.run.E2eRecords
import dev.orderproof.verifier.run.InstrumentationResult
import dev.orderproof.verifier.run.TestStatus
import dev.orderproof.verifier.run.UiAssertion

fun logcatLine(json: String) = "11-19 10:57:16.542  5909  5922 I OrderProofE2E: $json"

fun records(vararg json: String): E2eRecords = E2eRecords.fromLogcat(json.joinToString("\n") { logcatLine(it) })

val startedAndPassedUi = records(
    """{"type":"scenario.start","scenario":"s"}""",
    """{"type":"assertion","scenario":"s","name":"total shown","passed":true,"detail":"Total $14.02"}""",
    """{"type":"scenario.end","scenario":"s","result":"passed"}""",
)

fun verdict(vararg codes: ViolationCode) = OracleVerdict(codes.map { Violation(it, "$it happened") }, ordersSeen = 1, paymentsSeen = 1)

fun observations(
    status: TestStatus = TestStatus.PASSED,
    records: E2eRecords = startedAndPassedUi,
    oracle: OracleVerdict? = verdict(),
    missing: List<String> = emptyList(),
    reported: List<String> = listOf("corrected"),
) = Observations("corrected", reported, InstrumentationResult(status, null, if (status == TestStatus.RUNNER_CRASHED) "Process crashed." else null), records, oracle, missing)

fun execution(
    scenario: String,
    run: Int,
    outcome: Outcome,
    attempt: Int = 1,
    codes: List<ViolationCode> = emptyList(),
    missing: List<String> = emptyList(),
    uiFailed: Boolean = false,
) = ScenarioExecution(
    scenario, scenario, run, attempt, outcome, listOf("because"), TestStatus.PASSED, null,
    listOf(UiAssertion("a", !uiFailed, "d")), emptyList(), verdict(*codes.toTypedArray()), missing,
    EvidenceIndex("runs/run-%02d/$scenario/attempt-$attempt".format(run)), listOf("corrected"), "abc",
    "2026-11-19T10:00:0${run}Z", 1000,
)
