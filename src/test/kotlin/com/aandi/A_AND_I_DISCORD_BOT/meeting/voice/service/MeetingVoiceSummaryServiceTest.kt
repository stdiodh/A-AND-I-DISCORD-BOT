package com.aandi.A_AND_I_DISCORD_BOT.meeting.voice.service

import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.repository.VoiceSummaryJobRepository
import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.service.VoiceSummaryJobService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MeetingVoiceSummaryServiceTest : FunSpec({

    val meetingSessionRepository = mockk<MeetingSessionRepository>()
    val voiceSummaryJobRepository = mockk<VoiceSummaryJobRepository>()
    val voiceSummaryJobService = mockk<VoiceSummaryJobService>()
    val clock = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC)

    beforeTest {
        clearMocks(meetingSessionRepository, voiceSummaryJobRepository, voiceSummaryJobService)
    }

    fun service(enabled: Boolean): MeetingVoiceSummaryService = MeetingVoiceSummaryService(
        meetingSessionRepository = meetingSessionRepository,
        voiceSummaryJobRepository = voiceSummaryJobRepository,
        voiceSummaryJobService = voiceSummaryJobService,
        clock = clock,
        enabled = enabled,
        maxMinutes = 120,
        dataDir = "/data/voice",
    )

    test("enabled=false이면 시작 명령은 항상 차단되고 DB를 건드리지 않는다") {
        val svc = service(enabled = false)

        val result = svc.start(
            guildId = 100L,
            meetingRefId = 10L,
            voiceChannelId = 999L,
            createdBy = 111L,
        )

        (result as MeetingVoiceSummaryService.StartResult.Disabled).message shouldBe
            "현재 음성요약 기능은 비활성화 상태입니다(관리자 설정 필요)."
        verify(exactly = 0) { meetingSessionRepository.findById(any()) }
        verify(exactly = 0) { voiceSummaryJobRepository.findFirstByGuildIdAndMeetingThreadIdOrderByIdDesc(any(), any()) }
        verify(exactly = 0) { voiceSummaryJobService.createJob(any(), any(), any(), any(), any()) }
    }

    test("enabled=false이면 종료 명령은 항상 차단되고 DB를 건드리지 않는다") {
        val svc = service(enabled = false)

        val result = svc.stop(
            guildId = 100L,
            meetingRefId = 10L,
        )

        (result as MeetingVoiceSummaryService.StopResult.Disabled).message shouldBe
            "현재 음성요약 기능은 비활성화 상태입니다(관리자 설정 필요)."
        verify(exactly = 0) { meetingSessionRepository.findById(any()) }
        verify(exactly = 0) { voiceSummaryJobRepository.findFirstByGuildIdAndMeetingThreadIdOrderByIdDesc(any(), any()) }
        verify(exactly = 0) { voiceSummaryJobService.stop(any(), any()) }
    }

    test("enabled=false이면 상태 명령도 항상 차단되고 DB를 조회하지 않는다") {
        val svc = service(enabled = false)

        val result = svc.status(
            guildId = 100L,
            meetingRefId = 10L,
        )

        (result as MeetingVoiceSummaryService.StatusResult.Disabled).message shouldBe
            "현재 음성요약 기능은 비활성화 상태입니다(관리자 설정 필요)."
        verify(exactly = 0) { meetingSessionRepository.findById(any()) }
        verify(exactly = 0) { voiceSummaryJobRepository.findFirstByGuildIdAndMeetingThreadIdOrderByIdDesc(any(), any()) }
    }
})

