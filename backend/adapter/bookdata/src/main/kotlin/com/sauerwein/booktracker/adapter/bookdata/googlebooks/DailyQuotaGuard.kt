package com.sauerwein.booktracker.adapter.bookdata.googlebooks

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Google resets its daily quotas at midnight Pacific time. */
private val QUOTA_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")

/** A short quota (per minute) gets a short pause; anything else waits for the daily reset. */
private val SHORT_QUOTA_PAUSE: Duration = Duration.ofSeconds(60)

/**
 * Remembers that the budget is spent, so no request is sent until it can succeed again. A
 * per-second limiter is useless against a daily budget: blocking would hang a request for hours.
 */
class DailyQuotaGuard(private val clock: Clock) {

    @Volatile
    private var closedUntil: Instant? = null

    fun isClosed(): Boolean = closedUntil?.let { Instant.now(clock).isBefore(it) } ?: false

    /** Closes the guard and returns until when. */
    fun trip(daily: Boolean): Instant {
        val now = Instant.now(clock)
        val until = if (daily) nextReset(now) else now.plus(SHORT_QUOTA_PAUSE)
        closedUntil = until
        return until
    }

    private fun nextReset(now: Instant): Instant =
        LocalDate.ofInstant(now, QUOTA_ZONE).plusDays(1).atStartOfDay(QUOTA_ZONE).toInstant()
}
