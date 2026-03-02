package com.aandi.A_AND_I_DISCORD_BOT.admin.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.GuildConfig
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.GuildConfigRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class GuildConfigServiceTest : FunSpec({

    val guildConfigRepository = mockk<GuildConfigRepository>()
    val service = GuildConfigService(guildConfigRepository)

    beforeTest {
        clearMocks(guildConfigRepository)
    }

    test("getOrCreate-기본 row를 보장하고 guild_config를 반환한다") {
        val guildId = 1234L
        val config = GuildConfig(guildId = guildId, timezone = "Asia/Seoul", mogakcoActiveMinutes = 30)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 1
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)

        val result = service.getOrCreate(guildId)

        result.guildId shouldBe guildId
        result.timezone shouldBe "Asia/Seoul"
        result.mogakcoActiveMinutes shouldBe 30
        verify(exactly = 1) { guildConfigRepository.createDefaultIfAbsent(guildId) }
    }

    test("setAdminRole-운영진 역할 ID를 저장한다") {
        val guildId = 1234L
        val roleId = 9876L
        val config = GuildConfig(guildId = guildId, adminRoleId = null)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)
        every { guildConfigRepository.save(config) } returns config

        val result = service.setAdminRole(guildId, roleId)

        result.adminRoleId shouldBe roleId
        verify(exactly = 1) { guildConfigRepository.save(config) }
    }

    test("getAdminRole-저장된 운영진 역할 ID를 반환한다") {
        val guildId = 1234L
        val config = GuildConfig(guildId = guildId, adminRoleId = 9999L)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)

        val result = service.getAdminRole(guildId)

        result shouldBe 9999L
    }

    test("clearAdminRole-운영진 역할 ID를 null로 저장한다") {
        val guildId = 1234L
        val config = GuildConfig(guildId = guildId, adminRoleId = 9999L)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)
        every { guildConfigRepository.save(config) } returns config

        val result = service.clearAdminRole(guildId)

        result.adminRoleId shouldBe null
        verify(exactly = 1) { guildConfigRepository.save(config) }
    }

    test("setMeetingOpenerRole-회의 시작 권한 역할 ID를 저장한다") {
        val guildId = 1234L
        val roleId = 3333L
        val config = GuildConfig(guildId = guildId, meetingOpenerRoleId = null)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)
        every { guildConfigRepository.save(config) } returns config

        val result = service.setMeetingOpenerRole(guildId, roleId)

        result.meetingOpenerRoleId shouldBe roleId
        verify(exactly = 1) { guildConfigRepository.save(config) }
    }

    test("getMeetingOpenerRole-저장된 회의 시작 권한 역할 ID를 반환한다") {
        val guildId = 1234L
        val config = GuildConfig(guildId = guildId, meetingOpenerRoleId = 4444L)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)

        val result = service.getMeetingOpenerRole(guildId)

        result shouldBe 4444L
    }

    test("clearMeetingOpenerRole-회의 시작 권한 역할 ID를 null로 저장한다") {
        val guildId = 1234L
        val config = GuildConfig(guildId = guildId, meetingOpenerRoleId = 4444L)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)
        every { guildConfigRepository.save(config) } returns config

        val result = service.clearMeetingOpenerRole(guildId)

        result.meetingOpenerRoleId shouldBe null
        verify(exactly = 1) { guildConfigRepository.save(config) }
    }

    test("setMeetingBoardChannel-회의 채널 ID를 저장한다") {
        val guildId = 1234L
        val channelId = 7777L
        val config = GuildConfig(guildId = guildId, meetingBoardChannelId = null)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)
        every { guildConfigRepository.save(config) } returns config

        val result = service.setMeetingBoardChannel(guildId, channelId)

        result.meetingBoardChannelId shouldBe channelId
        verify(exactly = 1) { guildConfigRepository.save(config) }
    }

    test("clearMeetingBoardChannel-회의 채널 ID를 null로 저장한다") {
        val guildId = 1234L
        val config = GuildConfig(guildId = guildId, meetingBoardChannelId = 7777L)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)
        every { guildConfigRepository.save(config) } returns config

        val result = service.clearMeetingBoardChannel(guildId)

        result.meetingBoardChannelId shouldBe null
        verify(exactly = 1) { guildConfigRepository.save(config) }
    }

    test("setMogakcoBoardChannel-모각코 채널 ID를 저장한다") {
        val guildId = 1234L
        val channelId = 8888L
        val config = GuildConfig(guildId = guildId, mogakcoBoardChannelId = null)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)
        every { guildConfigRepository.save(config) } returns config

        val result = service.setMogakcoBoardChannel(guildId, channelId)

        result.mogakcoBoardChannelId shouldBe channelId
        verify(exactly = 1) { guildConfigRepository.save(config) }
    }

    test("clearMogakcoBoardChannel-모각코 채널 ID를 null로 저장한다") {
        val guildId = 1234L
        val config = GuildConfig(guildId = guildId, mogakcoBoardChannelId = 8888L)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)
        every { guildConfigRepository.save(config) } returns config

        val result = service.clearMogakcoBoardChannel(guildId)

        result.mogakcoBoardChannelId shouldBe null
        verify(exactly = 1) { guildConfigRepository.save(config) }
    }

    test("setAssignmentBoardChannel-과제 공지 채널 ID를 defaultTaskChannelId에 저장한다") {
        val guildId = 1234L
        val channelId = 9999L
        val config = GuildConfig(guildId = guildId, defaultTaskChannelId = null)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)
        every { guildConfigRepository.save(config) } returns config

        val result = service.setAssignmentBoardChannel(guildId, channelId)

        result.defaultTaskChannelId shouldBe channelId
        verify(exactly = 1) { guildConfigRepository.save(config) }
    }

    test("getBoardChannels-회의 모각코 과제 채널 설정을 반환한다") {
        val guildId = 1234L
        val config = GuildConfig(
            guildId = guildId,
            meetingBoardChannelId = 7001L,
            mogakcoBoardChannelId = 7002L,
            defaultTaskChannelId = 7003L,
        )
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)

        val result = service.getBoardChannels(guildId)

        result.meetingChannelId shouldBe 7001L
        result.mogakcoChannelId shouldBe 7002L
        result.assignmentChannelId shouldBe 7003L
    }

    test("setDashboard-채널과 메시지 ID를 저장한다") {
        val guildId = 1234L
        val config = GuildConfig(guildId = guildId)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)
        every { guildConfigRepository.save(config) } returns config

        val result = service.setDashboard(guildId, 555L, 777L)

        result.dashboardChannelId shouldBe 555L
        result.dashboardMessageId shouldBe 777L
    }

    test("getDashboard-getOrCreate를 통해 nullable 대시보드 설정을 반환한다") {
        val guildId = 1234L
        val config = GuildConfig(guildId = guildId, dashboardChannelId = 101L, dashboardMessageId = 202L)
        every { guildConfigRepository.createDefaultIfAbsent(guildId) } returns 0
        every { guildConfigRepository.findById(guildId) } returns Optional.of(config)

        val result = service.getDashboard(guildId)

        result.channelId shouldBe 101L
        result.messageId shouldBe 202L
        verify(exactly = 1) { guildConfigRepository.createDefaultIfAbsent(guildId) }
    }
})
