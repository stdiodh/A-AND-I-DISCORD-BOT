package com.aandi.A_AND_I_DISCORD_BOT.common.time

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class PeriodCalculatorTest {

    private val periodCalculator = PeriodCalculator("Asia/Seoul")

    @Test
    fun `week window starts monday and ends next monday in Asia Seoul`() {
        val now = Instant.parse("2026-02-24T03:00:00Z")

        val window = periodCalculator.currentWindow(PeriodType.WEEK, now)

        assertEquals(Instant.parse("2026-02-22T15:00:00Z"), window.startInclusive)
        assertEquals(Instant.parse("2026-03-01T15:00:00Z"), window.endExclusive)
        assertEquals(now, window.measureEndExclusive)
        assertEquals(7, window.totalDays)
    }

    @Test
    fun `month window starts first day and ends next month first day in Asia Seoul`() {
        val now = Instant.parse("2026-02-24T03:00:00Z")

        val window = periodCalculator.currentWindow(PeriodType.MONTH, now)

        assertEquals(Instant.parse("2026-01-31T15:00:00Z"), window.startInclusive)
        assertEquals(Instant.parse("2026-02-28T15:00:00Z"), window.endExclusive)
        assertEquals(now, window.measureEndExclusive)
        assertEquals(28, window.totalDays)
    }
}
