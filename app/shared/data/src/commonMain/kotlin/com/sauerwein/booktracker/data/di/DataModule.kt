package com.sauerwein.booktracker.data.di

import com.sauerwein.booktracker.data.config.AppEnvironment
import com.sauerwein.booktracker.data.config.AppFlavor
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Singleton

/** Root Koin module of shared/data (D45, D46). Annotated classes in the data packages are found by the scan. */
@Module
@Configuration
@ComponentScan("com.sauerwein.booktracker.data")
class DataModule {
    /** [flavor] is declared at start from the build flavor (`initKoin`), not built by a definition. */
    @Singleton
    fun environment(
        @Provided flavor: AppFlavor,
    ): AppEnvironment = flavor.defaultEnvironment
}
