package com.aandi.A_AND_I_DISCORD_BOT.mogakco.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.GuildConfig
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.GuildConfigRepository
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodCalculator
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodType
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.VoiceSession
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.MogakcoChannelRepository
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.VoiceSessionRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Optional

class MogakcoStatisticsKotestTest : FunSpec({

    val guildConfigRepository = mockk<GuildConfigRepository>()
    val mogakcoChannelRepository = mockk<MogakcoChannelRepository>()
    val voiceSessionRepository = mockk<VoiceSessionRepository>()
    val permissionChecker = mockk<PermissionChecker>()
    val testClock = Clock.fixed(Instant.parse("2026-02-24T03:00:00Z"), ZoneOffset.UTC)
    val periodCalculator = PeriodCalculator("Asia/Seoul", testClock)
    val service = MogakcoService(
        guildConfigRepository = guildConfigRepository,
        mogakcoChannelRepository = mogakcoChannelRepository,
        voiceSessionRepository = voiceSessionRepository,
        periodCalculator = periodCalculator,
        permissionChecker = permissionChecker,
        clock = testClock,
    )

    beforeTest {
        clearAllMocks()
    }

    test("leaderboard-유저 A 2시간 유저 B 30분이면 A가 1위다") {
        val guildId = 101L
        val now = Instant.parse("2026-02-24T03:00:00Z")
        val window = periodCalculator.currentWindow(PeriodType.WEEK, now)
        val sessions = listOf(
            session(
                guildId = guildId,
                userId = 1L,
                joinedAt = window.startInclusive.plus(Duration.ofHours(1)),
                leftAt = window.startInclusive.plus(Duration.ofHours(3)),
            ),
            session(
                guildId = guildId,
                userId = 2L,
                joinedAt = window.startInclusive.plus(Duration.ofHours(2)),
                leftAt = window.startInclusive.plus(Duration.ofHours(2).plus(Duration.ofMinutes(30))),
            ),
        )

        every {
            voiceSessionRepository.findSessionsInRange(guildId, window.startInclusive, window.measureEndExclusive)
        } returns sessions

        val result = service.getLeaderboard(guildId = guildId, period = PeriodType.WEEK, top = 10, now = now)

        result.entries.size shouldBe 2
        result.entries[0].userId shouldBe 1L
        result.entries[0].totalSeconds shouldBe 7200L
        result.entries[1].userId shouldBe 2L
        result.entries[1].totalSeconds shouldBe 1800L
    }

    test("leaderboard-기간 경계를 걸친 세션은 겹치는 구간만 합산된다") {
        val guildId = 202L
        val now = Instant.parse("2026-02-24T03:00:00Z")
        val window = periodCalculator.currentWindow(PeriodType.WEEK, now)
        val sessions = listOf(
            session(
                guildId = guildId,
                userId = 10L,
                joinedAt = window.startInclusive.minus(Duration.ofMinutes(30)),
                leftAt = window.startInclusive.plus(Duration.ofMinutes(30)),
            ),
            session(
                guildId = guildId,
                userId = 10L,
                joinedAt = window.measureEndExclusive.minus(Duration.ofMinutes(10)),
                leftAt = window.measureEndExclusive.plus(Duration.ofMinutes(50)),
            ),
        )

        every {
            voiceSessionRepository.findSessionsInRange(guildId, window.startInclusive, window.measureEndExclusive)
        } returns sessions

        val result = service.getLeaderboard(guildId = guildId, period = PeriodType.WEEK, top = 10, now = now)

        result.entries.size shouldBe 1
        result.entries.first().userId shouldBe 10L
        result.entries.first().totalSeconds shouldBe 2400L
    }

    test("leaderboard-데이터가 없으면 빈 목록을 반환한다") {
        val guildId = 303L
        val now = Instant.parse("2026-02-24T03:00:00Z")
        val window = periodCalculator.currentWindow(PeriodType.WEEK, now)

        every {
            voiceSessionRepository.findSessionsInRange(guildId, window.startInclusive, window.measureEndExclusive)
        } returns emptyList()

        val result = service.getLeaderboard(guildId = guildId, period = PeriodType.WEEK, top = 10, now = now)

        result.entries shouldBe emptyList()
    }

    test("참여율-하루 누적이 기준 이상이면 참여일로 카운트되고 미만이면 제외된다") {
        val guildId = 404L
        val userId = 20L
        val now = Instant.parse("2026-02-11T03:00:00Z")
        val window = periodCalculator.currentWindow(PeriodType.MONTH, now)
        val zoneId = ZoneId.of("Asia/Seoul")
        val dayOneStart = ZonedDateTime.of(2026, 2, 10, 10, 0, 0, 0, zoneId).toInstant()
        val dayTwoStart = ZonedDateTime.of(2026, 2, 11, 11, 0, 0, 0, zoneId).toInstant()
        val sessions = listOf(
            session(
                guildId = guildId,
                userId = userId,
                joinedAt = dayOneStart,
                leftAt = dayOneStart.plus(Duration.ofMinutes(35)),
            ),
            session(
                guildId = guildId,
                userId = userId,
                joinedAt = dayTwoStart,
                leftAt = dayTwoStart.plus(Duration.ofMinutes(20)),
            ),
        )

        every {
            voiceSessionRepository.findSessionsInRange(guildId, window.startInclusive, window.measureEndExclusive)
        } returns sessions
        every { guildConfigRepository.findById(guildId) } returns Optional.of(
            GuildConfig(
                guildId = guildId,
                mogakcoActiveMinutes = 30,
            ),
        )

        val result = service.getMyStats(guildId = guildId, userId = userId, period = PeriodType.MONTH, now = now)

        result.totalSeconds shouldBe 3300L
        result.activeDays shouldBe 1
        result.totalDays shouldBe window.totalDays
        result.participationRate shouldBe (1.0 / window.totalDays.toDouble()) plusOrMinus 0.000001
    }

    test("참여율-자정을 넘는 세션은 날짜 분할 정책대로 참여일을 계산한다") {
        val guildId = 505L
        val userId = 30L
        val now = Instant.parse("2026-02-11T03:00:00Z")
        val window = periodCalculator.currentWindow(PeriodType.MONTH, now)
        val zoneId = ZoneId.of("Asia/Seoul")
        val joinedAt = ZonedDateTime.of(2026, 2, 10, 23, 50, 0, 0, zoneId).toInstant()
        val leftAt = ZonedDateTime.of(2026, 2, 11, 0, 30, 0, 0, zoneId).toInstant()
        val sessions = listOf(
            session(
                guildId = guildId,
                userId = userId,
                joinedAt = joinedAt,
                leftAt = leftAt,
            ),
        )

        every {
            voiceSessionRepository.findSessionsInRange(guildId, window.startInclusive, window.measureEndExclusive)
        } returns sessions
        every { guildConfigRepository.findById(guildId) } returns Optional.of(
            GuildConfig(
                guildId = guildId,
                mogakcoActiveMinutes = 30,
            ),
        )

        val result = service.getMyStats(guildId = guildId, userId = userId, period = PeriodType.MONTH, now = now)

        result.totalSeconds shouldBe 2400L
        result.activeDays shouldBe 1
    }

    test("참여율-23시50분부터 00시10분 세션은 기준 30분에서 참여일 0일이다") {
        val guildId = 506L
        val userId = 31L
        val now = Instant.parse("2026-02-11T03:00:00Z")
        val window = periodCalculator.currentWindow(PeriodType.MONTH, now)
        val zoneId = ZoneId.of("Asia/Seoul")
        val joinedAt = ZonedDateTime.of(2026, 2, 10, 23, 50, 0, 0, zoneId).toInstant()
        val leftAt = ZonedDateTime.of(2026, 2, 11, 0, 10, 0, 0, zoneId).toInstant()
        val sessions = listOf(
            session(
                guildId = guildId,
                userId = userId,
                joinedAt = joinedAt,
                leftAt = leftAt,
            ),
        )

        every {
            voiceSessionRepository.findSessionsInRange(guildId, window.startInclusive, window.measureEndExclusive)
        } returns sessions
        every { guildConfigRepository.findById(guildId) } returns Optional.of(
            GuildConfig(
                guildId = guildId,
                mogakcoActiveMinutes = 30,
            ),
        )

        val result = service.getMyStats(guildId = guildId, userId = userId, period = PeriodType.MONTH, now = now)

        result.totalSeconds shouldBe 1200L
        result.activeDays shouldBe 0
    }
}) {
    companion object {
        private fun session(
            guildId: Long,
            userId: Long,
            joinedAt: Instant,
            leftAt: Instant,
        ): VoiceSession = VoiceSession(
            guildId = guildId,
            userId = userId,
            channelId = 999L,
            joinedAt = joinedAt,
            leftAt = leftAt,
        )
    }
}
