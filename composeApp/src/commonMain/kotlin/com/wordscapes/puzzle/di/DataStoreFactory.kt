package com.wordscapes.puzzle.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

internal const val PROGRESS_FILE = "wordscapes_progress.preferences_pb"

internal fun progressDataStoreAt(directory: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { "$directory/$PROGRESS_FILE".toPath() },
    )
