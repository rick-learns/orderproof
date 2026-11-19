package dev.orderproof.backend

import dev.orderproof.backend.config.BackendConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.HexFormat
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    val config = BackendConfig.parse(args)
    val token = config.controlTokenFile?.let(::issueControlToken)
    val app = BackendApp(config, token).start()
    println("Ember Kitchen backend listening on http://${config.publicHost}:${app.publicPort} (db=${config.databaseFile}, clock=${config.clockMode})")
    app.controlPort?.let { println("Control API on http://${BackendConfig.CONTROL_HOST}:$it (token in ${config.controlTokenFile})") }
        ?: println("Control API disabled (no --control-token-file).")
    val stopped = CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(Thread { app.close(); stopped.countDown() })
    stopped.await()
}

/** Fresh random token per backend start, written 0600 so only the local test runner can read it. */
private fun issueControlToken(file: Path): String {
    val token = HexFormat.of().formatHex(ByteArray(24).also { SecureRandom().nextBytes(it) })
    file.toAbsolutePath().parent?.let { Files.createDirectories(it) }
    Files.deleteIfExists(file)
    Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
    Files.writeString(file, token)
    return token
}
