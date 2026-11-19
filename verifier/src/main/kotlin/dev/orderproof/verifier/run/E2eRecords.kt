package dev.orderproof.verifier.run

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class UiAssertion(val name: String, val passed: Boolean, val detail: String)

/** Structured records the e2e harness logs under tag OrderProofE2E, one JSON object per line. */
class E2eRecords(val records: List<JsonObject>) {
    private fun ofType(type: String) = records.filter { it.str("type") == type }

    val started: Boolean get() = ofType("scenario.start").isNotEmpty()
    val end: JsonObject? get() = ofType("scenario.end").lastOrNull()
    val endKind: String? get() = end?.str("kind")
    val endError: String? get() = end?.str("error")
    val assertions: List<UiAssertion>
        get() = ofType("assertion").map { UiAssertion(it.str("name").orEmpty(), it["passed"]?.jsonPrimitive?.booleanOrNull == true, it.str("detail").orEmpty()) }
    val screenshots: List<Pair<String, String>> get() = ofType("screenshot").map { it.str("label").orEmpty() to it.str("file").orEmpty() }
    val attachments: List<String> get() = ofType("attachment").mapNotNull { it.str("file") }
    val steps: List<String> get() = ofType("step").mapNotNull { it.str("detail") } + ofType("observation").mapNotNull { "observed: " + it.str("detail") }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Extracts records from `logcat -v threadtime` text; non-JSON lines are ignored. */
        fun fromLogcat(logcat: String, tag: String = "OrderProofE2E"): E2eRecords = E2eRecords(
            logcat.lineSequence()
                .filter { " $tag: " in it }
                .mapNotNull { line -> runCatching { json.parseToJsonElement(line.substringAfter(" $tag: ")) as JsonObject }.getOrNull() }
                .toList(),
        )

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
