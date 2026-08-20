package com.wordscapes.puzzle.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineDispatcher

// Dispatchers.IO is JVM-only; Kotlin/Native has no equivalent, so iOS maps this
// to Default. Injected rather than referenced directly so tests can substitute.
expect val ioDispatcher: CoroutineDispatcher

// DataStore needs a platform file path: Context.filesDir on Android, the user
// home on desktop, NSDocumentDirectory on iOS.
expect fun createProgressDataStore(): DataStore<Preferences>
