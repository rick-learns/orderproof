package dev.orderproof.verifier

import com.google.common.truth.Truth.assertThat
import dev.orderproof.verifier.evidence.EvidenceDir
import dev.orderproof.verifier.run.E2eRecords
import org.junit.Test
import java.nio.file.Files

class ReportSafetyTest {
    @Test
    fun theControlTokenIsRedactedFromEveryWrittenFile() {
        val root = Files.createTempDirectory("report")
        val dir = EvidenceDir(root.resolve("runs/run-01/x/attempt-1"), root, listOf("s3cret-token"))
        val path = dir.write("instrumentation.txt", "am instrument -e controlToken s3cret-token -e class X")
        assertThat(path).isEqualTo("runs/run-01/x/attempt-1/instrumentation.txt")
        assertThat(Files.readString(root.resolve(path))).isEqualTo("am instrument -e controlToken [redacted] -e class X")
    }

    @Test
    fun htmlIsEscaped() {
        assertThat(dev.orderproof.verifier.report.Html.esc("<img src=x onerror=\"a\">&'")).isEqualTo("&lt;img src=x onerror=&quot;a&quot;&gt;&amp;&#39;")
    }

    @Test
    fun e2eRecordsIgnoreForeignAndMalformedLines() {
        val logcat = listOf(
            logcatLine("""{"type":"scenario.start","scenario":"s"}"""),
            "11-19 10:57:16.542  5909  5922 I OrderProof: candidate=corrected app.start",
            logcatLine("{not json"),
        ).joinToString("\n")
        val records = E2eRecords.fromLogcat(logcat)
        assertThat(records.records).hasSize(1)
        assertThat(records.started).isTrue()
    }
}
