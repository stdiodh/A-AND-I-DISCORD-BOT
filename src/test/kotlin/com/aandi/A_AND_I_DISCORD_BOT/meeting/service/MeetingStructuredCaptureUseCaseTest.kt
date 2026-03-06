package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.structured.entity.MeetingStructuredItemEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.structured.service.MeetingStructuredItemService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel

class MeetingStructuredCaptureUseCaseTest : FunSpec({

    val meetingSessionRepository = mockk<MeetingSessionRepository>()
    val meetingStructuredItemService = mockk<MeetingStructuredItemService>()
    val meetingThreadGateway = mockk<MeetingThreadGateway>()
    val useCase = MeetingStructuredCaptureUseCase(
        meetingSessionRepository = meetingSessionRepository,
        meetingStructuredItemService = meetingStructuredItemService,
        meetingThreadGateway = meetingThreadGateway,
    )

    beforeTest {
        clearAllMocks()
    }

    test("스레드 외 채널에서 회의아이디가 없으면 MeetingIdRequired를 반환한다") {
        val result = useCase.captureDecision(
            guildId = 1L,
            requestedBy = 2L,
            fallbackThreadId = null,
            requestedMeetingSessionId = null,
            content = "결정: 배포 일정 확정",
        )

        result shouldBe MeetingService.StructuredCaptureResult.MeetingIdRequired
    }

    test("요청한 회의아이디가 ENDED 상태면 MeetingNotActive를 반환한다") {
        every { meetingSessionRepository.findByIdAndGuildId(10L, 1L) } returns MeetingSessionEntity(
            id = 10L,
            guildId = 1L,
            threadId = 101L,
            status = MeetingSessionStatus.ENDED,
        )

        val result = useCase.captureTodo(
            guildId = 1L,
            requestedBy = 2L,
            fallbackThreadId = null,
            requestedMeetingSessionId = 10L,
            content = "TODO: 예외 케이스 점검",
        )

        result shouldBe MeetingService.StructuredCaptureResult.MeetingNotActive
    }

    test("스레드 외 채널에서도 회의아이디를 주면 해당 세션에 기록한다") {
        val activeSession = MeetingSessionEntity(
            id = 33L,
            guildId = 1L,
            threadId = 303L,
            status = MeetingSessionStatus.ACTIVE,
        )
        val thread = mockk<ThreadChannel>()
        every { thread.idLong } returns 303L
        every { meetingSessionRepository.findByIdAndGuildId(33L, 1L) } returns activeSession
        every { meetingThreadGateway.findThreadChannel(303L) } returns thread
        every {
            meetingStructuredItemService.addDecision(
                meetingSessionId = 33L,
                guildId = 1L,
                threadId = 303L,
                content = "결정: 다음주 릴리즈",
                createdBy = 2L,
            )
        } returns MeetingStructuredItemEntity(
            id = 501L,
            meetingSessionId = 33L,
            guildId = 1L,
            threadId = 303L,
            content = "결정: 다음주 릴리즈",
        )
        every { meetingThreadGateway.postStructuredCaptureNotice(thread, any()) } just runs

        val result = useCase.captureDecision(
            guildId = 1L,
            requestedBy = 2L,
            fallbackThreadId = null,
            requestedMeetingSessionId = 33L,
            content = "결정: 다음주 릴리즈",
        )

        (result is MeetingService.StructuredCaptureResult.Success) shouldBe true
        (result as MeetingService.StructuredCaptureResult.Success).sessionId shouldBe 33L
        (result as MeetingService.StructuredCaptureResult.Success).threadId shouldBe 303L
        verify(exactly = 1) {
            meetingStructuredItemService.addDecision(
                meetingSessionId = 33L,
                guildId = 1L,
                threadId = 303L,
                content = "결정: 다음주 릴리즈",
                createdBy = 2L,
            )
        }
    }
})
