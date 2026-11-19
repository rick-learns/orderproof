package dev.orderproof.emberkitchen.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The backend address is configured by the person running the app; nothing is hardcoded.
 * Typical values: `http://10.0.2.2:8787` on an emulator, `http://192.168.x.y:8787` on a phone.
 */
class ConnectionSettings(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("backend_url")

    val backendUrl: Flow<String?> = dataStore.data.map { it[key] }

    suspend fun current(): String? = backendUrl.first()

    suspend fun save(url: String) {
        val normalized = normalize(url) ?: throw IllegalArgumentException("Not an http(s) URL: $url")
        dataStore.edit { it[key] = normalized }
    }

    companion object {
        const val EMULATOR_HINT = "http://10.0.2.2:8787"

        fun normalize(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            val parsed = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
            if (parsed.scheme !in setOf("http", "https") || parsed.host.isNullOrBlank()) return null
            return withScheme
        }
    }
}
