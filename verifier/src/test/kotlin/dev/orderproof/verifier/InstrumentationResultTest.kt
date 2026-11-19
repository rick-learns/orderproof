package dev.orderproof.verifier

import com.google.common.truth.Truth.assertThat
import dev.orderproof.verifier.run.InstrumentationResult
import dev.orderproof.verifier.run.TestStatus
import org.junit.Test

class InstrumentationResultTest {
    private val started = "INSTRUMENTATION_STATUS: test=normalCheckout\nINSTRUMENTATION_STATUS_CODE: 1\n"

    @Test
    fun passingTest() {
        val raw = started + "INSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_RESULT: stream=\n\nTime: 30.1\n\nOK (1 test)\n\n\nINSTRUMENTATION_CODE: -1\n"
        assertThat(InstrumentationResult.parse(raw).status).isEqualTo(TestStatus.PASSED)
    }

    @Test
    fun failingTestKeepsTheStack() {
        val raw = started + "INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: Timed out after 20000ms waiting for 'menu_list'\n" +
            "\tat dev.orderproof.e2e.harness.AppDriver.await(AppDriver.kt:45)\nINSTRUMENTATION_STATUS_CODE: -2\n" +
            "INSTRUMENTATION_RESULT: stream=\nFAILURES!!!\nTests run: 1,  Failures: 1\n\nINSTRUMENTATION_CODE: -1\n"
        val result = InstrumentationResult.parse(raw)
        assertThat(result.status).isEqualTo(TestStatus.FAILED)
        assertThat(result.failure).startsWith("java.lang.AssertionError: Timed out")
    }

    @Test
    fun crashedRunnerIsNotAProductFailure() {
        val raw = "INSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0\n"
        val result = InstrumentationResult.parse(raw)
        assertThat(result.status).isEqualTo(TestStatus.RUNNER_CRASHED)
        assertThat(result.runnerMessage).isEqualTo("Process crashed.")
    }

    @Test
    fun noOutputMeansNotRun() {
        assertThat(InstrumentationResult.parse("").status).isEqualTo(TestStatus.NOT_RUN)
    }
}
