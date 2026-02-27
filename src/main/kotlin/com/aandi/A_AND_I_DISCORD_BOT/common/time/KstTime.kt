package com.aandi.A_AND_I_DISCORD_BOT.common.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

object KstTime {

    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul")
    private val parseFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm")
        .optionalStart()
        .appendLiteral(':')
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .optionalEnd()
        .toFormatter()
    private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun parse(raw: String): Instant {
        val localDateTime = LocalDateTime.parse(raw.trim(), parseFormatter)
        return localDateTime.atZone(zoneId).toInstant()
    }

    fun parseKstToInstant(raw: String): Instant = parse(raw)

    fun format(instant: Instant): String = instant.atZone(zoneId).format(displayFormatter)

    fun formatInstantToKst(instant: Instant): String = format(instant)
}
