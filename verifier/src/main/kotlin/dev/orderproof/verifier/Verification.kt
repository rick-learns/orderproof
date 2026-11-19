package dev.orderproof.verifier

import dev.orderproof.verifier.device.Adb
import dev.orderproof.verifier.device.DevicePreparation
import dev.orderproof.verifier.device.DeviceProfile
import dev.orderproof.verifier.evidence.ControlReader
import dev.orderproof.verifier.evidence.EvidenceDir
import dev.orderproof.verifier.judge.Gate
import dev.orderproof.verifier.judge.Outcome
import dev.orderproof.verifier.judge.ScenarioExecution
import dev.orderproof.verifier.report.BuildIdentity
import dev.orderproof.verifier.report.Timings
import dev.orderproof.verifier.report.VerificationReport
import dev.orderproof.verifier.run.RunTarget
import dev.orderproof.verifier.run.ScenarioRunner
import dev.orderproof.verifier.scenario.ScenarioCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

/** Prepares the device, runs every scenario `runs` times, and judges the result. */
class Verification(private val options: VerifyOptions, private val progress: (String) -> Unit) {
    fun run(): VerificationReport {
        val started = System.currentTimeMillis()
        val token = Files.readString(options.tokenFile).trim()
        val adb = Adb(options.adb, options.serial)
        progress("preparing ${options.serial}")
        val preparation = DevicePreparation(adb).prepare(options.appApk.toString(), options.testApk.toString(), options.appPackage, options.controlPort)
        val device = DeviceProfile.read(adb)
        progress("device: ${device.model} API ${device.apiLevel} (${device.avdName}, ${device.screenSize} @ ${device.density})")

        val target = RunTarget(options.candidate, options.appPackage, options.backendUrl, options.controlUrl, token, options.databaseFile)
        val runner = ScenarioRunner(adb, target, ControlReader(options.controlUrl, token))
        val scenarios = ScenarioCatalog.select(options.scenarios)
        val executions = mutableListOf<ScenarioExecution>()
        for (run in 1..options.runs) {
            for (scenario in scenarios) {
                var attempt = 1
                do {
                    val dir = EvidenceDir(options.out.resolve("runs/run-%02d/%s/attempt-%d".format(run, scenario.id, attempt)), options.out, listOf(token))
                    val execution = runner.execute(scenario, run, attempt, dir)
                    executions += execution
                    progress("run $run ${scenario.id} attempt $attempt: ${execution.outcome} (${execution.durationMillis} ms)")
                    attempt++
                } while (execution.outcome != Outcome.PASS && attempt <= 1 + options.rerunFailures)
            }
        }

        val reportedCommits = executions.mapNotNull { it.reportedCommit }.distinct()
        val identityProblems = reportedCommits.filterNot { options.sourceCommit.startsWith(it) }
            .map { "BUILD_IDENTITY_MISMATCH: the app reports commit $it but the source under test is ${options.sourceCommit}" }
        val gate = Gate.decide(executions, options.runs, options.requiredCleanRuns, options.expectFailure, identityProblems)
        return VerificationReport(
            generatedAt = Instant.now().toString(),
            configLabel = options.configLabel,
            build = BuildIdentity(
                options.candidate, options.appPackage, versionName(adb), options.sourceCommit, options.sourceDirty, reportedCommits,
                options.appApk.fileName.toString(), sha256(options.appApk), options.testApk.fileName.toString(), sha256(options.testApk),
            ),
            device = device,
            preparation = preparation,
            gate = gate,
            executions = executions,
            reproduction = reproduction(device, executions),
            timings = Timings(preparation.settleMillis, System.currentTimeMillis() - started, executions.groupBy({ it.scenario }, { it.durationMillis })),
            notes = buildList {
                if (options.sourceDirty) add("The working tree had uncommitted changes when this ran; the commit alone does not identify the source.")
                add("Timings are diagnostics only; no verdict depends on them.")
            },
        )
    }

    private fun versionName(adb: Adb): String =
        Regex("""versionName=(\S+)""").find(adb.shell("dumpsys package ${options.appPackage}"))?.groupValues?.get(1) ?: "unknown"

    private fun reproduction(device: DeviceProfile, executions: List<ScenarioExecution>): List<String> {
        val full = "scripts/verify.sh --candidate ${options.candidate} --avd ${device.avdName} --runs ${options.runs}" +
            (options.expectFailure?.let { " --expect-failure $it" } ?: "")
        val failed = executions.filter { it.attempt == 1 && it.outcome != Outcome.PASS }.map { it.scenario }.distinct()
        return listOf(full) + failed.map { id ->
            val scenario = ScenarioCatalog.all.first { it.id == id }
            "scripts/smoke-one.sh ${scenario.testClass}#${scenario.id} ${options.appPackage}"
        }
    }

    private fun sha256(path: Path): String =
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
