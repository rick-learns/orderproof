package dev.orderproof.verifier.evidence

import java.nio.file.Files
import java.nio.file.Path

/**
 * One scenario execution's evidence folder. All text passes through [redact], so the control
 * token (which travels as an instrumentation argument) never lands in a report.
 */
class EvidenceDir(val root: Path, private val reportRoot: Path, private val secrets: List<String>) {
    init {
        Files.createDirectories(root)
    }

    fun write(name: String, text: String): String {
        Files.writeString(root.resolve(name), redact(text))
        return relative(name)
    }

    fun file(name: String): Path = root.resolve(name)

    fun exists(name: String): Boolean = Files.isRegularFile(root.resolve(name)) && Files.size(root.resolve(name)) > 0

    /** Path relative to the report folder, for links in the HTML and JSON. */
    fun relative(name: String): String = reportRoot.relativize(root.resolve(name)).toString()

    fun listFiles(suffix: String): List<String> = Files.list(root).use { files ->
        files.filter { it.fileName.toString().endsWith(suffix) }.map { reportRoot.relativize(it).toString() }.sorted().toList()
    }

    fun redact(text: String): String = secrets.filter { it.isNotBlank() }.fold(text) { acc, s -> acc.replace(s, "[redacted]") }
}
