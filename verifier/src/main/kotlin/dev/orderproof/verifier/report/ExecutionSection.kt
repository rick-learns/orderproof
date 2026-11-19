package dev.orderproof.verifier.report

import dev.orderproof.control.BackendEvent
import dev.orderproof.oracle.Ledger
import dev.orderproof.verifier.judge.ScenarioExecution
import dev.orderproof.verifier.report.Html.badge
import dev.orderproof.verifier.report.Html.esc
import dev.orderproof.verifier.report.Html.table

/** Reads evidence files (paths relative to the report folder) so the page can show them inline. */
interface EvidenceReader {
    fun text(path: String?): String?
    fun ledger(path: String?): Ledger?
    fun events(path: String?): List<BackendEvent>?
}

internal class ExecutionSection(private val reader: EvidenceReader) {
    fun render(e: ScenarioExecution): String = buildString {
        val label = if (e.attempt == 1) "run ${e.run}" else "run ${e.run} · diagnostic rerun ${e.attempt - 1} (not counted)"
        append("<div class=\"card\" id=\"${anchor(e)}\"><h3>${esc(e.title)} <span class=\"muted\">· ${esc(e.scenario)} · ${esc(label)}</span></h3>")
        append("<p>${badge(e.outcome.name, e.outcome.name)} <span class=\"muted\">test ${esc(e.testStatus)} · ${e.durationMillis} ms (diagnostic)</span></p>")
        append("<ul>").append(e.reasons.joinToString("") { "<li>${esc(it)}</li>" }).append("</ul>")
        if (e.missingEvidence.isNotEmpty()) append("<p><strong>Missing evidence:</strong> ${esc(e.missingEvidence.joinToString())}</p>")
        e.oracle?.violations?.takeIf { it.isNotEmpty() }?.let { violations ->
            append("<h4>Ledger oracle violations</h4>")
            append(table(listOf("Code", "What the ledger shows", "Ledger ids"), violations.map { v -> listOf(v.code.name, v.message, v.evidence.entries.joinToString("\n") { "${it.key}=${it.value}" }) }))
        }
        append("<h4>What the customer saw (UI assertions)</h4>")
        append(table(listOf("Assertion", "Result", "Observed"), e.uiAssertions.map { listOf(it.name, if (it.passed) "pass" else "FAIL", it.detail) }))
        reader.ledger(e.evidence.ledger)?.let { append(ledgerSection(it)) }
        if (e.evidence.recording != null) append("<h4>Recording</h4><video controls preload=\"metadata\" src=\"${esc(e.evidence.recording)}\"></video>")
        append("<h4>Screenshots</h4><div class=\"shots\">")
        e.evidence.screenshots.forEach { append("<figure><a href=\"${esc(it)}\"><img loading=\"lazy\" src=\"${esc(it)}\" alt=\"${esc(it.substringAfterLast('/'))}\"></a><figcaption>${esc(it.substringAfterLast('/'))}</figcaption></figure>") }
        append("</div>")
        if (e.evidence.attachments.isNotEmpty()) append("<p>Attachments: ${e.evidence.attachments.joinToString(" · ") { Html.link(it, it.substringAfterLast('/')) }}</p>")
        append(Html.details("Steps (${e.steps.size})", "<ol>" + e.steps.joinToString("") { "<li>${esc(it)}</li>" } + "</ol>"))
        e.testFailure?.let { append(Html.details("Test failure", Html.pre(it))) }
        reader.events(e.evidence.backendEvents)?.let { events ->
            append(Html.details("Backend events (${events.size})", table(listOf("seq", "kind", "request", "attempt", "key", "order", "detail"),
                events.map { listOf(it.seq.toString(), it.kind, it.requestId.orEmpty(), it.checkoutAttemptId.orEmpty(), it.idempotencyKey.orEmpty(), it.orderId.orEmpty(), it.detail) })))
        }
        reader.text(e.evidence.appLog)?.let { append(Html.details("App log (${it.lines().size} lines)", Html.pre(it))) }
        append("<p>Raw files: ")
        append(listOf(e.evidence.instrumentation to "instrumentation", e.evidence.logcat to "logcat", e.evidence.appLog to "app log",
            e.evidence.backendEvents to "backend events", e.evidence.ledger to "ledger", e.evidence.oracle to "oracle verdict").joinToString(" · ") { Html.link(it.first, it.second) })
        append("</p></div>")
    }

    private fun ledgerSection(ledger: Ledger): String {
        val payments = ledger.payments.groupBy { it.orderId }
        val rows = ledger.orders.map { o ->
            listOf(o.orderNumber, o.checkoutAttemptId ?: "—", o.idempotencyKey, o.lines.joinToString("; ") { "${it.quantity}× ${it.itemId} ${it.optionIds}" },
                "${o.subtotalMinor} − ${o.discountMinor} + ${o.taxMinor} = ${o.totalMinor}",
                payments[o.orderId].orEmpty().joinToString { "${it.status} ${it.amountMinor}" }.ifEmpty { "none" })
        }
        val body = if (rows.isEmpty()) "<p class=\"muted\">No orders persisted.</p>" else table(listOf("Order", "Checkout attempt", "Idempotency key", "Lines", "Subtotal − discount + tax = total (minor units)", "Payments"), rows)
        return "<h4>Persisted ledger (${ledger.orders.size} order(s), ${ledger.payments.size} payment(s))</h4>$body"
    }

    companion object {
        fun anchor(e: ScenarioExecution) = "${e.scenario}-run${e.run}-a${e.attempt}"
    }
}
