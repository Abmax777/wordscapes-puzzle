package com.wordscapes.puzzle.di

import com.wordscapes.puzzle.data.dictionary.DictionarySource
import com.wordscapes.puzzle.data.level.LevelJsonSource
import com.wordscapes.puzzle.data.level.LevelRepository
import com.wordscapes.puzzle.data.progress.ProgressDataStore
import com.wordscapes.puzzle.domain.repository.LevelCatalog
import com.wordscapes.puzzle.domain.repository.ProgressStore
import com.wordscapes.puzzle.domain.repository.WordLookup
import com.wordscapes.puzzle.domain.usecase.ValidateWord
import com.wordscapes.puzzle.ui.game.GameViewModel
import com.wordscapes.puzzle.ui.home.HomeViewModel
import com.wordscapes.puzzle.ui.levelselect.LevelSelectViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Koin replaces Hilt, which is Android-only. Same shape as before: domain
 * interfaces bound to data implementations, everything a singleton.
 */
val appModule = module {
    single { createProgressDataStore() }

    single { LevelJsonSource(ioDispatcher) }
    single<LevelCatalog> { LevelRepository(get()) }
    single<WordLookup> { DictionarySource(ioDispatcher) }
    single<ProgressStore> { ProgressDataStore(get()) }
    single { ValidateWord(get()) }

    viewModel { HomeViewModel(get(), get()) }
    viewModel { LevelSelectViewModel(get(), get()) }
    viewModel { GameViewModel(get(), get(), get(), get()) }
}

fun initKoin(declaration: KoinAppDeclaration = {}) = startKoin {
    declaration()
    modules(appModule)
}
