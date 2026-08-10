package com.wordscapes.puzzle.di

import com.wordscapes.puzzle.data.dictionary.DictionarySource
import com.wordscapes.puzzle.data.level.LevelRepository
import com.wordscapes.puzzle.data.progress.ProgressDataStore
import com.wordscapes.puzzle.domain.repository.LevelCatalog
import com.wordscapes.puzzle.domain.repository.ProgressStore
import com.wordscapes.puzzle.domain.repository.WordLookup
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds domain-layer interfaces to their data-layer implementations.
 *
 * @Binds rather than @Provides: there is no construction logic here, only a
 * declaration that where a WordLookup is asked for, Hilt should supply the
 * DictionarySource it already knows how to build. Dagger can then generate a
 * direct reference instead of a factory that calls another factory.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWordLookup(impl: DictionarySource): WordLookup

    @Binds
    @Singleton
    abstract fun bindLevelCatalog(impl: LevelRepository): LevelCatalog

    @Binds
    @Singleton
    abstract fun bindProgressStore(impl: ProgressDataStore): ProgressStore
}
