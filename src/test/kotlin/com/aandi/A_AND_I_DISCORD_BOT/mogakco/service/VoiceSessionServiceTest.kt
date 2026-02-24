package com.aandi.A_AND_I_DISCORD_BOT.mogakco.service

import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.VoiceSession
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.MogakcoChannelRepository
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.VoiceSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.VoiceSessionService.VoiceEventOutcome
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.Instant

class VoiceSessionServiceTest : FunSpec({

    val mogakcoChannelRepository = mockk<MogakcoChannelRepository>()
    val voiceSessionRepository = mockk<VoiceSessionRepository>()
    val service = VoiceSessionService(
        mogakcoChannelRepository = mogakcoChannelRepository,
        voiceSessionRepository = voiceSessionRepository,
    )

    val guildId = 1000L
    val userId = 2000L
    val trackedChannelId = 3000L
    val otherChannelId = 4000L
    val occurredAt = Instant.parse("2026-02-24T03:00:00Z")

    beforeTest {
        clearAllMocks()
        every { mogakcoChannelRepository.existsByIdGuildIdAndIdChannelId(any(), any()) } returns false
        every { voiceSessionRepository.save(any()) } answers { firstArg() }
    }

    test("Join-old null new tracked이면 열린 세션을 생성한다") {
        val savedSlot = slot<VoiceSession>()

        every { mogakcoChannelRepository.existsByIdGuildIdAndIdChannelId(guildId, trackedChannelId) } returns true
        every {
            voiceSessionRepository.findFirstByGuildIdAndUserIdAndLeftAtIsNullOrderByJoinedAtDesc(guildId, userId)
        } returns null
        every { voiceSessionRepository.save(capture(savedSlot)) } answers { firstArg() }

        val outcome = service.ingest(
            VoiceTransition(
                guildId = guildId,
                userId = userId,
                oldChannelId = null,
                newChannelId = trackedChannelId,
                occurredAt = occurredAt,
            ),
        )

        outcome shouldBe VoiceEventOutcome.JoinedTracked
        savedSlot.captured.guildId shouldBe guildId
        savedSlot.captured.userId shouldBe userId
        savedSlot.captured.channelId shouldBe trackedChannelId
        savedSlot.captured.joinedAt shouldBe occurredAt
        savedSlot.captured.leftAt shouldBe null
        verify(exactly = 1) { voiceSessionRepository.save(any()) }
    }

    test("Leave-old tracked new null이면 열린 세션을 종료한다") {
        val open = VoiceSession(
            guildId = guildId,
            userId = userId,
            channelId = trackedChannelId,
            joinedAt = occurredAt.minus(Duration.ofMinutes(20)),
        )

        every { mogakcoChannelRepository.existsByIdGuildIdAndIdChannelId(guildId, trackedChannelId) } returns true
        every {
            voiceSessionRepository.findFirstByGuildIdAndUserIdAndLeftAtIsNullOrderByJoinedAtDesc(guildId, userId)
        } returns open

        val outcome = service.ingest(
            VoiceTransition(
                guildId = guildId,
                userId = userId,
                oldChannelId = trackedChannelId,
                newChannelId = null,
                occurredAt = occurredAt,
            ),
        )

        outcome shouldBe VoiceEventOutcome.LeftTracked
        open.leftAt shouldBe occurredAt
        open.durationSec shouldBe 1200
        verify(exactly = 0) { voiceSessionRepository.save(any()) }
    }

    test("Move out-old tracked new other이면 열린 세션을 종료한다") {
        val open = VoiceSession(
            guildId = guildId,
            userId = userId,
            channelId = trackedChannelId,
            joinedAt = occurredAt.minus(Duration.ofMinutes(10)),
        )

        every { mogakcoChannelRepository.existsByIdGuildIdAndIdChannelId(guildId, trackedChannelId) } returns true
        every { mogakcoChannelRepository.existsByIdGuildIdAndIdChannelId(guildId, otherChannelId) } returns false
        every {
            voiceSessionRepository.findFirstByGuildIdAndUserIdAndLeftAtIsNullOrderByJoinedAtDesc(guildId, userId)
        } returns open

        val outcome = service.ingest(
            VoiceTransition(
                guildId = guildId,
                userId = userId,
                oldChannelId = trackedChannelId,
                newChannelId = otherChannelId,
                occurredAt = occurredAt,
            ),
        )

        outcome shouldBe VoiceEventOutcome.LeftTracked
        open.leftAt shouldBe occurredAt
        open.durationSec shouldBe 600
        verify(exactly = 0) { voiceSessionRepository.save(any()) }
    }

    test("Move in-old other new tracked이면 새 세션을 생성한다") {
        val savedSlot = slot<VoiceSession>()

        every { mogakcoChannelRepository.existsByIdGuildIdAndIdChannelId(guildId, otherChannelId) } returns false
        every { mogakcoChannelRepository.existsByIdGuildIdAndIdChannelId(guildId, trackedChannelId) } returns true
        every {
            voiceSessionRepository.findFirstByGuildIdAndUserIdAndLeftAtIsNullOrderByJoinedAtDesc(guildId, userId)
        } returns null
        every { voiceSessionRepository.save(capture(savedSlot)) } answers { firstArg() }

        val outcome = service.ingest(
            VoiceTransition(
                guildId = guildId,
                userId = userId,
                oldChannelId = otherChannelId,
                newChannelId = trackedChannelId,
                occurredAt = occurredAt,
            ),
        )

        outcome shouldBe VoiceEventOutcome.JoinedTracked
        savedSlot.captured.channelId shouldBe trackedChannelId
        savedSlot.captured.joinedAt shouldBe occurredAt
        verify(exactly = 1) { voiceSessionRepository.save(any()) }
    }

    test("Safety-열린 세션이 이미 있으면 먼저 닫고 새 세션을 연다") {
        val open = VoiceSession(
            guildId = guildId,
            userId = userId,
            channelId = trackedChannelId,
            joinedAt = occurredAt.minus(Duration.ofMinutes(15)),
        )
        val savedSlot = slot<VoiceSession>()

        every { mogakcoChannelRepository.existsByIdGuildIdAndIdChannelId(guildId, trackedChannelId) } returns true
        every {
            voiceSessionRepository.findFirstByGuildIdAndUserIdAndLeftAtIsNullOrderByJoinedAtDesc(guildId, userId)
        } returns open
        every { voiceSessionRepository.save(capture(savedSlot)) } answers { firstArg() }

        val outcome = service.ingest(
            VoiceTransition(
                guildId = guildId,
                userId = userId,
                oldChannelId = null,
                newChannelId = trackedChannelId,
                occurredAt = occurredAt,
            ),
        )

        outcome shouldBe VoiceEventOutcome.JoinedTracked
        open.leftAt shouldBe occurredAt
        open.durationSec shouldBe 900
        savedSlot.captured.channelId shouldBe trackedChannelId
        savedSlot.captured.joinedAt shouldBe occurredAt
        verify(exactly = 1) { voiceSessionRepository.save(any()) }
    }
})
