package dev.orderproof.emberkitchen.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.orderproof.contract.ContractJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer

/**
 * One JSON-encoded value in the shared Preferences DataStore. DataStore writes atomically and
 * syncs to disk before `edit` returns, which is what makes "persist before send" meaningful.
 */
class JsonSlot<T : Any>(
    private val dataStore: DataStore<Preferences>,
    name: String,
    private val serializer: KSerializer<T>,
) {
    private val key = stringPreferencesKey(name)

    val flow: Flow<T?> = dataStore.data.map { prefs -> prefs[key]?.let(::decodeOrNull) }

    suspend fun read(): T? = flow.first()

    suspend fun write(value: T) {
        dataStore.edit { it[key] = ContractJson.encodeToString(serializer, value) }
    }

    suspend fun update(default: T, transform: (T) -> T) {
        dataStore.edit { prefs ->
            val current = prefs[key]?.let(::decodeOrNull) ?: default
            prefs[key] = ContractJson.encodeToString(serializer, transform(current))
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }

    /** A value written by an older build that no longer parses is treated as absent. */
    private fun decodeOrNull(raw: String): T? = runCatching { ContractJson.decodeFromString(serializer, raw) }.getOrNull()
}
