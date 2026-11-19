package dev.orderproof.backend.config

import java.nio.file.Path
import java.nio.file.Paths

enum class ClockMode { SYSTEM, FIXED }

/**
 * Runtime configuration. The control server only exists when [controlTokenFile] is set, and it
 * always binds to loopback: fault injection and ledger access never reach a LAN-connected phone.
 */
data class BackendConfig(
    val publicHost: String = "0.0.0.0",
    val publicPort: Int = 8787,
    val controlPort: Int = 8788,
    val controlTokenFile: Path? = null,
    val databaseFile: Path = Paths.get("backend/data/orderproof.db"),
    val clockMode: ClockMode = ClockMode.SYSTEM,
    val resetOnStart: Boolean = false,
) {
    val controlEnabled: Boolean get() = controlTokenFile != null

    companion object {
        const val CONTROL_HOST = "127.0.0.1"

        fun parse(args: Array<String>): BackendConfig {
            var config = BackendConfig()
            var i = 0
            fun value(): String = args.getOrNull(++i) ?: error("Missing value for ${args[i - 1]}")
            while (i < args.size) {
                config = when (val flag = args[i]) {
                    "--host" -> config.copy(publicHost = value())
                    "--port" -> config.copy(publicPort = value().toInt())
                    "--control-port" -> config.copy(controlPort = value().toInt())
                    "--control-token-file" -> config.copy(controlTokenFile = Paths.get(value()))
                    "--db" -> config.copy(databaseFile = Paths.get(value()))
                    "--clock" -> config.copy(clockMode = ClockMode.valueOf(value().uppercase()))
                    "--reset" -> config.copy(resetOnStart = true)
                    else -> error("Unknown flag: $flag")
                }
                i++
            }
            return config
        }
    }
}
