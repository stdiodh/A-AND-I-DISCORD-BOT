package com.aandi.A_AND_I_DISCORD_BOT.agenda.handler

import com.aandi.A_AND_I_DISCORD_BOT.agenda.service.AgendaService
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorCode
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorResponse
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class AgendaSlashCommandHandler(
    private val agendaService: AgendaService,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != "agenda") {
            return
        }

        if (event.subcommandName == "set") {
            handleSet(event)
            return
        }
        if (event.subcommandName == "today") {
            handleToday(event)
            return
        }
        if (event.subcommandName == "recent") {
            handleRecent(event)
            return
        }

        replyInvalidInputError(event, "지원하지 않는 하위 명령입니다.", true)
    }

    private fun handleSet(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyGuildOnlyError(event)
            return
        }

        val url = event.getOption("url")?.asString
        if (url.isNullOrBlank()) {
            replyInvalidInputError(event, "url 옵션은 필수입니다.", true)
            return
        }

        val hasManageServerPermission = hasManageServerPermission(member)
        val result = agendaService.setTodayAgenda(
            guildId = guild.idLong,
            requesterUserId = member.idLong,
            requesterRoleIds = member.roles.map { it.idLong }.toSet(),
            hasManageServerPermission = hasManageServerPermission,
            rawUrl = url,
            rawTitle = event.getOption("title")?.asString,
        )

        when (result) {
            is AgendaService.SetAgendaResult.Success -> {
                val action = updateActionLabel(result.updated)
                event.reply("오늘 안건 링크를 $action 했습니다.\n제목: ${result.title}")
                    .addComponents(ActionRow.of(Button.link(result.url, "안건 링크 열기")))
                    .setEphemeral(true)
                    .queue()
            }

            AgendaService.SetAgendaResult.Forbidden -> {
                replyAccessDeniedError(event, true)
            }

            AgendaService.SetAgendaResult.InvalidUrl -> {
                replyInvalidInputError(event, "URL 형식이 올바르지 않습니다. http/https만 허용됩니다.", true)
            }

            AgendaService.SetAgendaResult.InvalidTitle -> {
                replyInvalidInputError(event, "title은 255자 이하여야 합니다.", true)
            }
        }
    }

    private fun handleToday(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            replyGuildOnlyError(event)
            return
        }

        val agenda = agendaService.getTodayAgenda(guild.idLong)
        if (agenda == null) {
            replyResourceNotFoundError(event, "아직 등록되지 않았습니다.", false)
            return
        }

        val embed = EmbedBuilder()
            .setTitle("오늘 안건")
            .setDescription(agenda.title)
            .addField("날짜", agenda.dateLocal.toString(), true)
            .setColor(Color(0x2B2D31))
            .build()

        event.replyEmbeds(embed)
            .addComponents(ActionRow.of(Button.link(agenda.url, "안건 링크 열기")))
            .queue()
    }

    private fun handleRecent(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            replyGuildOnlyError(event)
            return
        }

        val days = event.getOption("days")?.asInt ?: DEFAULT_RECENT_DAYS
        val result = agendaService.getRecentAgendas(guild.idLong, days)
        when (result) {
            is AgendaService.RecentAgendaResult.Success -> {
                val lines = result.agendas.map {
                    "- ${it.dateLocal}: ${displayTitle(it.title)} - ${it.url}"
                }
                event.reply("최근 ${days}일 안건 링크\n${lines.joinToString(separator = "\n")}")
                    .setEphemeral(false)
                    .queue()
            }

            AgendaService.RecentAgendaResult.Empty -> {
                replyResourceNotFoundError(event, "최근 안건 링크가 없습니다.", false)
            }

            AgendaService.RecentAgendaResult.InvalidDays -> {
                replyInvalidInputError(event, "days는 1 이상의 정수여야 합니다.", true)
            }
        }
    }

    private fun hasManageServerPermission(member: net.dv8tion.jda.api.entities.Member): Boolean {
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true
        }
        return member.hasPermission(Permission.MANAGE_SERVER)
    }

    private fun updateActionLabel(updated: Boolean): String {
        if (updated) {
            return "업데이트"
        }
        return "등록"
    }

    private fun displayTitle(title: String): String {
        if (title.isBlank()) {
            return "오늘 안건"
        }
        return title
    }

    private fun replyGuildOnlyError(event: SlashCommandInteractionEvent) {
        replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.", true)
    }

    private fun replyInvalidInputError(event: SlashCommandInteractionEvent, message: String, ephemeral: Boolean) {
        replyError(
            event = event,
            code = DiscordErrorCode.COMMON_INVALID_INPUT,
            message = message,
            retryable = false,
            ephemeral = ephemeral,
        )
    }

    private fun replyAccessDeniedError(event: SlashCommandInteractionEvent, ephemeral: Boolean) {
        replyError(
            event = event,
            code = DiscordErrorCode.ACCESS_DENIED,
            message = "이 명령은 운영진만 사용할 수 있습니다.",
            retryable = false,
            ephemeral = ephemeral,
        )
    }

    private fun replyResourceNotFoundError(event: SlashCommandInteractionEvent, message: String, ephemeral: Boolean) {
        replyError(
            event = event,
            code = DiscordErrorCode.RESOURCE_NOT_FOUND,
            message = message,
            retryable = false,
            ephemeral = ephemeral,
        )
    }

    private fun replyError(
        event: SlashCommandInteractionEvent,
        code: DiscordErrorCode,
        message: String,
        retryable: Boolean,
        ephemeral: Boolean,
    ) {
        val payload = DiscordErrorFormatter.format(
            DiscordErrorResponse(
                code = code,
                message = message,
                retryable = retryable,
            ),
        )
        event.reply(payload)
            .setEphemeral(ephemeral)
            .queue()
    }

    companion object {
        private const val DEFAULT_RECENT_DAYS = 7
    }
}
