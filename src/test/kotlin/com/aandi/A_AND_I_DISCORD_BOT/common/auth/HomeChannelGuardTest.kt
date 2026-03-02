package com.aandi.A_AND_I_DISCORD_BOT.common.auth

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class HomeChannelGuardTest : FunSpec({
    val guildConfigService = mockk<GuildConfigService>()
    val guard = HomeChannelGuard(guildConfigService)
    val guildId = 123L
    val homeChannelId = 10L

    test("홈 채널 미설정이면 허용한다") {
        every { guildConfigService.getDashboard(guildId) } returns GuildConfigService.DashboardConfig(
            channelId = null,
            messageId = null,
        )

        val result = guard.validate(
            guildId = guildId,
            currentChannelId = 11L,
            featureChannelId = 20L,
            featureName = "회의",
            setupCommand = "/설정 회의채널 채널:#회의",
            usageCommand = "/회의 시작 채널:#회의",
        )

        result shouldBe HomeChannelGuard.GuardResult.Allowed
    }

    test("홈 채널이 아니면 허용한다") {
        every { guildConfigService.getDashboard(guildId) } returns GuildConfigService.DashboardConfig(
            channelId = homeChannelId,
            messageId = 777L,
        )

        val result = guard.validate(
            guildId = guildId,
            currentChannelId = 11L,
            featureChannelId = 20L,
            featureName = "회의",
            setupCommand = "/설정 회의채널 채널:#회의",
            usageCommand = "/회의 시작 채널:#회의",
        )

        result shouldBe HomeChannelGuard.GuardResult.Allowed
    }

    test("홈 채널에서 전용 채널이 없으면 차단한다") {
        every { guildConfigService.getDashboard(guildId) } returns GuildConfigService.DashboardConfig(
            channelId = homeChannelId,
            messageId = 777L,
        )

        val result = guard.validate(
            guildId = guildId,
            currentChannelId = homeChannelId,
            featureChannelId = null,
            featureName = "회의",
            setupCommand = "/설정 회의채널 채널:#회의",
            usageCommand = "/회의 시작 채널:#회의",
        )

        (result is HomeChannelGuard.GuardResult.Blocked) shouldBe true
    }

    test("홈 채널과 전용 채널이 같으면 허용한다") {
        every { guildConfigService.getDashboard(guildId) } returns GuildConfigService.DashboardConfig(
            channelId = homeChannelId,
            messageId = 777L,
        )

        val result = guard.validate(
            guildId = guildId,
            currentChannelId = homeChannelId,
            featureChannelId = homeChannelId,
            featureName = "회의",
            setupCommand = "/설정 회의채널 채널:#회의",
            usageCommand = "/회의 시작 채널:#회의",
        )

        result shouldBe HomeChannelGuard.GuardResult.Allowed
    }

    test("홈 채널에서 전용 채널이 다르면 차단한다") {
        every { guildConfigService.getDashboard(guildId) } returns GuildConfigService.DashboardConfig(
            channelId = homeChannelId,
            messageId = 777L,
        )

        val result = guard.validate(
            guildId = guildId,
            currentChannelId = homeChannelId,
            featureChannelId = 30L,
            featureName = "회의",
            setupCommand = "/설정 회의채널 채널:#회의",
            usageCommand = "/회의 시작 채널:#회의",
        )

        (result is HomeChannelGuard.GuardResult.Blocked) shouldBe true
    }
})
