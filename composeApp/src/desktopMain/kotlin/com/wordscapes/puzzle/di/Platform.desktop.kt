package com.wordscapes.puzzle.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual fun createProgressDataStore(): DataStore<Preferences> {
    val dir = File(System.getProperty("user.home"), ".wordscapes").apply { mkdirs() }
    return progressDataStoreAt(dir.absolutePath)
}
