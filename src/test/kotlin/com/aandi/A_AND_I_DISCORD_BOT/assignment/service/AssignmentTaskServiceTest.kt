package com.aandi.A_AND_I_DISCORD_BOT.assignment.service

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.GuildConfig
import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentStatus
import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentTaskEntity
import com.aandi.A_AND_I_DISCORD_BOT.assignment.repository.AssignmentTaskRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.fail

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
            dueAtUtc = nowUtc.plusSeconds(3600),
            createdBy = 3L,
            nowUtc = nowUtc,
            notifyRoleId = null,
            preReminderHoursRaw = null,
            closingMessageRaw = null,
        )

        result shouldBe AssignmentTaskService.CreateResult.RemindAtMustBeFuture
        verify(exactly = 0) { guildConfigService.getOrCreate(any()) }
        verify(exactly = 0) { assignmentTaskRepository.save(any()) }
    }

    test("create-dueAtUtc가 nowUtc 이전이면 거부된다") {
        val nowUtc = Instant.parse("2026-03-01T12:30:00Z")

        val result = service.create(
            guildId = 1L,
            channelId = 2L,
            title = "자료구조 과제",
            verifyUrl = "https://example.com/task",
            remindAtUtc = nowUtc.plusSeconds(60),
            dueAtUtc = nowUtc,
            createdBy = 3L,
            nowUtc = nowUtc,
            notifyRoleId = null,
            preReminderHoursRaw = null,
            closingMessageRaw = null,
        )

        result shouldBe AssignmentTaskService.CreateResult.DueAtMustBeFuture
        verify(exactly = 0) { guildConfigService.getOrCreate(any()) }
        verify(exactly = 0) { assignmentTaskRepository.save(any()) }
    }

    test("create-dueAtUtc가 remindAtUtc보다 이르면 거부된다") {
        val nowUtc = Instant.parse("2026-03-01T12:30:00Z")

        val result = service.create(
            guildId = 1L,
            channelId = 2L,
            title = "운영 과제",
            verifyUrl = "https://example.com/task",
            remindAtUtc = nowUtc.plusSeconds(7200),
            dueAtUtc = nowUtc.plusSeconds(3600),
            createdBy = 3L,
            nowUtc = nowUtc,
            notifyRoleId = null,
            preReminderHoursRaw = null,
            closingMessageRaw = null,
        )

        result shouldBe AssignmentTaskService.CreateResult.DueAtMustBeAfterRemindAt
        verify(exactly = 0) { guildConfigService.getOrCreate(any()) }
        verify(exactly = 0) { assignmentTaskRepository.save(any()) }
    }

    test("create-임박알림 형식이 잘못되면 거부된다") {
        val nowUtc = Instant.parse("2026-03-01T12:30:00Z")

        val result = service.create(
            guildId = 1L,
            channelId = 2L,
            title = "테스트 과제",
            verifyUrl = "https://example.com/task",
            remindAtUtc = nowUtc.plusSeconds(3600),
            dueAtUtc = nowUtc.plusSeconds(7200),
            createdBy = 3L,
            nowUtc = nowUtc,
            notifyRoleId = null,
            preReminderHoursRaw = "a,b,c",
            closingMessageRaw = null,
        )

        result shouldBe AssignmentTaskService.CreateResult.InvalidPreReminderHours
        verify(exactly = 0) { guildConfigService.getOrCreate(any()) }
        verify(exactly = 0) { assignmentTaskRepository.save(any()) }
    }

    test("create-기본 임박알림값으로 저장된다") {
        val nowUtc = Instant.parse("2026-03-01T12:30:00Z")
        every { guildConfigService.getOrCreate(any()) } returns GuildConfig(guildId = 1L)
        every { assignmentTaskRepository.save(any()) } answers {
            val entity = firstArg<AssignmentTaskEntity>()
            entity.id = 99L
            entity
        }

        val result = service.create(
            guildId = 1L,
            channelId = 2L,
            title = "기본값 과제",
            verifyUrl = "https://example.com/task",
            remindAtUtc = nowUtc.plusSeconds(3600),
            dueAtUtc = nowUtc.plusSeconds(10800),
            createdBy = 3L,
            nowUtc = nowUtc,
            notifyRoleId = null,
            preReminderHoursRaw = null,
            closingMessageRaw = null,
        )

        if (result is AssignmentTaskService.CreateResult.Success) {
            result.task.preRemindHours shouldBe setOf(24, 3, 1)
        }
        if (result !is AssignmentTaskService.CreateResult.Success) {
            fail("Expected success but was $result")
        }
        verify(exactly = 1) { guildConfigService.getOrCreate(1L) }
        verify(exactly = 1) { assignmentTaskRepository.save(any()) }
    }

    test("list-기본 조회는 취소 상태를 제외한 상태만 조회한다") {
        every {
            assignmentTaskRepository.findByGuildIdAndStatusInOrderByRemindAtDescCreatedAtDesc(
                guildId = 1L,
                statuses = any(),
            )
        } returns emptyList()

        val result = service.list(guildId = 1L, rawStatus = null)

        result shouldBe AssignmentTaskService.ListResult.Success(emptyList())
        verify(exactly = 1) {
            assignmentTaskRepository.findByGuildIdAndStatusInOrderByRemindAtDescCreatedAtDesc(
                guildId = 1L,
                statuses = setOf(AssignmentStatus.PENDING, AssignmentStatus.DONE, AssignmentStatus.CLOSED),
            )
        }
    }

    test("list-취소 상태 필터는 숨김 처리된다") {
        val result = service.list(guildId = 1L, rawStatus = "취소")

        result shouldBe AssignmentTaskService.ListResult.HiddenDeleted
        verify(exactly = 0) { assignmentTaskRepository.findByGuildIdAndStatusOrderByRemindAtDescCreatedAtDesc(any(), any()) }
    }
})
