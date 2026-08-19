package com.wordscapes.puzzle.data.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.wordscapes.puzzle.domain.model.GameProgress
import com.wordscapes.puzzle.domain.repository.ProgressStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.IOException

/** The DataStore is created per platform and injected — see di/Platform.kt. */
class ProgressDataStore(
    private val store: DataStore<Preferences>,
) : ProgressStore {

    /** String set: Preferences has no int-set type, and a delimited string can fail to parse. */
    override val progress: Flow<GameProgress> =
        store.data
            // Losing progress is bad; a crash loop on every launch is worse.
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }
            .map { prefs ->
                val ids = prefs[KEY_COMPLETED]
                    ?.mapNotNull(String::toIntOrNull)
                    ?.toSet()
                    ?: emptySet()
                GameProgress(completedLevelIds = ids)
            }

    override suspend fun markCompleted(levelId: Int) {
        store.edit { prefs ->
            // Inside edit{}, under DataStore's lock: outside would race and drop one.
            val current = prefs[KEY_COMPLETED] ?: emptySet()
            prefs[KEY_COMPLETED] = current + levelId.toString()
        }
    }

    override suspend fun reset() {
        store.edit { it.remove(KEY_COMPLETED) }
    }

    private companion object {
        val KEY_COMPLETED = stringSetPreferencesKey("completed_level_ids")
    }
}
