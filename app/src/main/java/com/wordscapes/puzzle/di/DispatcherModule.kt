package com.wordscapes.puzzle.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Dispatchers are injected rather than referenced as `Dispatchers.IO` directly
 * at the call site. The reason is testability: a unit test can bind an
 * immediate dispatcher and get deterministic ordering, which is not possible
 * if the class reaches for the global object itself.
 *
 * A qualifier is required because `CoroutineDispatcher` is a common type —
 * without one, Hilt cannot tell an IO dispatcher from a Default dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    /** Blocking file I/O: reading assets. */
    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /** CPU-bound work: anagram checks, grid derivation. */
    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
