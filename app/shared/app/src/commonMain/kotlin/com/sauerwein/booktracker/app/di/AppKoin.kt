package com.sauerwein.booktracker.app.di

import com.sauerwein.booktracker.data.config.AppFlavor
import org.koin.core.annotation.KoinApplication
import org.koin.plugin.module.dsl.startKoin

/**
 * The Koin application (D46). The Compiler Plugin finds every root module tagged `@Configuration`
 * on the classpath and checks the whole graph at build time, so there is no `modules(...)` list.
 */
@KoinApplication
class BookTrackerKoinApp

/**
 * Starts dependency injection once per process: from `Application.onCreate` on Android and from the
 * `AppDelegate` on iOS. [flavorId] is the build flavor, `dev` or `prod`; it becomes the [AppFlavor]
 * that `@Provided` definitions receive.
 */
fun initKoin(flavorId: String) {
    startKoin<BookTrackerKoinApp> {
        koin.declare(AppFlavor.fromId(flavorId))
    }
}
