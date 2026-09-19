package com.sauerwein.booktracker.core.book.models

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IsbnTest {

    @Test
    fun `accepts a valid ISBN-13 and removes hyphens`() {
        val isbn = Isbn.of("978-0-306-40615-7")

        assertEquals("9780306406157", isbn.value)
    }

    @Test
    fun `converts a valid ISBN-10 to ISBN-13`() {
        val isbn = Isbn.of("0-306-40615-2")

        assertEquals("9780306406157", isbn.value)
    }

    @Test
    fun `accepts an ISBN-10 with check digit X`() {
        val isbn = Isbn.of("080442957X")

        assertEquals("9780804429573", isbn.value)
    }

    @Test
    fun `accepts an ISBN-10 with check digit x`() {
        val isbn = Isbn.of("080442957x")

        assertEquals("9780804429573", isbn.value)
    }

    @Test
    fun `rejects an ISBN-13 with a wrong check digit`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Isbn.of("978-0-306-40615-8")
        }

        assertContains(exception.message.orEmpty(), "check digit")
    }

    @Test
    fun `accepts an ISBN-13 with prefix 979`() {
        val isbn = Isbn.of("979-10-90636-07-1")

        assertEquals("9791090636071", isbn.value)
    }

    @Test
    fun `removes whitespace`() {
        val isbn = Isbn.of(" 978 0306406157 ")

        assertEquals("9780306406157", isbn.value)
    }

    @Test
    fun `rejects an ISBN-13 without prefix 978 or 979`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Isbn.of("1234567890128")
        }

        assertContains(exception.message.orEmpty(), "978 or 979")
    }

    @Test
    fun `rejects an ISBN-10 with a wrong check digit`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Isbn.of("0306406153")
        }

        assertContains(exception.message.orEmpty(), "ISBN-10 check digit")
    }

    @Test
    fun `rejects a value with a wrong length`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Isbn.of("97803064061")
        }

        assertContains(exception.message.orEmpty(), "got 11")
    }

    @Test
    fun `rejects an empty value`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Isbn.of("")
        }

        assertContains(exception.message.orEmpty(), "got 0")
    }

    @Test
    fun `rejects an ISBN-10 with X in a position other than the last`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Isbn.of("X306406152")
        }

        assertContains(exception.message.orEmpty(), "trailing X")
    }

    @Test
    fun `rejects an ISBN-13 containing letters`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Isbn.of("97803064061X7")
        }

        assertContains(exception.message.orEmpty(), "may only contain digits")
    }

    @Test
    fun `rejects non-ASCII digits`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Isbn.of("٩٧٨0306406157")
        }

        assertContains(exception.message.orEmpty(), "may only contain digits")
    }
}
