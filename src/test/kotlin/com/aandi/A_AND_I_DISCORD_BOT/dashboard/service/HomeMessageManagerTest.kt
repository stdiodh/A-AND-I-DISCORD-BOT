package com.aandi.A_AND_I_DISCORD_BOT.dashboard.service

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.GuildConfig
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.MessageHistory
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction

class HomeMessageManagerTest : FunSpec({

    test("ensureHomeMessage는 저장된 홈 메시지가 존재하면 재사용한다") {
        val guildConfigService = mockk<GuildConfigService>()
        val jda = mockk<JDA>()
        val channel = mockk<TextChannel>()
        val message = mockk<Message>(relaxed = true)
        val action = mockk<RestAction<Message>>()

        every { guildConfigService.getDashboard(100L) } returns GuildConfigService.DashboardConfig(
            channelId = 200L,
            messageId = 300L,
        )
        every { jda.getTextChannelById(200L) } returns channel
        every { channel.retrieveMessageById(300L) } returns action
        every { action.complete() } returns message
        every { message.idLong } returns 300L

        val manager = HomeMessageManager(guildConfigService, jda)
        val payload = HomeMessageManager.HomePayload(
            embed = EmbedBuilder().setTitle("home").build(),
            components = emptyList(),
        )

        val result = manager.ensureHomeMessage(
            guildId = 100L,
            preferredChannelId = 200L,
            payload = payload,
        )

        val success = result.shouldBeInstanceOf<HomeMessageManager.EnsureResult.Success>()
        success.asClue {
            it.channelId shouldBe 200L
            it.messageId shouldBe 300L
            it.outcome shouldBe HomeMessageManager.EnsureOutcome.REUSED
        }
        verify(exactly = 0) { guildConfigService.setDashboard(any(), any(), any()) }
    }

    test("markerForGuild는 길드별 고유 홈 마커를 생성한다") {
        val guildConfigService = mockk<GuildConfigService>()
        val jda = mockk<JDA>()
        val manager = HomeMessageManager(guildConfigService, jda)

        manager.markerForGuild(1234L) shouldBe "A&I_HOME_MARKER:1234"
    }

    test("ensureHomeMessage를 연속 호출해도 홈 메시지는 한 번만 생성된다") {
        val guildConfigService = mockk<GuildConfigService>()
        val jda = mockk<JDA>()
        val channel = mockk<TextChannel>()
        val history = mockk<MessageHistory>()
        val historyAction = mockk<RestAction<List<Message>>>()
        val sendAction = mockk<MessageCreateAction>()
        val retrieveAction = mockk<RestAction<Message>>()
        val createdMessage = mockk<Message>(relaxed = true)

        var storedChannelId: Long? = null
        var storedMessageId: Long? = null
        every { guildConfigService.getDashboard(100L) } answers {
            GuildConfigService.DashboardConfig(
                channelId = storedChannelId,
                messageId = storedMessageId,
            )
        }
        every { guildConfigService.setDashboard(100L, any(), any()) } answers {
            storedChannelId = secondArg()
            storedMessageId = thirdArg()
            mockk<GuildConfig>()
        }
        every { jda.getTextChannelById(200L) } returns channel
        every { channel.history } returns history
        every { history.retrievePast(any()) } returns historyAction
        every { historyAction.complete() } returns emptyList()
        every { channel.sendMessageEmbeds(any<MessageEmbed>(), *anyVararg()) } returns sendAction
        every { sendAction.setComponents(any<List<net.dv8tion.jda.api.components.actionrow.ActionRow>>()) } returns sendAction
        every { sendAction.complete() } returns createdMessage
        every { createdMessage.idLong } returns 301L
        every { channel.retrieveMessageById(301L) } returns retrieveAction
        every { retrieveAction.complete() } returns createdMessage

        val manager = HomeMessageManager(guildConfigService, jda)
        val payload = HomeMessageManager.HomePayload(
            embed = EmbedBuilder().setTitle("home").build(),
            components = emptyList(),
        )

        val first = manager.ensureHomeMessage(100L, 200L, payload).shouldBeInstanceOf<HomeMessageManager.EnsureResult.Success>()
        val second = manager.ensureHomeMessage(100L, 200L, payload).shouldBeInstanceOf<HomeMessageManager.EnsureResult.Success>()

        first.outcome shouldBe HomeMessageManager.EnsureOutcome.CREATED
        second.outcome shouldBe HomeMessageManager.EnsureOutcome.REUSED
        verify(exactly = 1) { channel.sendMessageEmbeds(any<MessageEmbed>(), *anyVararg()) }
        verify(exactly = 1) { guildConfigService.setDashboard(100L, 200L, 301L) }
    }
})
