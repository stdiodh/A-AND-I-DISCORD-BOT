package com.aandi.A_AND_I_DISCORD_BOT.meeting.handler

import com.aandi.A_AND_I_DISCORD_BOT.agenda.service.AgendaService
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.DiscordReplyFactory
import com.aandi.A_AND_I_DISCORD_BOT.meeting.service.MeetingService
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingSlashCommandHandler(
    private val meetingService: MeetingService,
    private val agendaService: AgendaService,
    private val permissionGate: PermissionGate,
    private val discordReplyFactory: DiscordReplyFactory,
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
        if (event.subcommandName == SUBCOMMAND_AGENDA_SET_KO || event.subcommandName == SUBCOMMAND_AGENDA_SET_EN) {
            setMeetingAgenda(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_AGENDA_GET_KO || event.subcommandName == SUBCOMMAND_AGENDA_GET_EN) {
            getMeetingAgenda(event)
            return
        }
        discordReplyFactory.invalidInput(event, "지원하지 않는 하위 명령입니다.")
    }

    private fun startMeeting(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (!permissionGate.canAdminAction(guild.idLong, member)) {
            discordReplyFactory.accessDenied(event, "회의 시작 권한이 없습니다.")
            return
        }

        val channelOption = event.getOption(OPTION_CHANNEL_KO)?.asChannel
        if (channelOption == null || channelOption.type != ChannelType.TEXT) {
            discordReplyFactory.invalidInput(event, "텍스트 채널을 지정해 주세요.")
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
                event.reply("회의를 시작했습니다. 세션ID `${result.sessionId}` / 스레드 <#${result.threadId}>")
                    .setEphemeral(true)
                    .queue()
            }

            is MeetingService.StartResult.AlreadyActive -> {
                discordReplyFactory.invalidInput(event, "이미 진행 중인 회의가 있습니다. <#${result.threadId}>")
            }

            MeetingService.StartResult.ChannelNotConfigured -> {
                discordReplyFactory.invalidInput(event, "홈 채널이 설정되지 않았습니다. `/홈 생성`을 먼저 실행해 주세요.")
            }

            MeetingService.StartResult.ChannelNotFound -> {
                discordReplyFactory.invalidInput(event, "회의 스레드를 만들 채널을 찾을 수 없습니다.")
            }

            MeetingService.StartResult.ThreadCreateFailed -> {
                discordReplyFactory.invalidInput(event, "회의 스레드 생성에 실패했습니다.")
            }
        }
    }

    private fun endMeeting(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (!permissionGate.canAdminAction(guild.idLong, member)) {
            discordReplyFactory.accessDenied(event, "회의 종료 권한이 없습니다.")
            return
        }

        val requestedThreadId = parseThreadId(event.getOption(OPTION_THREAD_ID_KO)?.asString)
        val fallbackThreadId = event.channel
            .takeIf { event.channelType.isThread }
            ?.idLong
        event.deferReply(true).queue(
            {
                handleEndMeetingAfterDefer(
                    event = event,
                    guildId = guild.idLong,
                    requestedBy = member.idLong,
                    fallbackThreadId = fallbackThreadId,
                    requestedThreadId = requestedThreadId,
                )
            },
            {
                discordReplyFactory.internalError(event, "응답 초기화에 실패했습니다. 잠시 후 다시 시도해 주세요.")
            },
        )
    }

    private fun setMeetingAgenda(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }

        val url = event.getOption(OPTION_LINK_KO)?.asString ?: event.getOption(OPTION_LINK_EN)?.asString
        if (url.isNullOrBlank()) {
            discordReplyFactory.invalidInput(event, "링크 옵션은 필수입니다.")
            return
        }

        val result = agendaService.setTodayAgenda(
            guildId = guild.idLong,
            requesterUserId = member.idLong,
            requesterRoleIds = member.roles.map { it.idLong }.toSet(),
            hasManageServerPermission = hasManageServerPermission(member),
            rawUrl = url,
            rawTitle = event.getOption(OPTION_TITLE_KO)?.asString ?: event.getOption(OPTION_TITLE_EN)?.asString,
        )
        when (result) {
            is AgendaService.SetAgendaResult.Success -> {
                val action = if (result.updated) "업데이트" else "등록"
                event.reply("회의 안건 링크를 $action 했습니다.\n제목: ${result.title}")
                    .addComponents(ActionRow.of(Button.link(result.url, "회의 안건 링크 열기")))
                    .setEphemeral(true)
                    .queue()
            }

            AgendaService.SetAgendaResult.Forbidden -> {
                discordReplyFactory.accessDenied(event, "회의 안건 등록 권한이 없습니다.")
            }

            AgendaService.SetAgendaResult.InvalidUrl -> {
                discordReplyFactory.invalidInput(event, "URL 형식이 올바르지 않습니다. http/https만 허용됩니다.")
            }

            AgendaService.SetAgendaResult.InvalidTitle -> {
                discordReplyFactory.invalidInput(event, "제목은 255자 이하여야 합니다.")
            }
        }
    }

    private fun getMeetingAgenda(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }

        val agenda = agendaService.getTodayAgenda(guild.idLong)
        if (agenda == null) {
            discordReplyFactory.resourceNotFound(event, "오늘 회의 안건 링크가 아직 등록되지 않았습니다.")
            return
        }

        event.reply("오늘 회의 안건: ${agenda.title}")
            .addComponents(ActionRow.of(Button.link(agenda.url, "회의 안건 링크 열기")))
            .setEphemeral(true)
            .queue()
    }

    private fun handleEndMeetingAfterDefer(
        event: SlashCommandInteractionEvent,
        guildId: Long,
        requestedBy: Long,
        fallbackThreadId: Long?,
        requestedThreadId: Long?,
    ) {
        when (
            val result = meetingService.endMeeting(
                guildId = guildId,
                requestedBy = requestedBy,
                fallbackThreadId = fallbackThreadId,
                requestedThreadId = requestedThreadId,
            )
        ) {
            is MeetingService.EndResult.Success -> {
                val archiveState = if (result.archived) "성공" else "실패(권한/상태 확인 필요)"
                val agendaLine = buildAgendaSummaryLine(result.agendaTitle, result.agendaUrl)
                event.hook.sendMessage(
                    "회의 종료 완료\n세션ID `${result.sessionId}`\n스레드 <#${result.threadId}>\n요약 메시지ID `${result.summaryMessageId}`\n분석 메시지 `${result.sourceMessageCount}`건\n$agendaLine\n결정 `${result.decisions.size}`건 / 액션 `${result.actionItems.size}`건\n아카이브 `${archiveState}`",
                ).setEphemeral(true).queue()
            }

            MeetingService.EndResult.SessionNotFound -> {
                event.hook.sendMessage("종료할 회의 세션을 찾지 못했습니다. 회의 스레드에서 실행하거나 스레드아이디를 지정해 주세요.")
                    .setEphemeral(true)
                    .queue()
            }

            MeetingService.EndResult.AlreadyEnded -> {
                event.hook.sendMessage("이미 종료된 회의입니다.").setEphemeral(true).queue()
            }

            is MeetingService.EndResult.ThreadNotFound -> {
                event.hook.sendMessage("요약 대상 스레드를 찾지 못했습니다. (threadId=${result.threadId})")
                    .setEphemeral(true)
                    .queue()
            }
        }
    }

    private fun parseThreadId(raw: String?): Long? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return raw.trim().toLongOrNull()
    }

    private fun hasManageServerPermission(member: net.dv8tion.jda.api.entities.Member): Boolean {
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true
        }
        return member.hasPermission(Permission.MANAGE_SERVER)
    }

    private fun buildAgendaSummaryLine(title: String?, url: String?): String {
        if (title.isNullOrBlank() || url.isNullOrBlank()) {
            return "연결 안건 `없음`"
        }
        return "연결 안건 `${title}` (${url})"
    }

    companion object {
        private const val COMMAND_NAME_KO = "회의"
        private const val COMMAND_NAME_EN = "meeting"
        private const val SUBCOMMAND_START_KO = "시작"
        private const val SUBCOMMAND_START_EN = "start"
        private const val SUBCOMMAND_END_KO = "종료"
        private const val SUBCOMMAND_END_EN = "end"
        private const val SUBCOMMAND_AGENDA_SET_KO = "안건등록"
        private const val SUBCOMMAND_AGENDA_SET_EN = "agenda-set"
        private const val SUBCOMMAND_AGENDA_GET_KO = "안건조회"
        private const val SUBCOMMAND_AGENDA_GET_EN = "agenda-get"
        private const val OPTION_CHANNEL_KO = "채널"
        private const val OPTION_THREAD_ID_KO = "스레드아이디"
        private const val OPTION_LINK_KO = "링크"
        private const val OPTION_LINK_EN = "url"
        private const val OPTION_TITLE_KO = "제목"
        private const val OPTION_TITLE_EN = "title"
    }
}
