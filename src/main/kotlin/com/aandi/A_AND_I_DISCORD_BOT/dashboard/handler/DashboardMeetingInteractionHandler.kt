package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeCustomIdParser
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionPrefixHandler
import com.aandi.A_AND_I_DISCORD_BOT.meeting.service.MeetingService
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class DashboardMeetingInteractionHandler(
    private val permissionGate: PermissionGate,
    private val meetingService: MeetingService,
) : InteractionPrefixHandler {

    override fun supports(prefix: String): Boolean {
        return prefix in supportedPrefixes
    }

    override fun onButton(event: ButtonInteractionEvent): Boolean {
        if (event.componentId == DashboardActionIds.MEETING_START) {
            startMeetingFromDashboard(event)
            return true
        }

        val parsed = HomeCustomIdParser.parse(event.componentId) ?: return false
        if (parsed.domain != "meeting" || parsed.action != "start") {
            return false
        }

        startMeetingFromDashboard(event)
        return true
    }

    private fun startMeetingFromDashboard(event: ButtonInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!permissionGate.canAdminAction(guild.idLong, member)) {
            event.reply("회의 시작 권한이 없습니다.").setEphemeral(true).queue()
            return
        }

        val fallbackChannelId = event.channel.idLong
        when (
            val result = meetingService.startMeeting(
                guildId = guild.idLong,
                requestedBy = member.idLong,
                targetChannelId = null,
                fallbackChannelId = fallbackChannelId,
                rawTitle = null,
            )
        ) {
            is MeetingService.StartResult.Success -> {
                event.reply("회의 스레드를 생성했습니다: <#${result.threadId}>")
                    .setEphemeral(true)
                    .queue()
            }

            is MeetingService.StartResult.AlreadyActive -> {
                event.reply("이미 진행 중인 회의가 있습니다: <#${result.threadId}>")
                    .setEphemeral(true)
                    .queue()
            }

            MeetingService.StartResult.ChannelNotConfigured -> {
                event.reply("회의 채널이 설정되지 않았습니다. `/홈 생성` 후 다시 시도해 주세요.")
                    .setEphemeral(true)
                    .queue()
            }

            MeetingService.StartResult.ChannelNotFound -> {
                event.reply("회의 채널을 찾지 못했습니다.").setEphemeral(true).queue()
            }

            MeetingService.StartResult.ThreadCreateFailed -> {
                event.reply("회의 스레드 생성에 실패했습니다.").setEphemeral(true).queue()
            }
        }
    }

    companion object {
        private val supportedPrefixes = setOf("dash", "home")
    }
}
