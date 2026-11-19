package dev.orderproof.verifier.run

import dev.orderproof.oracle.LedgerOracle
import dev.orderproof.oracle.OracleVerdict
import dev.orderproof.oracle.SqliteLedgerSource
import dev.orderproof.verifier.device.Adb
import dev.orderproof.verifier.device.DevicePreparation
import dev.orderproof.verifier.evidence.ControlReader
import dev.orderproof.verifier.evidence.EvidenceDir
import dev.orderproof.verifier.evidence.ScreenRecorder
import dev.orderproof.verifier.judge.Classifier
import dev.orderproof.verifier.judge.EvidenceIndex
import dev.orderproof.verifier.judge.Observations
import dev.orderproof.verifier.judge.ScenarioExecution
import dev.orderproof.verifier.scenario.Scenario
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.time.Instant

/** Where and against what one scenario runs. */
data class RunTarget(
    val candidate: String,
    val appPackage: String,
    val backendUrl: String,
    val controlUrl: String,
    val controlToken: String,
    val databaseFile: Path,
)

/** Runs one scenario once and captures its complete evidence bundle. */
class ScenarioRunner(private val adb: Adb, private val target: RunTarget, private val control: ControlReader) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun execute(scenario: Scenario, run: Int, attempt: Int, dir: EvidenceDir): ScenarioExecution {
        val startedAt = Instant.now()
        adb.run("logcat", "-c")
        val recorder = ScreenRecorder(adb).takeIf { scenario.central }?.also { it.start() }
        val raw = adb.run("shell", instrumentCommand(scenario), timeoutSeconds = 600)
        val recorded = recorder?.stopAndPull(dir.file("recording.mp4")) ?: false
        val durationMillis = Instant.now().toEpochMilli() - startedAt.toEpochMilli()

        val logcat = adb.run("logcat", "-d", "-v", "threadtime", "-s", "OrderProof:V", "OrderProofE2E:V", "TestRunner:V", "AndroidRuntime:E").output
        val appLog = logcat.lineSequence().filter { " OrderProof: " in it }.joinToString("\n")
        val records = E2eRecords.fromLogcat(logcat)
        val instrumentation = InstrumentationResult.parse(raw.output)
        val missing = mutableListOf<String>()

        val index = EvidenceIndex(
            directory = dir.relative(""),
            instrumentation = dir.write("instrumentation.txt", raw.output),
            logcat = dir.write("logcat.txt", logcat),
            appLog = dir.write("app.log", appLog),
            backendEvents = captureEvents(dir, missing),
            screenshots = pullScreenshots(records, dir, missing),
            attachments = records.attachments.mapNotNull { remote ->
                val name = remote.substringAfterLast('/')
                if (adb.pull(remote, dir.file(name))) dir.relative(name) else null
            },
            recording = if (recorded) dir.relative("recording.mp4") else null,
        )
        val oracle = judgeLedger(scenario, dir, missing)

        if (raw.output.isBlank()) missing += "instrumentation output"
        if (!records.started) missing += "scenario start marker"
        if (appLog.isBlank()) missing += "app logs"
        if (scenario.central && !recorded) missing += "screen recording of the central scenario"

        val reportedCandidates = Regex("""candidate=(\w+)""").findAll(appLog).map { it.groupValues[1] }.distinct().toList()
        val classification = Classifier.classify(Observations(target.candidate, reportedCandidates, instrumentation, records, oracle, missing))
        return ScenarioExecution(
            scenario = scenario.id,
            title = scenario.title,
            run = run,
            attempt = attempt,
            outcome = classification.outcome,
            reasons = classification.reasons,
            testStatus = instrumentation.status,
            testFailure = instrumentation.failure?.let(dir::redact),
            uiAssertions = records.assertions,
            steps = records.steps,
            oracle = oracle,
            missingEvidence = missing,
            evidence = index.copy(
                ledger = dir.relative("ledger.json").takeIf { oracle != null },
                oracle = dir.relative("oracle.json").takeIf { oracle != null },
            ),
            reportedCandidates = reportedCandidates,
            reportedCommit = Regex("""app\.start .*commit=(\S+)""").find(appLog)?.groupValues?.get(1),
            startedAt = startedAt.toString(),
            durationMillis = durationMillis,
        )
    }

    private fun instrumentCommand(scenario: Scenario) = listOf(
        "am instrument -w -r",
        "-e targetPackage ${target.appPackage}",
        "-e backendUrl ${target.backendUrl}",
        "-e controlUrl ${target.controlUrl}",
        "-e controlToken ${target.controlToken}",
        "-e class ${scenario.testName}",
        "${DevicePreparation.TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner",
    ).joinToString(" ")

    private fun captureEvents(dir: EvidenceDir, missing: MutableList<String>): String? = runCatching {
        val events = control.events()
        if (events.isEmpty()) missing += "backend events"
        dir.write("backend-events.json", json.encodeToString(ListSerializer(dev.orderproof.control.BackendEvent.serializer()), events))
    }.getOrElse {
        missing += "backend events (${it.message})"
        null
    }

    private fun pullScreenshots(records: E2eRecords, dir: EvidenceDir, missing: MutableList<String>): List<String> {
        val pulled = records.screenshots.mapNotNull { (_, remote) ->
            val name = remote.substringAfterLast('/')
            if (adb.pull(remote, dir.file(name)) && dir.exists(name)) dir.relative(name) else null
        }
        if (pulled.isEmpty()) missing += "screenshots"
        else if (pulled.size < records.screenshots.size) missing += "${records.screenshots.size - pulled.size} screenshot(s)"
        return pulled
    }

    /** The oracle reads the backend's SQLite file itself; nothing here trusts the backend's answers. */
    private fun judgeLedger(scenario: Scenario, dir: EvidenceDir, missing: MutableList<String>): OracleVerdict? = runCatching {
        val ledger = SqliteLedgerSource.read(target.databaseFile)
        dir.write("ledger.json", json.encodeToString(dev.orderproof.oracle.Ledger.serializer(), ledger))
        LedgerOracle.judge(ledger, scenario.expectation).also {
            dir.write("oracle.json", json.encodeToString(OracleVerdict.serializer(), it))
        }
    }.getOrElse {
        missing += "ledger (${it.message})"
        null
    }
}
