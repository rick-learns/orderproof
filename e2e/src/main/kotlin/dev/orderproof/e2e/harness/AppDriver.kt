package dev.orderproof.e2e.harness

import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/**
 * Drives the candidate app from outside its process through accessibility, using the Compose
 * test tags it exposes as resource ids. Every wait is a condition with a deadline, never a sleep.
 */
class AppDriver(private val pkg: String, private val waitMillis: Long) {
    val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** Starts the app, or brings its existing task to the front exactly as the launcher would. */
    fun launch() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: throw InfrastructureError("Package $pkg is not installed")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (!device.wait(Until.hasObject(By.pkg(pkg).depth(0)), waitMillis)) throw AssertionError("$pkg did not come to the foreground")
    }

    /** Real process death: the kernel reclaims the process, nothing in the app gets a callback. */
    fun forceStop() {
        Shell.run("am force-stop $pkg")
        device.wait(Until.gone(By.pkg(pkg)), waitMillis)
    }

    /**
     * Wipes app data, which also revokes runtime permissions. On API 37+ the app needs
     * ACCESS_LOCAL_NETWORK to reach the backend; it is granted up front so no system dialog
     * interrupts the journey (the app's own request flow is covered by its UI tests).
     */
    fun clearData() {
        Shell.run("pm clear $pkg")
        if (Build.VERSION.SDK_INT >= 37) Shell.run("pm grant $pkg android.permission.ACCESS_LOCAL_NETWORK")
    }

    fun pressHome() {
        device.pressHome()
        device.wait(Until.gone(By.pkg(pkg).depth(0)), waitMillis)
    }

    fun await(tag: String): UiObject2 = device.wait(Until.findObject(res(tag)), waitMillis)
        ?: throw AssertionError("Timed out after ${waitMillis}ms waiting for '$tag' on screen")

    /** Waits until one of [tags] shows and returns which one. */
    fun awaitAny(vararg tags: String): String {
        val pattern = Pattern.compile(tags.joinToString("|") { Pattern.quote(it) })
        val found = device.wait(Until.findObject(By.res(pattern)), waitMillis)
            ?: throw AssertionError("Timed out after ${waitMillis}ms waiting for any of ${tags.toList()}")
        return found.resourceName.substringAfterLast('/')
    }

    fun has(tag: String): Boolean = device.hasObject(res(tag))

    /** Clicks once the control is enabled; taps on a still-disabled button would be silently lost. */
    fun click(tag: String, scrollIn: String? = null) {
        if (scrollIn != null) scrollTo(tag, scrollIn) else await(tag)
        val enabled = device.wait(Until.findObject(res(tag).enabled(true)), waitMillis)
            ?: throw AssertionError("'$tag' stayed disabled for ${waitMillis}ms")
        enabled.click()
    }

    /**
     * Swipes [container] upward until [tag] is on screen. The end of the list is detected by its
     * content no longer changing after a swipe, rather than by scroll accessibility events, which
     * Compose lists do not always report the way UiAutomator's own scrollUntil expects.
     */
    fun scrollTo(tag: String, container: String): UiObject2 {
        repeat(MAX_SWIPES) {
            device.findObject(res(tag))?.let { return it }
            val list = await(container)
            val before = collect(list)
            val b = list.visibleBounds
            device.swipe(b.centerX(), b.bottom - b.height() / 4, b.centerX(), b.top + b.height() / 4, SWIPE_STEPS)
            device.waitForIdle()
            if (collect(await(container)) == before) {
                return device.findObject(res(tag)) ?: throw AssertionError("'$tag' not found; '$container' cannot scroll further")
            }
        }
        throw AssertionError("'$tag' not reached after $MAX_SWIPES swipes of '$container'")
    }

    /** Clicks [tag] repeatedly with no waits in between, as fast as touch events can be injected. */
    fun burstClick(tag: String, times: Int) {
        val center = await(tag).visibleCenter
        repeat(times) { device.click(center.x, center.y) }
    }

    /**
     * Sets a field's text through accessibility (as a keyboard or autofill service would) without
     * focusing it, so no soft keyboard opens and covers controls the scenario taps next.
     */
    fun type(tag: String, value: String, scrollIn: String? = null) {
        val field = if (scrollIn != null) scrollTo(tag, scrollIn) else await(tag)
        field.text = value
    }

    /** All visible text of a node and its descendants (Compose may merge text into the parent). */
    fun text(tag: String): String = collect(await(tag))

    fun isEnabled(tag: String): Boolean = await(tag).isEnabled

    fun waitForText(tag: String, fragment: String): String {
        val deadline = System.nanoTime() + waitMillis * 1_000_000
        var last = ""
        while (System.nanoTime() < deadline) {
            device.findObject(res(tag))?.let { last = collect(it) }
            if (fragment in last) return last
            device.waitForIdle(500)
        }
        throw AssertionError("'$tag' never showed '$fragment' (last: '$last')")
    }

    private fun collect(node: UiObject2): String =
        (listOfNotNull(node.text, node.contentDescription) + node.children.map(::collect)).filter { it.isNotBlank() }.joinToString(" ")

    private fun res(tag: String): BySelector = By.res(tag)

    private companion object {
        const val MAX_SWIPES = 25
        /** About 5 ms per step: a deliberate drag, not a fling that could skip past the target. */
        const val SWIPE_STEPS = 40
    }
}
