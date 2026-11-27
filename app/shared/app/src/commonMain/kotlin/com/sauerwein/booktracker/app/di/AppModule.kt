package com.sauerwein.booktracker.app.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

/** Root Koin module of shared/app (D45, D46): its own definitions, e.g. [com.sauerwein.booktracker.app.ScreenDestinations]. */
@Module
@Configuration
@ComponentScan("com.sauerwein.booktracker.app")
class AppModule
