package com.wordscapes.puzzle.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Top-level singleton module. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
