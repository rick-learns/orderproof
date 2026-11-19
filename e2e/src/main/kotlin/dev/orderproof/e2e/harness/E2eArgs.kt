package dev.orderproof.e2e.harness

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Everything environment-specific arrives as instrumentation arguments (`am instrument -e`),
 * so the same APK runs unchanged against either candidate and any backend.
 */
data class E2eArgs(
    val targetPackage: String,
    val backendUrl: String,
    val controlUrl: String,
    val controlToken: String,
    val evidenceDir: String,
    val waitMillis: Long,
) {
    companion object {
        fun fromInstrumentation(): E2eArgs {
            val args = InstrumentationRegistry.getArguments()
            fun required(name: String) = requireNotNull(args.getString(name)) { "Missing instrumentation argument -e $name" }
            return E2eArgs(
                targetPackage = required("targetPackage"),
                backendUrl = args.getString("backendUrl") ?: "http://10.0.2.2:8787",
                controlUrl = args.getString("controlUrl") ?: "http://127.0.0.1:8788",
                controlToken = required("controlToken"),
                evidenceDir = args.getString("evidenceDir") ?: "/data/local/tmp/orderproof/evidence",
                waitMillis = args.getString("waitMillis")?.toLong() ?: 20_000,
            )
        }
    }
}
