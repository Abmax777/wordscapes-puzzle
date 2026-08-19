package com.wordscapes.puzzle.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

// Set once from Application.onCreate before Koin builds the graph.
lateinit var androidContext: Context

actual fun createProgressDataStore(): DataStore<Preferences> =
    progressDataStoreAt(androidContext.filesDir.absolutePath)
