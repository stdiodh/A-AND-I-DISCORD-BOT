package com.aandi.A_AND_I_DISCORD_BOT.mogakco.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.GuildConfig
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.GuildConfigRepository
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodCalculator
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodType
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.VoiceSession
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.MogakcoChannelRepository
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.VoiceSessionRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Optional
import kotlin.test.assertEquals

class MogakcoServiceStatisticsTest {

    private val guildConfigRepository: GuildConfigRepository = Mockito.mock(GuildConfigRepository::class.java)
    private val mogakcoChannelRepository: MogakcoChannelRepository = Mockito.mock(MogakcoChannelRepository::class.java)
    private val voiceSessionRepository: VoiceSessionRepository = Mockito.mock(VoiceSessionRepository::class.java)
    private val permissionChecker: PermissionChecker = Mockito.mock(PermissionChecker::class.java)
    private val periodCalculator = PeriodCalculator("Asia/Seoul")

    private val service = MogakcoService(
        guildConfigRepository = guildConfigRepository,
        mogakcoChannelRepository = mogakcoChannelRepository,
        voiceSessionRepository = voiceSessionRepository,
        periodCalculator = periodCalculator,
        permissionChecker = permissionChecker,
    )

    @Test
    fun `leaderboard sums only overlapped duration when sessions cross period boundaries`() {
        val guildId = 100L
        val now = Instant.parse("2026-02-24T03:00:00Z")
        val window = periodCalculator.currentWindow(PeriodType.WEEK, now)
        val sessions = listOf(
            VoiceSession(
                guildId = guildId,
                userId = 101L,
                channelId = 1001L,
                joinedAt = window.startInclusive.minus(Duration.ofHours(1)),
                leftAt = window.startInclusive.plus(Duration.ofMinutes(30)),
            ),
            VoiceSession(
                guildId = guildId,
                userId = 101L,
                channelId = 1001L,
                joinedAt = window.startInclusive.plus(Duration.ofHours(1)),
                leftAt = window.startInclusive.plus(Duration.ofHours(2)),
            ),
            VoiceSession(
                guildId = guildId,
                userId = 202L,
                channelId = 1001L,
                joinedAt = window.measureEndExclusive.minus(Duration.ofMinutes(15)),
                leftAt = window.measureEndExclusive.plus(Duration.ofMinutes(30)),
            ),
        )

        Mockito.`when`(
            voiceSessionRepository.findSessionsInRange(
                guildId,
                window.startInclusive,
                window.measureEndExclusive,
            ),
        ).thenReturn(sessions)

        val result = service.getLeaderboard(guildId = guildId, period = PeriodType.WEEK, top = 10, now = now)

        assertEquals(2, result.entries.size)
        assertEquals(101L, result.entries[0].userId)
        assertEquals(5400L, result.entries[0].totalSeconds)
        assertEquals(202L, result.entries[1].userId)
        assertEquals(900L, result.entries[1].totalSeconds)
    }

    @Test
    fun `my stats splits daily duration by Asia Seoul boundary for active day calculation`() {
        val guildId = 200L
        val userId = 333L
        val now = Instant.parse("2026-02-11T01:00:00Z")
        val window = periodCalculator.currentWindow(PeriodType.MONTH, now)
        val zoneId = ZoneId.of("Asia/Seoul")
        val joinedAt = ZonedDateTime.of(2026, 2, 10, 23, 50, 0, 0, zoneId).toInstant()
        val leftAt = ZonedDateTime.of(2026, 2, 11, 0, 20, 0, 0, zoneId).toInstant()
        val sessions = listOf(
            VoiceSession(
                guildId = guildId,
                userId = userId,
                channelId = 2001L,
                joinedAt = joinedAt,
                leftAt = leftAt,
            ),
        )

        Mockito.`when`(
            voiceSessionRepository.findSessionsInRange(
                guildId,
                window.startInclusive,
                window.measureEndExclusive,
            ),
        ).thenReturn(sessions)
        Mockito.`when`(guildConfigRepository.findById(guildId)).thenReturn(
            Optional.of(
                GuildConfig(
                    guildId = guildId,
                    mogakcoActiveMinutes = 30,
                ),
            ),
        )

        val result = service.getMyStats(guildId = guildId, userId = userId, period = PeriodType.MONTH, now = now)

        assertEquals(1800L, result.totalSeconds)
        assertEquals(0, result.activeDays)
        assertEquals(window.totalDays, result.totalDays)
        assertEquals(0.0, result.participationRate)
    }
}
