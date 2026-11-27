package com.sauerwein.booktracker.core.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BookIdTest {
    @Test
    fun keepsTheValue() {
        assertEquals("3f1c", BookId("3f1c").value)
    }

    @Test
    fun rejectsABlankValue() {
        assertFailsWith<IllegalArgumentException> { BookId(" ") }
    }
}
