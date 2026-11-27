package com.sauerwein.booktracker.app

import com.sauerwein.booktracker.data.config.AppFlavor
import com.sauerwein.booktracker.data.dataModule
import com.sauerwein.booktracker.ui.feature.addbook.addBookModule
import org.koin.core.context.startKoin

/**
 * Starts dependency injection once per process: from `Application.onCreate` on Android and from the
 * `AppDelegate` on iOS. [flavorId] is the build flavor, `dev` or `prod`.
 */
fun initKoin(flavorId: String) {
    startKoin {
        modules(
            dataModule(AppFlavor.fromId(flavorId)),
            addBookModule,
        )
    }
}
