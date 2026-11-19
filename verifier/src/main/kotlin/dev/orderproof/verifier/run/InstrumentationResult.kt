package dev.orderproof.verifier.run

import kotlinx.serialization.Serializable

@Serializable
enum class TestStatus { PASSED, FAILED, RUNNER_CRASHED, NOT_RUN }

/** Parsed `am instrument -r` output for a single test. */
@Serializable
data class InstrumentationResult(val status: TestStatus, val failure: String?, val runnerMessage: String?) {
    companion object {
        /**
         * Status codes per test: 1 = started, 0 = OK, -2 = assertion failure, -1 = error.
         * A `shortMsg` in the final result means the test process itself died.
         */
        fun parse(raw: String): InstrumentationResult {
            val codes = Regex("""INSTRUMENTATION_STATUS_CODE: (-?\d+)""").findAll(raw).map { it.groupValues[1].toInt() }.toList()
            val stack = Regex("""INSTRUMENTATION_STATUS: stack=(.+?)(?=\nINSTRUMENTATION_|\z)""", RegexOption.DOT_MATCHES_ALL)
                .find(raw)?.groupValues?.get(1)?.trim()
            val shortMsg = Regex("""INSTRUMENTATION_RESULT: shortMsg=(.*)""").find(raw)?.groupValues?.get(1)?.trim()
            val status = when {
                shortMsg != null -> TestStatus.RUNNER_CRASHED
                codes.any { it == -1 || it == -2 } -> TestStatus.FAILED
                codes.contains(0) && Regex("""^OK \(1 test\)""", RegexOption.MULTILINE).containsMatchIn(raw) -> TestStatus.PASSED
                codes.isEmpty() -> TestStatus.NOT_RUN
                else -> TestStatus.FAILED
            }
            return InstrumentationResult(status, stack?.lineSequence()?.take(12)?.joinToString("\n"), shortMsg)
        }
    }
}
