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
}
