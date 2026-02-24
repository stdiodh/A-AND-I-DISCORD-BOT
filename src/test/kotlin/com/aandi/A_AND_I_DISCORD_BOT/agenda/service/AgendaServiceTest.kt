package com.aandi.A_AND_I_DISCORD_BOT.agenda.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.AgendaLink
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.AgendaLinkRepository
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodCalculator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate

class AgendaServiceTest : FunSpec({

    val agendaLinkRepository = mockk<AgendaLinkRepository>()
    val periodCalculator = mockk<PeriodCalculator>()
    val permissionChecker = mockk<PermissionChecker>()
    val service = AgendaService(
        agendaLinkRepository = agendaLinkRepository,
        periodCalculator = periodCalculator,
        permissionChecker = permissionChecker,
    )

    val guildId = 1000L
    val userId = 2000L
    val fixedToday = LocalDate.of(2026, 2, 24)

    beforeTest {
        clearAllMocks()
    }

    test("setTodayAgenda-기존 링크가 없으면 신규 저장을 반환한다") {
        val savedSlot = slot<AgendaLink>()

        every {
            permissionChecker.isAdmin(
                guildId = guildId,
                requesterRoleIds = setOf(11L),
                hasManageServerPermission = false,
            )
        } returns true
        every { periodCalculator.today(any()) } returns fixedToday
        every { agendaLinkRepository.findByGuildIdAndDateLocal(guildId, fixedToday) } returns null
        every { agendaLinkRepository.save(capture(savedSlot)) } answers { firstArg() }

        val result = service.setTodayAgenda(
            guildId = guildId,
            requesterUserId = userId,
            requesterRoleIds = setOf(11L),
            hasManageServerPermission = false,
            rawUrl = "https://docs.google.com/document/d/new",
            rawTitle = "주간 회의",
        )

        val success = result.shouldBeInstanceOf<AgendaService.SetAgendaResult.Success>()
        success.updated shouldBe false
        success.title shouldBe "주간 회의"
        success.url shouldBe "https://docs.google.com/document/d/new"
        savedSlot.captured.guildId shouldBe guildId
        savedSlot.captured.createdBy shouldBe userId
        savedSlot.captured.dateLocal shouldBe fixedToday
        savedSlot.captured.title shouldBe "주간 회의"
        savedSlot.captured.url shouldBe "https://docs.google.com/document/d/new"

        verify(exactly = 1) { agendaLinkRepository.save(any()) }
    }

    test("setTodayAgenda-기존 링크가 있으면 업데이트로 덮어쓴다") {
        val existing = AgendaLink(
            id = 1L,
            guildId = guildId,
            dateLocal = fixedToday,
            title = "기존 제목",
            url = "https://docs.google.com/document/d/old",
            createdBy = userId,
            createdAt = Instant.parse("2026-02-23T12:00:00Z"),
            updatedAt = Instant.parse("2026-02-23T12:00:00Z"),
        )
        val savedSlot = slot<AgendaLink>()

        every {
            permissionChecker.isAdmin(
                guildId = guildId,
                requesterRoleIds = setOf(22L),
                hasManageServerPermission = false,
            )
        } returns true
        every { periodCalculator.today(any()) } returns fixedToday
        every { agendaLinkRepository.findByGuildIdAndDateLocal(guildId, fixedToday) } returns existing
        every { agendaLinkRepository.save(capture(savedSlot)) } answers { firstArg() }

        val result = service.setTodayAgenda(
            guildId = guildId,
            requesterUserId = userId,
            requesterRoleIds = setOf(22L),
            hasManageServerPermission = false,
            rawUrl = "https://docs.google.com/document/d/new-version",
            rawTitle = "수정된 제목",
        )

        val success = result.shouldBeInstanceOf<AgendaService.SetAgendaResult.Success>()
        success.updated shouldBe true
        success.title shouldBe "수정된 제목"
        success.url shouldBe "https://docs.google.com/document/d/new-version"
        savedSlot.captured shouldBe existing
        existing.title shouldBe "수정된 제목"
        existing.url shouldBe "https://docs.google.com/document/d/new-version"

        verify(exactly = 1) { agendaLinkRepository.save(existing) }
    }

    test("getTodayAgenda-오늘 링크가 있으면 반환한다") {
        val existing = AgendaLink(
            id = 3L,
            guildId = guildId,
            dateLocal = fixedToday,
            title = "오늘 안건",
            url = "https://docs.google.com/document/d/today",
            createdBy = userId,
        )

        every { periodCalculator.today(any()) } returns fixedToday
        every { agendaLinkRepository.findByGuildIdAndDateLocal(guildId, fixedToday) } returns existing

        val result = service.getTodayAgenda(guildId)

        result?.title shouldBe "오늘 안건"
        result?.url shouldBe "https://docs.google.com/document/d/today"
        result?.dateLocal shouldBe fixedToday
    }

    test("getTodayAgenda-오늘 링크가 없으면 null을 반환한다") {
        every { periodCalculator.today(any()) } returns fixedToday
        every { agendaLinkRepository.findByGuildIdAndDateLocal(guildId, fixedToday) } returns null

        val result = service.getTodayAgenda(guildId)

        result.shouldBeNull()
    }

    test("setTodayAgenda-http https가 아닌 URL이면 InvalidUrl을 반환한다") {
        every {
            permissionChecker.isAdmin(
                guildId = guildId,
                requesterRoleIds = setOf(33L),
                hasManageServerPermission = false,
            )
        } returns true

        val result = service.setTodayAgenda(
            guildId = guildId,
            requesterUserId = userId,
            requesterRoleIds = setOf(33L),
            hasManageServerPermission = false,
            rawUrl = "ftp://example.com/agenda",
            rawTitle = "제목",
        )

        result shouldBe AgendaService.SetAgendaResult.InvalidUrl
        verify(exactly = 0) { agendaLinkRepository.save(any()) }
    }
})
