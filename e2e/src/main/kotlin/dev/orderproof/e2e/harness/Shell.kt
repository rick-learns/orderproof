package dev.orderproof.e2e.harness

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Runs one command as the shell user and waits for it to finish by draining its output.
 * The command is split on spaces and exec'd directly (no `sh`), so pass one command at a time.
 */
object Shell {
    fun run(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    /** Writes bytes to a shell-owned path (the test process itself cannot write there) by piping through `tee`. */
    fun write(path: String, bytes: ByteArray) {
        val (output, input) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRw("tee $path")
        ParcelFileDescriptor.AutoCloseOutputStream(input).use { it.write(bytes) }
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }
}
