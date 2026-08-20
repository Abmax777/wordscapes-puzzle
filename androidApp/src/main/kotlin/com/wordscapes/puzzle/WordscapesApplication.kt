package com.wordscapes.puzzle

import android.app.Application
import com.wordscapes.puzzle.di.androidContext
import com.wordscapes.puzzle.di.initKoin

class WordscapesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Set before Koin builds the graph — createProgressDataStore() needs it.
        androidContext = applicationContext
        initKoin()
    }
}
