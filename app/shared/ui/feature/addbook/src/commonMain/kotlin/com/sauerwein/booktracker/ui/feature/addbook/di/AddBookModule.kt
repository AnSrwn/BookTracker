package com.sauerwein.booktracker.ui.feature.addbook.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

/** Root Koin module of the add-book feature (D45, D46). Picks up its annotated ViewModels by the scan. */
@Module
@Configuration
@ComponentScan("com.sauerwein.booktracker.ui.feature.addbook")
class AddBookModule
