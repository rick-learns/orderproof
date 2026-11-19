package dev.orderproof.verifier

import dev.orderproof.verifier.device.InfrastructureFailure
import dev.orderproof.verifier.judge.Verdict
import dev.orderproof.verifier.report.FileEvidenceReader
import dev.orderproof.verifier.report.HtmlReport
import dev.orderproof.verifier.report.VerificationReport
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.system.exitProcess

/**
 * Exit codes: 0 gate passed (or, with --expect-failure, failed for exactly that reason);
 * 1 gate verdict did not meet expectations; 3 environment could not be prepared; 64 bad usage.
 */
fun main(args: Array<String>) {
    val options = try {
        VerifyOptions.parse(args)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        System.err.println(VerifyOptions.USAGE)
        exitProcess(64)
    }
    val report = try {
        Verification(options) { println("[verifier] $it") }.run()
    } catch (e: InfrastructureFailure) {
        System.err.println("[verifier] INFRASTRUCTURE: ${e.message}")
        exitProcess(3)
    }
    Files.createDirectories(options.out)
    val json = Json { prettyPrint = true; encodeDefaults = true }
    Files.writeString(options.out.resolve("report.json"), json.encodeToString(VerificationReport.serializer(), report))
    Files.writeString(options.out.resolve("report.html"), HtmlReport.render(report, FileEvidenceReader(options.out)))

    val gate = report.gate
    println("[verifier] gate ${gate.verdict} ${gate.failureCodes.ifEmpty { listOf("—") }} · ${gate.cleanRuns}/${gate.runs} clean run(s)")
    gate.reasons.forEach { println("[verifier]   $it") }
    gate.flaky.forEach { println("[verifier]   flaky: $it") }
    gate.expectation?.let { println("[verifier] expectation ${if (it.met) "MET" else "NOT MET"}: ${it.explanation}") }
    println("[verifier] report: ${options.out.resolve("report.html")}")
    val ok = gate.expectation?.met ?: (gate.verdict == Verdict.PASS)
    exitProcess(if (ok) 0 else 1)
}
