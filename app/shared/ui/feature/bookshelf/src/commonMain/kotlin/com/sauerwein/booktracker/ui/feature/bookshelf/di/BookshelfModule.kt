package com.sauerwein.booktracker.ui.feature.bookshelf.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

/** Root Koin module of the bookshelf feature (D45, D46). The scan picks up its destinations and ViewModels. */
@Module
@Configuration
@ComponentScan("com.sauerwein.booktracker.ui.feature.bookshelf")
class BookshelfModule
