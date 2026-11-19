package dev.orderproof.verifier

import java.nio.file.Path
import java.nio.file.Paths

data class VerifyOptions(
    val candidate: String,
    val appPackage: String,
    val appApk: Path,
    val testApk: Path,
    val serial: String,
    val adb: String,
    val configLabel: String,
    val databaseFile: Path,
    val tokenFile: Path,
    val backendUrl: String,
    val controlUrl: String,
    val runs: Int,
    val requiredCleanRuns: Int,
    val rerunFailures: Int,
    val scenarios: List<String>?,
    val expectFailure: String?,
    val sourceCommit: String,
    val sourceDirty: Boolean,
    val out: Path,
) {
    val controlPort: Int get() = java.net.URI(controlUrl).port

    companion object {
        val USAGE = """
            |Usage: orderproof-verifier --candidate corrected|faulty --app-apk APK --test-apk APK --db FILE --token-file FILE --out DIR
            |  [--serial emulator-5554] [--adb adb] [--config primary] [--package PKG]
            |  [--backend-url http://10.0.2.2:8787] [--control-url http://127.0.0.1:8788]
            |  [--runs 1] [--required-clean-runs RUNS] [--rerun-failures 1] [--scenarios a,b]
            |  [--expect-failure CODE] [--source-commit SHA] [--source-dirty]
        """.trimMargin()

        fun parse(args: Array<String>): VerifyOptions {
            val values = mutableMapOf<String, String>()
            val flags = mutableSetOf<String>()
            var i = 0
            while (i < args.size) {
                val name = args[i].removePrefix("--")
                require(args[i].startsWith("--")) { "Unexpected argument ${args[i]}" }
                if (name == "source-dirty") flags += name else values[name] = args.getOrNull(++i) ?: error("Missing value for --$name")
                i++
            }
            fun req(name: String) = values[name] ?: throw IllegalArgumentException("Missing --$name")
            val candidate = req("candidate")
            require(candidate in setOf("corrected", "faulty")) { "--candidate must be corrected or faulty" }
            val runs = values["runs"]?.toInt() ?: 1
            return VerifyOptions(
                candidate = candidate,
                appPackage = values["package"] ?: if (candidate == "faulty") "dev.orderproof.emberkitchen.faulty" else "dev.orderproof.emberkitchen",
                appApk = Paths.get(req("app-apk")),
                testApk = Paths.get(req("test-apk")),
                serial = values["serial"] ?: "emulator-5554",
                adb = values["adb"] ?: "adb",
                configLabel = values["config"] ?: "primary",
                databaseFile = Paths.get(req("db")),
                tokenFile = Paths.get(req("token-file")),
                backendUrl = values["backend-url"] ?: "http://10.0.2.2:8787",
                controlUrl = values["control-url"] ?: "http://127.0.0.1:8788",
                runs = runs,
                requiredCleanRuns = values["required-clean-runs"]?.toInt() ?: runs,
                rerunFailures = values["rerun-failures"]?.toInt() ?: 1,
                scenarios = values["scenarios"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
                expectFailure = values["expect-failure"],
                sourceCommit = values["source-commit"] ?: "unknown",
                sourceDirty = "source-dirty" in flags,
                out = Paths.get(req("out")),
            )
        }
    }
}
