package dev.orderproof.e2e.harness

import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Gives every scenario a clean, independent world: backend reset to the seed (fresh clock,
 * stock, coupons, no faults or holds), the app stopped and its data wiped, device display
 * settings restored. Records start/end markers that the verifier uses to cut evidence.
 */
class ScenarioRule : TestWatcher() {
    val args: E2eArgs = E2eArgs.fromInstrumentation()
    val control = ControlClient(args.controlUrl, args.controlToken)
    val app = AppDriver(args.targetPackage, args.waitMillis)
    lateinit var evidence: Evidence
        private set

    override fun starting(description: Description) {
        evidence = Evidence(args.evidenceDir, description.methodName)
        try {
            evidence.prepare()
            // A system dialog left by another app (e.g. a launcher ANR on a busy emulator) would hide the candidate.
            Shell.run("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
            DisplaySettings.restore()
            control.reset()
            app.forceStop()
            app.clearData()
        } catch (e: Exception) {
            throw InfrastructureError("could not reset the environment: ${e.message}", e)
        }
        evidence.record("scenario.start", "target" to args.targetPackage, "backendUrl" to args.backendUrl)
    }

    override fun succeeded(description: Description) {
        evidence.record("scenario.end", "result" to "passed")
    }

    override fun failed(e: Throwable, description: Description) {
        runCatching { evidence.screenshot("failure") }
        val kind = when {
            e is InfrastructureError || e.cause is InfrastructureError -> "infrastructure"
            e is AssertionError -> "assertion"
            else -> "error"
        }
        evidence.record("scenario.end", "result" to "failed", "kind" to kind, "error" to "${e.javaClass.simpleName}: ${e.message}")
    }

    override fun finished(description: Description) {
        runCatching { control.releaseHolds() }
        runCatching { DisplaySettings.restore() }
    }
}

/** Font scale and display size overrides for the large-text scenario, always undone afterwards. */
object DisplaySettings {
    fun apply(fontScale: String, size: String?, density: Int?) {
        Shell.run("settings put system font_scale $fontScale")
        size?.let { Shell.run("wm size $it") }
        density?.let { Shell.run("wm density $it") }
    }

    fun restore() {
        Shell.run("settings put system font_scale 1.0")
        Shell.run("wm size reset")
        Shell.run("wm density reset")
    }
}
