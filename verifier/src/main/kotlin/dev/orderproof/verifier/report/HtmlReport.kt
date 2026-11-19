package dev.orderproof.verifier.report

import dev.orderproof.verifier.judge.Outcome
import dev.orderproof.verifier.report.Html.badge
import dev.orderproof.verifier.report.Html.esc

/** Renders the human-readable report. Self-contained except for evidence files it links. */
object HtmlReport {
    fun render(r: VerificationReport, reader: EvidenceReader): String = buildString {
        val g = r.gate
        append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<title>OrderProof gate · ${esc(r.build.candidate)} · ${esc(g.verdict)}</title><style>${Html.STYLE}</style></head><body><main>")
        append("<h1>OrderProof release gate</h1>")
        append("<p class=\"muted\">Ember Kitchen · candidate <strong>${esc(r.build.candidate)}</strong> · ${esc(r.configLabel)} configuration · generated ${esc(r.generatedAt)}</p>")

        append("<section class=\"verdict ${g.verdict}\"><h2>Verdict: ${g.verdict}</h2>")
        append("<p>${g.failureCodes.joinToString(" ") { badge(it, "code") }} ${g.cleanRuns}/${g.runs} clean run(s), ${g.requiredCleanRuns} required.</p>")
        append("<ul>").append(g.reasons.joinToString("") { "<li>${esc(it)}</li>" }).append("</ul>")
        g.expectation?.let { append("<p><strong>Expectation ${if (it.met) "met" else "NOT met"}:</strong> ${esc(it.explanation)}</p>") }
        append("</section>")

        g.firstFailure?.let { f ->
            append("<h2>First failure (preserved)</h2>")
            append("<p>${badge(f.outcome.name, f.outcome.name)} <strong>${esc(f.scenario)}</strong> in run ${f.run}. Evidence: <a href=\"${esc(f.evidence)}\">${esc(f.evidence)}</a></p>")
            append("<ul>").append(f.reasons.joinToString("") { "<li>${esc(it)}</li>" }).append("</ul>")
        }
        if (g.flaky.isNotEmpty()) append("<h2>Flakiness</h2><ul>").append(g.flaky.joinToString("") { "<li>${esc(it)}</li>" }).append("</ul>")

        append("<h2>Scenario matrix</h2>")
        append(matrix(r))

        append("<h2>Build identity</h2>")
        append(Html.keyValues(listOf(
            "Candidate" to r.build.candidate, "Package" to r.build.appPackage, "Version" to r.build.versionName,
            "Source commit" to r.build.sourceCommit + if (r.build.sourceDirty) " (working tree dirty)" else "",
            "Commit reported by the running app" to r.build.reportedCommits.joinToString().ifEmpty { "—" },
            "App APK" to "${r.build.appApk} · sha256 ${r.build.appApkSha256}",
            "Test APK" to "${r.build.testApk} · sha256 ${r.build.testApkSha256}",
        )))
        append("<h2>Device</h2>")
        append(Html.keyValues(listOf(
            "AVD" to r.device.avdName, "Model" to r.device.model, "API level" to "${r.device.apiLevel} (Android ${r.device.androidRelease})",
            "Screen" to "${r.device.screenSize} @ ${r.device.density} dpi", "ABI" to r.device.abi, "Emulator" to r.device.emulator,
            "Serial" to r.device.serial, "Fingerprint" to r.device.fingerprint,
        )))

        append("<h2>Reproduce</h2>").append(Html.pre(r.reproduction.joinToString("\n")))
        append("<h2>Scenario evidence</h2>")
        val section = ExecutionSection(reader)
        r.executions.sortedWith(compareBy({ it.run }, { it.attempt })).forEach { append(section.render(it)) }

        append("<h2>Environment preparation</h2><ol>").append(r.preparation.steps.joinToString("") { "<li>${esc(it)}</li>" }).append("</ol>")
        append("<h2>Timings (diagnostic only)</h2>")
        append(Html.table(listOf("Scenario", "Durations (ms)"), r.timings.perScenarioMillis.map { (k, v) -> listOf(k, v.joinToString()) }))
        append("<p class=\"muted\">Preparation settle ${r.timings.preparationMillis} ms · total ${r.timings.totalMillis} ms.</p>")
        append("<h2>Notes</h2><ul>").append(r.notes.joinToString("") { "<li>${esc(it)}</li>" }).append("</ul>")
        append("</main></body></html>")
    }

    private fun matrix(r: VerificationReport): String {
        val scenarios = r.executions.map { it.scenario to it.title }.distinct()
        val rows = scenarios.map { (id, title) ->
            listOf("${esc(title)}<br><span class=\"muted\">${esc(id)}</span>") + (1..r.gate.runs).map { run ->
                r.executions.filter { it.scenario == id && it.run == run }.sortedBy { it.attempt }.joinToString(" ") { e ->
                    val text = if (e.attempt == 1) e.outcome.name else "rerun: ${e.outcome.name}"
                    "<a href=\"#${ExecutionSection.anchor(e)}\">${badge(text, e.outcome.name)}</a>"
                }.ifEmpty { "—" }
            }
        }
        return Html.table(listOf("Scenario") + (1..r.gate.runs).map { "Run $it" }, rows, rawCells = true) +
            "<p class=\"muted\">${badge(Outcome.PRODUCT_FAILURE.name, Outcome.PRODUCT_FAILURE.name)} the candidate broke the contract · " +
            "${badge(Outcome.INFRASTRUCTURE_FAILURE.name, Outcome.INFRASTRUCTURE_FAILURE.name)} the environment broke · " +
            "${badge(Outcome.INCONCLUSIVE.name, Outcome.INCONCLUSIVE.name)} could not tell; never counted as a pass.</p>"
    }
}
