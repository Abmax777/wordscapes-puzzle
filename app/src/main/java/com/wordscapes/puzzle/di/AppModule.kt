package com.wordscapes.puzzle.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Top-level singleton Hilt module.
 *
 * Day 1 additions: Context, LevelRepository, LevelJsonSource
 * Day 5 additions: ProgressRepository (DataStore)
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
