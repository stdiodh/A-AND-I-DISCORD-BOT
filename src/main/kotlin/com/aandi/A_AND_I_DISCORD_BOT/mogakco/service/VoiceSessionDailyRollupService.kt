package com.aandi.A_AND_I_DISCORD_BOT.mogakco.service

import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodCalculator
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.VoiceSessionDailyRollupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class VoiceSessionDailyRollupService(
    private val voiceSessionDailyRollupRepository: VoiceSessionDailyRollupRepository,
    private val periodCalculator: PeriodCalculator,
) {

    @Transactional
    fun accumulate(
        guildId: Long,
        userId: Long,
        startInclusive: Instant,
        endExclusive: Instant,
    ) {
        if (!endExclusive.isAfter(startInclusive)) {
            return
        }
        var cursor = startInclusive
        while (cursor.isBefore(endExclusive)) {
            val date = cursor.atZone(periodCalculator.zoneId).toLocalDate()
            val nextDateStart = date.plusDays(1).atStartOfDay(periodCalculator.zoneId).toInstant()
            val segmentEnd = minOf(nextDateStart, endExclusive)
            val seconds = Duration.between(cursor, segmentEnd).seconds
            if (seconds > 0) {
                voiceSessionDailyRollupRepository.upsertAddSeconds(
                    guildId = guildId,
                    userId = userId,
                    dateLocal = date,
                    seconds = seconds,
                )
            }
            cursor = segmentEnd
        }
    }
}
