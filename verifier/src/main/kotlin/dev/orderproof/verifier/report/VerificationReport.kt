package dev.orderproof.verifier.report

import dev.orderproof.verifier.device.DeviceProfile
import dev.orderproof.verifier.device.PreparationLog
import dev.orderproof.verifier.judge.GateResult
import dev.orderproof.verifier.judge.ScenarioExecution
import kotlinx.serialization.Serializable

@Serializable
data class BuildIdentity(
    val candidate: String,
    val appPackage: String,
    val versionName: String,
    val sourceCommit: String,
    val sourceDirty: Boolean,
    /** Commits the running app reported in its `app.start` log line; must match [sourceCommit]. */
    val reportedCommits: List<String>,
    val appApk: String,
    val appApkSha256: String,
    val testApk: String,
    val testApkSha256: String,
)

/** Wall-clock timings. Diagnostics only: no verdict depends on them. */
@Serializable
data class Timings(val preparationMillis: Long, val totalMillis: Long, val perScenarioMillis: Map<String, List<Long>>)

@Serializable
data class VerificationReport(
    val schemaVersion: Int = 1,
    val generatedAt: String,
    val configLabel: String,
    val build: BuildIdentity,
    val device: DeviceProfile,
    val preparation: PreparationLog,
    val gate: GateResult,
    val executions: List<ScenarioExecution>,
    val reproduction: List<String>,
    val timings: Timings,
    val notes: List<String>,
)
