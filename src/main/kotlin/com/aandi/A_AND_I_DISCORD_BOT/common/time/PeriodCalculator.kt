package com.aandi.A_AND_I_DISCORD_BOT.common.time

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Component
class PeriodCalculator(
    @Value("\${app.timezone:Asia/Seoul}") timezone: String,
) {

    val zoneId: ZoneId = ZoneId.of(timezone)

    fun today(now: Instant = Instant.now()): LocalDate = now.atZone(zoneId).toLocalDate()

    fun currentWindow(periodType: PeriodType, now: Instant = Instant.now()): PeriodWindow {
        val nowZoned = now.atZone(zoneId)
        val startDate = startDate(periodType, nowZoned.toLocalDate())
        val endDate = endDate(periodType, startDate)
        val startInclusive = startDate.atStartOfDay(zoneId).toInstant()
        val endExclusive = endDate.atStartOfDay(zoneId).toInstant()
        val measureEndExclusive = minOf(endExclusive, now)
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt()

        return PeriodWindow(
            startInclusive = startInclusive,
            endExclusive = endExclusive,
            measureEndExclusive = measureEndExclusive,
            totalDays = totalDays,
        )
    }

    private fun startDate(periodType: PeriodType, date: LocalDate): LocalDate = when (periodType) {
        PeriodType.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        PeriodType.MONTH -> date.withDayOfMonth(1)
    }

    private fun endDate(periodType: PeriodType, startDate: LocalDate): LocalDate = when (periodType) {
        PeriodType.WEEK -> startDate.plusWeeks(1)
        PeriodType.MONTH -> startDate.plusMonths(1)
    }
}

enum class PeriodType {
    WEEK,
    MONTH,
}

data class PeriodWindow(
    val startInclusive: Instant,
    val endExclusive: Instant,
    val measureEndExclusive: Instant,
    val totalDays: Int,
)
