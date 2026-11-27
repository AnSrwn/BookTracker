package com.sauerwein.booktracker.data.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppFlavorTest {
    @Test
    fun mapsTheBuildFlavorIds() {
        assertEquals(AppFlavor.DEV, AppFlavor.fromId("dev"))
        assertEquals(AppFlavor.PROD, AppFlavor.fromId("prod"))
    }

    @Test
    fun rejectsAnUnknownId() {
        assertFailsWith<IllegalArgumentException> { AppFlavor.fromId("staging") }
    }

    @Test
    fun eachFlavorStartsOnItsOwnBackend() {
        assertEquals(AppEnvironment.DEV, AppFlavor.DEV.defaultEnvironment)
        assertEquals(AppEnvironment.PROD, AppFlavor.PROD.defaultEnvironment)
    }

    @Test
    fun devAndProdAppUseDifferentRedirectSchemes() {
        assertEquals("booktracker-dev://auth/callback", AppFlavor.DEV.authRedirectUri)
        assertEquals("booktracker://auth/callback", AppFlavor.PROD.authRedirectUri)
    }
}
