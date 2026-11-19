package dev.orderproof.verifier.report

/** Minimal HTML building blocks. Every piece of report data goes through [esc]. */
internal object Html {
    fun esc(text: Any?): String = text.toString()
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    fun table(headers: List<String>, rows: List<List<String>>, rawCells: Boolean = false): String = buildString {
        append("<table><thead><tr>")
        headers.forEach { append("<th>").append(esc(it)).append("</th>") }
        append("</tr></thead><tbody>")
        rows.forEach { row ->
            append("<tr>")
            row.forEach { append("<td>").append(if (rawCells) it else esc(it)).append("</td>") }
            append("</tr>")
        }
        append("</tbody></table>")
    }

    fun keyValues(pairs: List<Pair<String, Any?>>): String =
        table(listOf("Field", "Value"), pairs.map { (k, v) -> listOf(k, v?.toString() ?: "—") })

    fun badge(text: String, kind: String): String = "<span class=\"badge $kind\">${esc(text)}</span>"

    fun link(href: String?, label: String): String = if (href == null) "<span class=\"muted\">${esc(label)} (missing)</span>" else "<a href=\"${esc(href)}\">${esc(label)}</a>"

    fun details(summary: String, body: String): String = "<details><summary>${esc(summary)}</summary>$body</details>"

    fun pre(text: String): String = "<pre>${esc(text)}</pre>"

    const val STYLE = """
        :root { --ember:#B5421A; --char:#24201D; --cream:#FBF4EA; --ok:#1E6B3A; --bad:#A3241B; --warn:#8A5A00; --line:#E6D8C6; }
        * { box-sizing: border-box; }
        body { font: 15px/1.5 system-ui, sans-serif; color: var(--char); background: var(--cream); margin: 0; }
        main { max-width: 1180px; margin: 0 auto; padding: 24px; }
        h1 { margin: 0 0 4px; } h2 { border-bottom: 2px solid var(--ember); padding-bottom: 4px; margin-top: 36px; }
        .verdict { padding: 18px 22px; border-radius: 10px; color: #fff; margin: 18px 0; }
        .verdict.PASS { background: var(--ok); } .verdict.FAIL { background: var(--bad); }
        .verdict h2 { border: 0; margin: 0 0 6px; color: #fff; }
        .badge { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: 12px; font-weight: 600; color: #fff; }
        .badge.PASS { background: var(--ok); } .badge.PRODUCT_FAILURE { background: var(--bad); }
        .badge.INFRASTRUCTURE_FAILURE, .badge.INCONCLUSIVE { background: var(--warn); } .badge.code { background: var(--char); }
        table { border-collapse: collapse; width: 100%; margin: 8px 0 14px; background: #fff; }
        th, td { border: 1px solid var(--line); padding: 5px 8px; text-align: left; vertical-align: top; font-size: 13px; }
        th { background: #F3E6D5; }
        .card { background: #fff; border: 1px solid var(--line); border-left: 6px solid var(--ember); border-radius: 8px; padding: 14px 18px; margin: 16px 0; }
        .shots { display: flex; flex-wrap: wrap; gap: 10px; } .shots figure { margin: 0; width: 170px; font-size: 12px; }
        .shots img { width: 170px; border: 1px solid var(--line); border-radius: 6px; }
        video { max-width: 360px; border-radius: 8px; border: 1px solid var(--line); }
        pre { background: var(--char); color: #F6EBDD; padding: 10px; border-radius: 6px; overflow-x: auto; font-size: 12px; white-space: pre-wrap; }
        code { background: #F3E6D5; padding: 1px 4px; border-radius: 4px; }
        .muted { color: #7A6E64; } details { margin: 6px 0; } summary { cursor: pointer; font-weight: 600; }
    """
}
