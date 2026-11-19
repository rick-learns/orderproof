package dev.orderproof.verifier.evidence

import dev.orderproof.verifier.device.Adb
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Records the device screen with `screenrecord` around one scenario (max 180 s per Android). */
class ScreenRecorder(private val adb: Adb) {
    private var process: Process? = null

    fun start() {
        adb.shell("rm -f $REMOTE")
        process = adb.start("shell", "screenrecord", "--bit-rate", "4000000", "--time-limit", "180", REMOTE)
        // Recording has begun once the file exists on the device; a condition, not a fixed delay.
        adb.awaitCondition(10_000, intervalMillis = 200) { adb.shell("ls $REMOTE 2>/dev/null").contains(REMOTE) }
    }

    /** Stops cleanly (SIGINT finalizes the MP4) and pulls the file. Returns false if nothing usable was recorded. */
    fun stopAndPull(target: Path): Boolean {
        val running = process ?: return false
        adb.shell("pkill -INT screenrecord")
        if (!running.waitFor(30, TimeUnit.SECONDS)) running.destroyForcibly()
        process = null
        return adb.pull(REMOTE, target) && target.toFile().length() > 0
    }

    private companion object {
        const val REMOTE = "/data/local/tmp/orderproof/central.mp4"
    }
}
