package com.sauerwein.booktracker.data

import com.sauerwein.booktracker.data.config.AppEnvironment
import com.sauerwein.booktracker.data.config.AppFlavor
import org.koin.dsl.module

fun dataModule(flavor: AppFlavor) =
    module {
        single { flavor }
        single<AppEnvironment> { flavor.defaultEnvironment }
    }
