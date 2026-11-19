package dev.orderproof.verifier.report

import dev.orderproof.control.BackendEvent
import dev.orderproof.oracle.Ledger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class FileEvidenceReader(private val reportRoot: Path) : EvidenceReader {
    private val json = Json { ignoreUnknownKeys = true }

    override fun text(path: String?): String? = path?.let { reportRoot.resolve(it) }?.takeIf(Files::isRegularFile)?.let(Files::readString)

    override fun ledger(path: String?): Ledger? = text(path)?.let { json.decodeFromString(Ledger.serializer(), it) }

    override fun events(path: String?): List<BackendEvent>? = text(path)?.let { json.decodeFromString(ListSerializer(BackendEvent.serializer()), it) }
}
