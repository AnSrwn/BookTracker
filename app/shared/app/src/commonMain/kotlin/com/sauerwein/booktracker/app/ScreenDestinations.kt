package com.sauerwein.booktracker.app

import com.sauerwein.booktracker.ui.common.ScreenDestination
import org.koin.core.annotation.Singleton

/**
 * Every screen of the app. Koin fills [all] with each `@Singleton` class that extends
 * [ScreenDestination], from all feature modules (D48), so no feature keeps a list.
 */
@Singleton
class ScreenDestinations(
    val all: List<ScreenDestination<*>>,
) {
    init {
        val duplicates = all.groupBy { it.keyClass }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "More than one destination for ${duplicates.map { it.simpleName }}" }
    }
}
