package dev.orderproof.verifier.device

import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class CommandResult(val exitCode: Int, val output: String, val timedOut: Boolean) {
    val ok: Boolean get() = exitCode == 0 && !timedOut
}

/** Thin wrapper over the `adb` binary for one device. Every call has a deadline. */
class Adb(private val adbPath: String, val serial: String) {
    fun run(vararg args: String, timeoutSeconds: Long = 120): CommandResult {
        val process = start(*args)
        val output = StringBuilder()
        val reader = Thread { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } }.apply { start() }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        reader.join(5_000)
        return CommandResult(if (finished) process.exitValue() else -1, output.toString(), !finished)
    }

    fun shell(command: String, timeoutSeconds: Long = 120): String = run("shell", command, timeoutSeconds = timeoutSeconds).output.trim()

    /** Starts a long-running adb command (for example `screenrecord`) and returns its process. */
    fun start(vararg args: String): Process =
        ProcessBuilder(listOf(adbPath, "-s", serial) + args).redirectErrorStream(true).start()

    fun pull(remote: String, local: Path): Boolean = run("pull", remote, local.toString(), timeoutSeconds = 300).ok

    /** Polls a condition every [intervalMillis] until it holds or [timeoutMillis] passes (device readiness only). */
    fun awaitCondition(timeoutMillis: Long, intervalMillis: Long = 1_000, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(intervalMillis)
        }
        return condition()
    }
}
