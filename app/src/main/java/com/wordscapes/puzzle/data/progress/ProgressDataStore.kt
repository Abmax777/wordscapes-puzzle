package com.wordscapes.puzzle.data.progress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wordscapes.puzzle.domain.model.GameProgress
import com.wordscapes.puzzle.domain.repository.ProgressStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One DataStore instance per process, enforced by the delegate. Declared at
 * file scope because `preferencesDataStore` throws if the same file name is
 * delegated twice — a real crash people hit by putting it inside a class.
 */
private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wordscapes_progress",
)

@Singleton
class ProgressDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProgressStore {

    /**
     * Level ids are stored as a string set rather than, say, a comma-joined
     * string. Preferences has no int-set type, and a delimited string would
     * need parsing that can fail; a set of strings either contains a value or
     * does not.
     */
    override val progress: Flow<GameProgress> =
        context.progressDataStore.data
            // A corrupt or unreadable file must not take the app down. Falling
            // back to empty means the player loses progress, which is bad; a
            // crash loop on every launch is worse.
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
        context.progressDataStore.edit { prefs ->
            // Read-modify-write inside edit{}, which DataStore runs under its
            // own lock. Doing it outside would race two levels completing in
            // quick succession and silently drop one.
            val current = prefs[KEY_COMPLETED] ?: emptySet()
            prefs[KEY_COMPLETED] = current + levelId.toString()
        }
    }

    override suspend fun reset() {
        context.progressDataStore.edit { it.remove(KEY_COMPLETED) }
    }

    private companion object {
        val KEY_COMPLETED = stringSetPreferencesKey("completed_level_ids")
    }
}
