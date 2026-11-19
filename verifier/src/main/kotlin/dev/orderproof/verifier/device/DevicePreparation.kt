package dev.orderproof.verifier.device

import kotlinx.serialization.Serializable

@Serializable
data class PreparationLog(val steps: List<String>, val loadBefore: String, val loadAfter: String, val settleMillis: Long)

/**
 * Brings an emulator to a known state before any scenario: booted, awake, animations off,
 * stray system dialogs suppressed, both APKs installed and AOT-compiled (a cold emulator can
 * otherwise ANR the test process during startup), the control port reversed to the host, and
 * the guest's load settled after boot. Failures here are infrastructure failures, never verdicts.
 */
class DevicePreparation(private val adb: Adb) {
    private val steps = mutableListOf<String>()

    fun prepare(appApk: String, testApk: String, appPackage: String, controlPort: Int): PreparationLog {
        step("wait for device") { adb.run("wait-for-device", timeoutSeconds = 300).ok }
        step("boot completed") { adb.awaitCondition(300_000) { adb.shell("getprop sys.boot_completed") == "1" } }
        listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").forEach {
            adb.shell("settings put global $it 0")
        }
        adb.shell("settings put global hide_error_dialogs 1")
        adb.shell("svc power stayon true")
        adb.shell("input keyevent KEYCODE_WAKEUP")
        adb.shell("wm dismiss-keyguard")
        adb.shell("logcat -G 16M")
        steps += "device settings applied (animations off, error dialogs hidden, stay awake)"
        // Exactly one candidate on the device: a stale other candidate can resurface when the
        // one under test is force-stopped, and its traffic would pollute the ledger.
        CANDIDATE_PACKAGES.filter { it != appPackage }.forEach { other ->
            if (adb.shell("pm list packages $other").lines().any { it.trim() == "package:$other" }) {
                step("uninstall other candidate $other") { adb.run("uninstall", other).output.contains("Success") }
            }
        }
        step("install $appApk") { adb.run("install", "-r", "-t", appApk, timeoutSeconds = 600).output.contains("Success") }
        step("install $testApk") { adb.run("install", "-r", "-t", testApk, timeoutSeconds = 600).output.contains("Success") }
        step("AOT-compile $appPackage") { adb.shell("cmd package compile -m speed -f $appPackage", 600).contains("Success") }
        step("AOT-compile $TEST_PACKAGE") { adb.shell("cmd package compile -m speed -f $TEST_PACKAGE", 600).contains("Success") }
        step("reverse tcp:$controlPort") { adb.run("reverse", "tcp:$controlPort", "tcp:$controlPort").ok }
        val before = load()
        val started = System.currentTimeMillis()
        // Readiness, not test timing: wait (bounded) for post-boot work to quiet down.
        adb.awaitCondition(SETTLE_TIMEOUT_MILLIS, intervalMillis = 5_000) { (load().toDoubleOrNull() ?: 99.0) < SETTLED_LOAD }
        val after = load()
        steps += "load settled from $before to $after"
        return PreparationLog(steps.toList(), before, after, System.currentTimeMillis() - started)
    }

    private fun load(): String = adb.shell("cat /proc/loadavg").substringBefore(' ')

    private fun step(name: String, action: () -> Boolean) {
        if (!action()) throw InfrastructureFailure("device preparation failed at: $name")
        steps += name
    }

    companion object {
        const val TEST_PACKAGE = "dev.orderproof.e2e"
        val CANDIDATE_PACKAGES = listOf("dev.orderproof.emberkitchen", "dev.orderproof.emberkitchen.faulty")
        private const val SETTLED_LOAD = 8.0
        private const val SETTLE_TIMEOUT_MILLIS = 600_000L
    }
}

class InfrastructureFailure(message: String) : RuntimeException(message)
