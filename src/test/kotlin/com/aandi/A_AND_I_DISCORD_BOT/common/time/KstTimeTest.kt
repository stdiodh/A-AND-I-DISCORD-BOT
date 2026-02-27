package com.aandi.A_AND_I_DISCORD_BOT.common.time

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class KstTimeTest {

    @Test
    fun `parseKstToInstant converts KST string to UTC instant`() {
        val parsed = KstTime.parseKstToInstant("2026-03-01 21:30")

        assertEquals(Instant.parse("2026-03-01T12:30:00Z"), parsed)
    }

    @Test
    fun `parse supports optional seconds`() {
        val parsed = KstTime.parse("2026-03-01 21:30:45")

        assertEquals(Instant.parse("2026-03-01T12:30:45Z"), parsed)
    }

    @Test
    fun `formatInstantToKst converts UTC instant to KST display text`() {
        val formatted = KstTime.formatInstantToKst(Instant.parse("2026-03-01T12:30:45Z"))

        assertEquals("2026-03-01 21:30", formatted)
    }
}
