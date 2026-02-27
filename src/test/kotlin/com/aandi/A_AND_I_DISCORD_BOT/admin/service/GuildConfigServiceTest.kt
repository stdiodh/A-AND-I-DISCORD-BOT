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
