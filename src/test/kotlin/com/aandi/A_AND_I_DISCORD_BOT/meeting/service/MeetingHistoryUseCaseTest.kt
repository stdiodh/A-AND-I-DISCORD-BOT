package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.AgendaLink
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.AgendaLinkRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.MeetingSummaryExtractor
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.entity.MeetingSummaryArtifactEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.service.MeetingSummaryArtifactService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional

class MeetingHistoryUseCaseTest : FunSpec({

    val meetingSessionRepository = mockk<MeetingSessionRepository>()
    val meetingSummaryArtifactService = mockk<MeetingSummaryArtifactService>()
    val agendaLinkRepository = mockk<AgendaLinkRepository>()
    val clock = Clock.fixed(Instant.parse("2026-03-06T12:00:00Z"), ZoneOffset.UTC)

    val useCase = MeetingHistoryUseCase(
        meetingSessionRepository = meetingSessionRepository,
        meetingSummaryArtifactService = meetingSummaryArtifactService,
        agendaLinkRepository = agendaLinkRepository,
        clock = clock,
    )

    beforeTest {
        clearAllMocks()
        every { meetingSummaryArtifactService.findLatestByMeetingSessionId(any()) } returns null
        every { agendaLinkRepository.findById(any()) } returns Optional.empty()
    }

    test("내역 조회에서 일수 범위가 벗어나면 InvalidDays를 반환한다") {
        val result = useCase.listHistory(
            guildId = 1L,
            days = 0,
            statusFilter = MeetingService.HistoryStatusFilter.ALL,
        )
        result shouldBe MeetingService.HistoryResult.InvalidDays
    }

    test("내역 조회는 기간 컷오프 이후 세션만 반환한다") {
        every {
            meetingSessionRepository.findTop50ByGuildIdAndStatusOrderByStartedAtDesc(1L, MeetingSessionStatus.ACTIVE)
        } returns listOf(
            MeetingSessionEntity(
                id = 10L,
                guildId = 1L,
                threadId = 100L,
                status = MeetingSessionStatus.ACTIVE,
                startedBy = 7L,
                startedAt = Instant.parse("2026-03-05T01:00:00Z"),
            ),
            MeetingSessionEntity(
                id = 11L,
                guildId = 1L,
                threadId = 101L,
                status = MeetingSessionStatus.ACTIVE,
                startedBy = 8L,
                startedAt = Instant.parse("2025-12-01T01:00:00Z"),
            ),
        )

        val result = useCase.listHistory(
            guildId = 1L,
            days = 14,
            statusFilter = MeetingService.HistoryStatusFilter.ACTIVE,
        )

        (result is MeetingService.HistoryResult.Success) shouldBe true
        val meetings = (result as MeetingService.HistoryResult.Success).meetings
        meetings.size shouldBe 1
        meetings.first().sessionId shouldBe 10L
    }

    test("상세 조회에서 세션이 없으면 NotFound를 반환한다") {
        every { meetingSessionRepository.findByIdAndGuildId(99L, 1L) } returns null
        val result = useCase.detail(guildId = 1L, meetingSessionId = 99L)
        result shouldBe MeetingService.MeetingDetailResult.NotFound
    }

    test("상세 조회는 요약/안건 정보를 함께 반환한다") {
        every { meetingSessionRepository.findByIdAndGuildId(77L, 1L) } returns MeetingSessionEntity(
            id = 77L,
            guildId = 1L,
            threadId = 700L,
            agendaLinkId = 500L,
            status = MeetingSessionStatus.ENDED,
            startedBy = 21L,
            startedAt = Instant.parse("2026-03-06T09:00:00Z"),
            endedBy = 22L,
            endedAt = Instant.parse("2026-03-06T10:00:00Z"),
            summaryMessageId = 3333L,
        )
        every { meetingSummaryArtifactService.findLatestByMeetingSessionId(77L) } returns MeetingSummaryArtifactEntity(
            id = 9L,
            meetingSessionId = 77L,
            guildId = 1L,
            threadId = 700L,
            decisionCount = 1,
            actionCount = 1,
            todoCount = 1,
            generatedAt = Instant.parse("2026-03-06T10:01:00Z"),
            sourceWindowStart = Instant.parse("2026-03-06T09:00:00Z"),
            sourceWindowEnd = Instant.parse("2026-03-06T10:00:00Z"),
        )
        every { meetingSummaryArtifactService.toSummary(any()) } returns MeetingSummaryExtractor.MeetingSummary(
            decisions = listOf("결정 A"),
            actionItems = listOf("액션 B"),
            todos = listOf("투두 C"),
            highlights = emptyList(),
        )
        every { agendaLinkRepository.findById(500L) } returns Optional.of(
            AgendaLink(
                id = 500L,
                guildId = 1L,
                dateLocal = LocalDate.of(2026, 3, 6),
                title = "오늘 안건",
                url = "https://example.com/agenda",
                createdBy = 21L,
            ),
        )

        val result = useCase.detail(guildId = 1L, meetingSessionId = 77L)

        (result is MeetingService.MeetingDetailResult.Success) shouldBe true
        val detail = (result as MeetingService.MeetingDetailResult.Success).detail
        detail.sessionId shouldBe 77L
        detail.decisionCount shouldBe 1
        detail.actionCount shouldBe 1
        detail.todoCount shouldBe 1
        detail.agendaUrl shouldBe "https://example.com/agenda"
        detail.decisions shouldBe listOf("결정 A")
    }
})
