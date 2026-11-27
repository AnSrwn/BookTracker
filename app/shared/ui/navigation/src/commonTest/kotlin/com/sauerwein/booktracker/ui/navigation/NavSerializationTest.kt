package com.sauerwein.booktracker.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NavSerializationTest {
    private val keys: List<NavKey> =
        listOf(BookshelfKey, BookDetailKey("b-1"), AddBookKey, ManualBookKey, AccountKey, AboutKey)

    @Test
    fun everyKeyIsRegisteredForSavingAndRestoring() {
        keys.forEach { key ->
            val serializer = assertNotNull(appNavSerializersModule.getPolymorphic(NavKey::class, key), "no serializer to save $key")
            val restoring = appNavSerializersModule.getPolymorphic(NavKey::class, serializer.descriptor.serialName)
            assertNotNull(restoring, "no serializer to restore $key")
        }
    }

    @Test
    fun everyTabStartsAtItsOwnRoot() {
        assertEquals(listOf(BookshelfKey, AddBookKey, AccountKey), AppTab.entries.map { it.rootKey })
    }
}
