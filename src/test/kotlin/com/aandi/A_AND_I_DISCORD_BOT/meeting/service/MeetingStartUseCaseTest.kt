package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.AgendaLinkRepository
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodCalculator
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MeetingStartUseCaseTest : FunSpec({

    val guildConfigService = mockk<GuildConfigService>()
    val agendaLinkRepository = mockk<AgendaLinkRepository>()
    val periodCalculator = mockk<PeriodCalculator>()
    val meetingSessionRepository = mockk<MeetingSessionRepository>()
    val meetingThreadGateway = mockk<MeetingThreadGateway>()
    val clock = Clock.fixed(Instant.parse("2026-03-06T12:00:00Z"), ZoneOffset.UTC)

    val useCase = MeetingStartUseCase(
        guildConfigService = guildConfigService,
        agendaLinkRepository = agendaLinkRepository,
        periodCalculator = periodCalculator,
        meetingSessionRepository = meetingSessionRepository,
        meetingThreadGateway = meetingThreadGateway,
        clock = clock,
    )

    beforeTest {
        clearAllMocks()
        every { guildConfigService.getBoardChannels(any()) } returns GuildConfigService.BoardChannelConfig(
            meetingChannelId = null,
            mogakcoChannelId = null,
            assignmentChannelId = null,
        )
        every { guildConfigService.getDashboard(any()) } returns GuildConfigService.DashboardConfig(
            channelId = null,
            messageId = null,
        )
        every { periodCalculator.today(any()) } returns LocalDate.of(2026, 3, 6)
        every { agendaLinkRepository.findByGuildIdAndDateLocal(any(), any()) } returns null
        every { meetingThreadGateway.postMeetingTemplate(any(), any(), any()) } just runs
    }

    test("같은 채널에 ACTIVE 회의가 있으면 시작을 차단한다") {
        val guildId = 1L
        val channelId = 100L
        val activeThread = mockk<ThreadChannel>()
        every { meetingThreadGateway.findThreadChannel(999L) } returns activeThread
        every {
            meetingSessionRepository.findFirstByGuildIdAndBoardChannelIdAndStatusOrderByStartedAtDesc(
                guildId,
                channelId,
                MeetingSessionStatus.ACTIVE,
            )
        } returns MeetingSessionEntity(
            id = 10L,
            guildId = guildId,
            threadId = 999L,
            boardChannelId = channelId,
            status = MeetingSessionStatus.ACTIVE,
        )

        val result = useCase.startMeeting(
            guildId = guildId,
            requestedBy = 11L,
            targetChannelId = channelId,
            fallbackChannelId = null,
            rawTitle = null,
        )

        (result is MeetingService.StartResult.AlreadyActive) shouldBe true
        (result as MeetingService.StartResult.AlreadyActive).threadId shouldBe 999L
    }

    test("다른 채널이면 길드에 ACTIVE가 있어도 회의를 시작할 수 있다") {
        val guildId = 2L
        val requestedBy = 21L
        val channelA = 100L
        val channelB = 200L
        val textChannel = mockk<TextChannel>()
        val startMessage = mockk<Message>()
        val thread = mockk<ThreadChannel>()
        val savedSession = slot<MeetingSessionEntity>()

        every {
            meetingSessionRepository.findFirstByGuildIdAndBoardChannelIdAndStatusOrderByStartedAtDesc(
                guildId,
                channelB,
                MeetingSessionStatus.ACTIVE,
            )
        } returns null
        every { meetingThreadGateway.findTextChannel(channelB) } returns textChannel
        every { meetingThreadGateway.createStartMessage(textChannel, requestedBy, any()) } returns startMessage
        every { meetingThreadGateway.createThread(startMessage, any()) } returns thread
        every { thread.idLong } returns 2026L
        every { thread.name } returns "2026-03-06 회의"
        every { meetingSessionRepository.save(capture(savedSession)) } answers {
            savedSession.captured.apply { id = 77L }
        }

        val result = useCase.startMeeting(
            guildId = guildId,
            requestedBy = requestedBy,
            targetChannelId = channelB,
            fallbackChannelId = channelA,
            rawTitle = null,
        )

        (result is MeetingService.StartResult.Success) shouldBe true
        (result as MeetingService.StartResult.Success).sessionId shouldBe 77L
        savedSession.captured.boardChannelId shouldBe channelB
        verify(exactly = 1) {
            meetingSessionRepository.findFirstByGuildIdAndBoardChannelIdAndStatusOrderByStartedAtDesc(
                guildId,
                channelB,
                MeetingSessionStatus.ACTIVE,
            )
        }
    }
})
