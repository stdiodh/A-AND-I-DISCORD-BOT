package com.aandi.A_AND_I_DISCORD_BOT.assignment.service

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.repository.AssignmentTaskRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AssignmentTaskServiceTest : FunSpec({

    val assignmentTaskRepository = mockk<AssignmentTaskRepository>()
    val guildConfigService = mockk<GuildConfigService>()
    val clock = Clock.fixed(Instant.parse("2026-03-01T12:30:00Z"), ZoneOffset.UTC)
    val service = AssignmentTaskService(
        assignmentTaskRepository = assignmentTaskRepository,
        guildConfigService = guildConfigService,
        clock = clock,
    )

    beforeTest {
        clearAllMocks()
    }

    test("create-remindAtUtc가 nowUtc와 같으면 과거 입력으로 거부된다") {
        val nowUtc = Instant.parse("2026-03-01T12:30:00Z")

        val result = service.create(
            guildId = 1L,
            channelId = 2L,
            title = "알고리즘 과제",
            verifyUrl = "https://example.com/task",
            remindAtUtc = nowUtc,
            createdBy = 3L,
            nowUtc = nowUtc,
        )

        result shouldBe AssignmentTaskService.CreateResult.RemindAtMustBeFuture
        verify(exactly = 0) { guildConfigService.getOrCreate(any()) }
        verify(exactly = 0) { assignmentTaskRepository.save(any()) }
    }

    test("create-remindAtUtc가 nowUtc보다 이전이면 과거 입력으로 거부된다") {
        val nowUtc = Instant.parse("2026-03-01T12:30:00Z")

        val result = service.create(
            guildId = 1L,
            channelId = 2L,
            title = "자료구조 과제",
            verifyUrl = "https://example.com/task",
            remindAtUtc = nowUtc.minusSeconds(1),
            createdBy = 3L,
            nowUtc = nowUtc,
        )

        result shouldBe AssignmentTaskService.CreateResult.RemindAtMustBeFuture
        verify(exactly = 0) { guildConfigService.getOrCreate(any()) }
        verify(exactly = 0) { assignmentTaskRepository.save(any()) }
    }
})
