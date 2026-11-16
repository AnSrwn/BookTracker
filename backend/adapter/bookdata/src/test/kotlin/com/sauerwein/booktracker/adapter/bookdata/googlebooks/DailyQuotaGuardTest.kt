package com.sauerwein.booktracker.adapter.bookdata.googlebooks

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyQuotaGuardTest {

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    @Test
    fun `is open until tripped`() {
        assertFalse(DailyQuotaGuard(MutableClock(Instant.parse("2026-11-16T10:00:00Z"))).isClosed())
    }

    /** Google resets daily quotas at midnight Pacific time: 08:00 UTC in November (PST). */
    @Test
    fun `stays closed until the next midnight Pacific time after a daily quota`() {
        val clock = MutableClock(Instant.parse("2026-11-16T10:00:00Z"))
        val guard = DailyQuotaGuard(clock)

        assertEquals(Instant.parse("2026-11-17T08:00:00Z"), guard.trip(daily = true))

        clock.now = Instant.parse("2026-11-17T07:59:59Z")
        assertTrue(guard.isClosed())
        clock.now = Instant.parse("2026-11-17T08:00:00Z")
        assertFalse(guard.isClosed())
    }

    /** 07:30 UTC is still the previous day in California: the reset is half an hour away, not a day. */
    @Test
    fun `counts the day in Pacific time, not in UTC`() {
        val guard = DailyQuotaGuard(MutableClock(Instant.parse("2026-11-17T07:30:00Z")))

        assertEquals(Instant.parse("2026-11-17T08:00:00Z"), guard.trip(daily = true))
    }

    @Test
    fun `pauses a minute for a short-term quota`() {
        val clock = MutableClock(Instant.parse("2026-11-16T10:00:00Z"))
        val guard = DailyQuotaGuard(clock)

        guard.trip(daily = false)

        clock.now = clock.now.plus(Duration.ofSeconds(59))
        assertTrue(guard.isClosed())
        clock.now = clock.now.plus(Duration.ofSeconds(1))
        assertFalse(guard.isClosed())
    }
}
