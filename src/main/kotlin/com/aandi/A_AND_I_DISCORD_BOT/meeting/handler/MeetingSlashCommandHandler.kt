package com.aandi.A_AND_I_DISCORD_BOT.meeting.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.auth.AdminPermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorCode
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorResponse
import com.aandi.A_AND_I_DISCORD_BOT.meeting.service.MeetingService
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingSlashCommandHandler(
    private val meetingService: MeetingService,
    private val adminPermissionChecker: AdminPermissionChecker,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND_NAME_KO && event.name != COMMAND_NAME_EN) {
            return
        }
        if (event.subcommandName == SUBCOMMAND_START_KO || event.subcommandName == SUBCOMMAND_START_EN) {
            startMeeting(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_END_KO || event.subcommandName == SUBCOMMAND_END_EN) {
            endMeeting(event)
            return
        }
        replyInvalidInput(event, "지원하지 않는 하위 명령입니다.")
    }

    private fun startMeeting(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyInvalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (!isMeetingAllowed(guild.idLong, member)) {
            replyAccessDenied(event, "회의 시작 권한이 없습니다.")
            return
        }

        val channelOption = event.getOption(OPTION_CHANNEL_KO)?.asChannel
        if (channelOption == null || channelOption.type != ChannelType.TEXT) {
            replyInvalidInput(event, "텍스트 채널을 지정해 주세요.")
            return
        }

        when (
            val result = meetingService.startMeeting(
                guildId = guild.idLong,
                requestedBy = member.idLong,
                targetChannelId = channelOption.idLong,
                fallbackChannelId = null,
                rawTitle = null,
            )
        ) {
            is MeetingService.StartResult.Success -> {
                event.reply("회의를 시작했습니다. 스레드: <#${result.threadId}>")
                    .setEphemeral(true)
                    .queue()
            }

            is MeetingService.StartResult.AlreadyActive -> {
                replyInvalidInput(event, "이미 진행 중인 회의가 있습니다. <#${result.threadId}>")
            }

            MeetingService.StartResult.ChannelNotConfigured -> {
                replyInvalidInput(event, "홈 채널이 설정되지 않았습니다. `/홈 생성`을 먼저 실행해 주세요.")
            }

            MeetingService.StartResult.ChannelNotFound -> {
                replyInvalidInput(event, "회의 스레드를 만들 채널을 찾을 수 없습니다.")
            }

            MeetingService.StartResult.ThreadCreateFailed -> {
                replyInvalidInput(event, "회의 스레드 생성에 실패했습니다.")
            }
        }
    }

    private fun endMeeting(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyInvalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (!isMeetingAllowed(guild.idLong, member)) {
            replyAccessDenied(event, "회의 종료 권한이 없습니다.")
            return
        }

        val requestedThreadId = parseThreadId(event.getOption(OPTION_THREAD_ID_KO)?.asString)
        val fallbackThreadId = if (event.channelType.isThread) event.channel.idLong else null
        when (
            val result = meetingService.endMeeting(
                guildId = guild.idLong,
                requestedBy = member.idLong,
                fallbackThreadId = fallbackThreadId,
                requestedThreadId = requestedThreadId,
            )
        ) {
            is MeetingService.EndResult.Success -> {
                event.reply(
                    "회의를 종료했습니다. 요약 메시지: <#${result.threadId}> (결정 ${result.decisions.size}건 / 액션 ${result.actionItems.size}건)",
                )
                    .setEphemeral(true)
                    .queue()
            }

            MeetingService.EndResult.SessionNotFound -> {
                replyInvalidInput(event, "종료할 회의 세션을 찾지 못했습니다. 회의 스레드에서 실행하거나 스레드아이디를 지정해 주세요.")
            }

            MeetingService.EndResult.AlreadyEnded -> {
                replyInvalidInput(event, "이미 종료된 회의입니다.")
            }

            is MeetingService.EndResult.ThreadNotFound -> {
                replyInvalidInput(event, "요약 대상 스레드를 찾지 못했습니다. (threadId=${result.threadId})")
            }
        }
    }

    private fun parseThreadId(raw: String?): Long? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return raw.trim().toLongOrNull()
    }

    private fun isMeetingAllowed(guildId: Long, member: net.dv8tion.jda.api.entities.Member): Boolean {
        if (adminPermissionChecker.isAdmin(guildId, member)) {
            return true
        }
        return adminPermissionChecker.canSetAdminRole(guildId, member)
    }

    private fun replyInvalidInput(event: SlashCommandInteractionEvent, message: String) {
        event.reply(errorPayload(DiscordErrorCode.COMMON_INVALID_INPUT, message))
            .setEphemeral(true)
            .queue()
    }

    private fun replyAccessDenied(event: SlashCommandInteractionEvent, message: String) {
        event.reply(errorPayload(DiscordErrorCode.ACCESS_DENIED, message))
            .setEphemeral(true)
            .queue()
    }

    private fun errorPayload(code: DiscordErrorCode, message: String): String = DiscordErrorFormatter.format(
        DiscordErrorResponse(
            code = code,
            message = message,
            retryable = false,
        ),
    )

    companion object {
        private const val COMMAND_NAME_KO = "회의"
        private const val COMMAND_NAME_EN = "meeting"
        private const val SUBCOMMAND_START_KO = "시작"
        private const val SUBCOMMAND_START_EN = "start"
        private const val SUBCOMMAND_END_KO = "종료"
        private const val SUBCOMMAND_END_EN = "end"
        private const val OPTION_CHANNEL_KO = "채널"
        private const val OPTION_THREAD_ID_KO = "스레드아이디"
    }
}
