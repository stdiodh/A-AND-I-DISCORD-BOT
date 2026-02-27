package com.aandi.A_AND_I_DISCORD_BOT.voice.summary.service

import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.entity.VoiceSummaryJobEntity
import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.entity.VoiceSummaryStatus
import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.repository.VoiceSummaryJobRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.Optional

class VoiceSummaryJobServiceTest : FunSpec({

    val repository = mockk<VoiceSummaryJobRepository>()
    val service = VoiceSummaryJobService(repository)
    val now = Instant.parse("2026-03-01T12:00:00Z")

    beforeTest {
        clearMocks(repository)
    }

    test("createJob-READY 상태로 생성한다") {
        val saved = slot<VoiceSummaryJobEntity>()
        every { repository.save(capture(saved)) } answers {
            saved.captured.apply { id = 1L }
        }

        val result = service.createJob(
            guildId = 100L,
            voiceChannelId = 200L,
            meetingThreadId = 300L,
            createdBy = 999L,
            nowUtc = now,
        )

        result.id shouldBe 1L
        result.status shouldBe VoiceSummaryStatus.READY
        result.maxMinutes shouldBe 120
        verify(exactly = 1) { repository.save(any()) }
    }

    test("start-READY에서 RECORDING으로 전이한다") {
        val job = VoiceSummaryJobEntity(
            id = 10L,
            guildId = 100L,
            voiceChannelId = 200L,
            status = VoiceSummaryStatus.READY,
            dataDir = "/data/voice/job-10",
            createdBy = 999L,
            createdAt = now,
            updatedAt = now,
        )
        every { repository.findById(10L) } returns Optional.of(job)
        every { repository.save(job) } returns job

        val result = service.start(10L, now)

        (result as VoiceSummaryJobService.TransitionResult.Success).job.status shouldBe VoiceSummaryStatus.RECORDING
        result.job.startedAt shouldBe now
    }

    test("start-중복 시작을 방지한다(RECORDING 상태)") {
        val job = VoiceSummaryJobEntity(
            id = 11L,
            guildId = 100L,
            voiceChannelId = 200L,
            status = VoiceSummaryStatus.RECORDING,
            dataDir = "/data/voice/job-11",
            createdBy = 999L,
            createdAt = now,
            updatedAt = now,
        )
        every { repository.findById(11L) } returns Optional.of(job)

        val result = service.start(11L, now)

        (result as VoiceSummaryJobService.TransitionResult.InvalidState).job.status shouldBe VoiceSummaryStatus.RECORDING
        verify(exactly = 0) { repository.save(any()) }
    }

    test("stop-RECORDING이 아닌 상태에서는 실패한다") {
        val job = VoiceSummaryJobEntity(
            id = 12L,
            guildId = 100L,
            voiceChannelId = 200L,
            status = VoiceSummaryStatus.READY,
            dataDir = "/data/voice/job-12",
            createdBy = 999L,
            createdAt = now,
            updatedAt = now,
        )
        every { repository.findById(12L) } returns Optional.of(job)

        val result = service.stop(12L, now)

        (result as VoiceSummaryJobService.TransitionResult.InvalidState).job.status shouldBe VoiceSummaryStatus.READY
        verify(exactly = 0) { repository.save(any()) }
    }

    test("stop-RECORDING에서 RECORDED로 전이한다") {
        val job = VoiceSummaryJobEntity(
            id = 13L,
            guildId = 100L,
            voiceChannelId = 200L,
            status = VoiceSummaryStatus.RECORDING,
            dataDir = "/data/voice/job-13",
            createdBy = 999L,
            createdAt = now,
            updatedAt = now,
        )
        every { repository.findById(13L) } returns Optional.of(job)
        every { repository.save(job) } returns job

        val result = service.stop(13L, now)

        (result as VoiceSummaryJobService.TransitionResult.Success).job.status shouldBe VoiceSummaryStatus.RECORDED
        result.job.endedAt shouldBe now
    }

    test("DISABLED 상태에서는 start와 stop 모두 실패한다") {
        val job = VoiceSummaryJobEntity(
            id = 14L,
            guildId = 100L,
            voiceChannelId = 200L,
            status = VoiceSummaryStatus.DISABLED,
            dataDir = "/data/voice/job-14",
            createdBy = 999L,
            createdAt = now,
            updatedAt = now,
        )
        every { repository.findById(14L) } returns Optional.of(job)

        val startResult = service.start(14L, now)
        val stopResult = service.stop(14L, now)

        (startResult as VoiceSummaryJobService.TransitionResult.InvalidState).job.status shouldBe VoiceSummaryStatus.DISABLED
        (stopResult as VoiceSummaryJobService.TransitionResult.InvalidState).job.status shouldBe VoiceSummaryStatus.DISABLED
        verify(exactly = 0) { repository.save(any()) }
    }
})

