package dev.orderproof.e2e.harness

import android.util.Log
import org.json.JSONObject

/**
 * Scenario evidence. Structured records go to logcat under [TAG] as one JSON object per line
 * (the verifier collects them per scenario); screenshots go to a shell-owned directory the
 * verifier pulls with `adb pull`.
 */
class Evidence(private val root: String, private val scenario: String) {
    private var shots = 0
    val directory: String get() = "$root/$scenario"

    fun prepare() {
        Shell.run("rm -rf $directory")
        Shell.run("mkdir -p $directory")
    }

    fun screenshot(label: String): String {
        val file = "%s/%02d-%s.png".format(directory, ++shots, label)
        Shell.run("screencap -p $file")
        record("screenshot", "label" to label, "file" to file)
        return file
    }

    /** Stores an arbitrary evidence file (for example a view hierarchy dump) next to the screenshots. */
    fun attach(name: String, bytes: ByteArray) {
        val file = "$directory/$name"
        Shell.write(file, bytes)
        record("attachment", "label" to name, "file" to file)
    }

    /** UI-level assertions: what the customer can see. Ledger judgment happens on the host. */
    fun check(name: String, passed: Boolean, detail: String) {
        record("assertion", "name" to name, "passed" to passed, "detail" to detail)
        if (!passed) throw AssertionError("UI assertion '$name' failed: $detail")
    }

    fun step(description: String) = record("step", "detail" to description)

    fun record(type: String, vararg fields: Pair<String, Any?>) {
        val json = JSONObject().put("type", type).put("scenario", scenario).put("atMillis", System.currentTimeMillis())
        fields.forEach { (k, v) -> json.put(k, v ?: JSONObject.NULL) }
        Log.i(TAG, json.toString())
    }

    companion object {
        const val TAG = "OrderProofE2E"
    }
}
