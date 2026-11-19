package dev.orderproof.verifier.judge

import dev.orderproof.oracle.OracleVerdict
import dev.orderproof.verifier.run.TestStatus
import dev.orderproof.verifier.run.UiAssertion
import kotlinx.serialization.Serializable

@Serializable
enum class Outcome { PASS, PRODUCT_FAILURE, INFRASTRUCTURE_FAILURE, INCONCLUSIVE }

/** Relative paths (from the report folder) of everything captured for one execution. */
@Serializable
data class EvidenceIndex(
    val directory: String,
    val instrumentation: String? = null,
    val logcat: String? = null,
    val appLog: String? = null,
    val backendEvents: String? = null,
    val ledger: String? = null,
    val oracle: String? = null,
    val screenshots: List<String> = emptyList(),
    /** Other files the harness attached, such as the view hierarchy at the moment of failure. */
    val attachments: List<String> = emptyList(),
    val recording: String? = null,
)

@Serializable
data class ScenarioExecution(
    val scenario: String,
    val title: String,
    val run: Int,
    /** 1 for the counted execution; 2+ for diagnostic reruns of a failure. */
    val attempt: Int,
    val outcome: Outcome,
    val reasons: List<String>,
    val testStatus: TestStatus,
    val testFailure: String?,
    val uiAssertions: List<UiAssertion>,
    val steps: List<String>,
    val oracle: OracleVerdict?,
    val missingEvidence: List<String>,
    val evidence: EvidenceIndex,
    /** Every candidate label seen in app logs during the scenario; exactly the one under test is expected. */
    val reportedCandidates: List<String>,
    val reportedCommit: String?,
    val startedAt: String,
    val durationMillis: Long,
)
