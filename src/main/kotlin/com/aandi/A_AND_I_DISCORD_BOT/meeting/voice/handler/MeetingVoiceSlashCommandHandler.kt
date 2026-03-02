package com.aandi.A_AND_I_DISCORD_BOT.meeting.voice.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.auth.AdminPermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorCode
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorResponse
import com.aandi.A_AND_I_DISCORD_BOT.meeting.voice.service.MeetingVoiceSummaryService
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingVoiceSlashCommandHandler(
    private val meetingVoiceSummaryService: MeetingVoiceSummaryService,
    private val adminPermissionChecker: AdminPermissionChecker,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND_NAME_KO && event.name != COMMAND_NAME_EN) {
            return
        }

        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyInvalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (!isAdmin(guild.idLong, member)) {
            replyAccessDenied(event, "이 명령은 운영진만 사용할 수 있습니다.")
            return
        }

        if (isSubcommand(event, SUBCOMMAND_START_KO, SUBCOMMAND_START_EN)) {
            handleStart(event, guild.idLong)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_STOP_KO, SUBCOMMAND_STOP_EN)) {
            handleStop(event, guild.idLong)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_STATUS_KO, SUBCOMMAND_STATUS_EN)) {
            handleStatus(event, guild.idLong)
            return
        }

        replyInvalidInput(event, "지원하지 않는 하위 명령입니다.")
    }

    private fun handleStart(event: SlashCommandInteractionEvent, guildId: Long) {
        val meetingRefId = resolveMeetingIdOption(event)
        if (meetingRefId == null) {
            replyInvalidInput(event, "아이디 옵션이 필요합니다.")
            return
        }

        val voiceChannel = resolveVoiceChannelOption(event)
        if (voiceChannel == null) {
            replyInvalidInput(event, "채널 옵션이 필요합니다.")
            return
        }
        if (voiceChannel.type != ChannelType.VOICE && voiceChannel.type != ChannelType.STAGE) {
            replyInvalidInput(event, "채널은 음성 채널만 가능합니다.")
            return
        }

        val createdBy = event.member?.idLong
        if (createdBy == null) {
            replyInvalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }

        when (val result = meetingVoiceSummaryService.start(guildId, meetingRefId, voiceChannel.idLong, createdBy)) {
            is MeetingVoiceSummaryService.StartResult.Disabled -> {
                event.reply(result.message)
                    .setEphemeral(true)
                    .queue()
            }

            is MeetingVoiceSummaryService.StartResult.Accepted -> {
                event.reply("${result.message}\njobId=${result.job.id}, status=${result.job.status}, channel=<#${result.job.voiceChannelId}>")
                    .setEphemeral(true)
                    .queue()
            }

            is MeetingVoiceSummaryService.StartResult.InvalidState -> {
                event.reply("${result.message}\njobId=${result.job.id}, status=${result.job.status}")
                    .setEphemeral(true)
                    .queue()
            }

            MeetingVoiceSummaryService.StartResult.MeetingNotFound -> {
                replyInvalidInput(event, "해당 회의를 찾을 수 없습니다. (회의 세션 ID 또는 스레드 ID)")
            }
        }
    }

    private fun handleStop(event: SlashCommandInteractionEvent, guildId: Long) {
        val meetingRefId = resolveMeetingIdOption(event)
        if (meetingRefId == null) {
            replyInvalidInput(event, "아이디 옵션이 필요합니다.")
            return
        }

        when (val result = meetingVoiceSummaryService.stop(guildId, meetingRefId)) {
            is MeetingVoiceSummaryService.StopResult.Disabled -> {
                event.reply(result.message)
                    .setEphemeral(true)
                    .queue()
            }

            is MeetingVoiceSummaryService.StopResult.Accepted -> {
                event.reply("${result.message}\njobId=${result.job.id}, status=${result.job.status}")
                    .setEphemeral(true)
                    .queue()
            }

            is MeetingVoiceSummaryService.StopResult.InvalidState -> {
                event.reply("${result.message}\njobId=${result.job.id}, status=${result.job.status}")
                    .setEphemeral(true)
                    .queue()
            }

            MeetingVoiceSummaryService.StopResult.MeetingNotFound -> {
                replyInvalidInput(event, "해당 회의를 찾을 수 없습니다. (회의 세션 ID 또는 스레드 ID)")
            }

            MeetingVoiceSummaryService.StopResult.JobNotFound -> {
                replyInvalidInput(event, "종료할 음성요약 작업이 없습니다.")
            }
        }
    }

    private fun handleStatus(event: SlashCommandInteractionEvent, guildId: Long) {
        val meetingRefId = resolveMeetingIdOption(event)
        if (meetingRefId == null) {
            replyInvalidInput(event, "아이디 옵션이 필요합니다.")
            return
        }

        when (val result = meetingVoiceSummaryService.status(guildId, meetingRefId)) {
            is MeetingVoiceSummaryService.StatusResult.Disabled -> {
                event.reply(result.message)
                    .setEphemeral(true)
                    .queue()
            }

            is MeetingVoiceSummaryService.StatusResult.Ready -> {
                val detail = result.job?.let {
                    "\njobId=${it.id}, status=${it.status}, channel=<#${it.voiceChannelId}>, dataDir=${it.dataDir}"
                }.orEmpty()
                event.reply("${result.message}$detail")
                    .setEphemeral(true)
                    .queue()
            }

            MeetingVoiceSummaryService.StatusResult.MeetingNotFound -> {
                replyInvalidInput(event, "해당 회의를 찾을 수 없습니다. (회의 세션 ID 또는 스레드 ID)")
            }
        }
    }

    private fun isAdmin(guildId: Long, member: net.dv8tion.jda.api.entities.Member): Boolean {
        if (adminPermissionChecker.isAdmin(guildId, member)) {
            return true
        }
        return adminPermissionChecker.canSetAdminRole(guildId, member)
    }

    private fun isSubcommand(event: SlashCommandInteractionEvent, ko: String, en: String): Boolean {
        return event.subcommandName == ko || event.subcommandName == en
    }

    private fun resolveMeetingIdOption(event: SlashCommandInteractionEvent): Long? {
        return event.getOption(OPTION_ID_KO)?.asLong
            ?: event.getOption(OPTION_MEETING_ID_LEGACY_KO)?.asLong
            ?: event.getOption(OPTION_MEETING_ID_EN)?.asLong
    }

    private fun resolveVoiceChannelOption(event: SlashCommandInteractionEvent) =
        event.getOption(OPTION_CHANNEL_KO)?.asChannel
            ?: event.getOption(OPTION_VOICE_CHANNEL_LEGACY_KO)?.asChannel
            ?: event.getOption(OPTION_CHANNEL_EN)?.asChannel

    private fun replyInvalidInput(event: SlashCommandInteractionEvent, message: String) {
        replyError(event, DiscordErrorCode.COMMON_INVALID_INPUT, message)
    }

    private fun replyAccessDenied(event: SlashCommandInteractionEvent, message: String) {
        replyError(event, DiscordErrorCode.ACCESS_DENIED, message)
    }

    private fun replyError(event: SlashCommandInteractionEvent, code: DiscordErrorCode, message: String) {
        val payload = DiscordErrorFormatter.format(
            DiscordErrorResponse(
                code = code,
                message = message,
                retryable = false,
            ),
        )
        event.reply(payload)
            .setEphemeral(true)
            .queue()
    }

    companion object {
        private const val COMMAND_NAME_KO = "회의음성"
        private const val COMMAND_NAME_EN = "meetingvoice"
        private const val SUBCOMMAND_START_KO = "시작"
        private const val SUBCOMMAND_START_EN = "start"
        private const val SUBCOMMAND_STOP_KO = "종료"
        private const val SUBCOMMAND_STOP_EN = "stop"
        private const val SUBCOMMAND_STATUS_KO = "상태"
        private const val SUBCOMMAND_STATUS_EN = "status"
        private const val OPTION_ID_KO = "아이디"
        private const val OPTION_MEETING_ID_LEGACY_KO = "회의아이디"
        private const val OPTION_MEETING_ID_EN = "meetingId"
        private const val OPTION_CHANNEL_KO = "채널"
        private const val OPTION_VOICE_CHANNEL_LEGACY_KO = "보이스채널"
        private const val OPTION_CHANNEL_EN = "channel"
    }
}
