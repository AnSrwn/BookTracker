package com.sauerwein.booktracker.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclassesOfSealed

/** Registers all [AppNavKey]s for polymorphic serialization. Needed on iOS, where Navigation 3 has no reflection. */
val appNavSerializersModule =
    SerializersModule {
        polymorphic(NavKey::class) {
            subclassesOfSealed<AppNavKey>()
        }
    }

/** Pass to `rememberNavBackStack(appNavSavedStateConfiguration, ...)` on every platform. */
val appNavSavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule = appNavSerializersModule
    }
