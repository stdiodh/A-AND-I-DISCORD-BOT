package com.aandi.A_AND_I_DISCORD_BOT.discord

import com.aandi.A_AND_I_DISCORD_BOT.agenda.service.AgendaService
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class AgendaSlashCommandListener(
    private val agendaService: AgendaService,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != "agenda") {
            return
        }

        when (event.subcommandName) {
            "set" -> handleSet(event)
            "today" -> handleToday(event)
            else -> event.reply("지원하지 않는 하위 명령입니다.")
                .setEphemeral(true)
                .queue()
        }
    }

    private fun handleSet(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val url = event.getOption("url")?.asString
        if (url.isNullOrBlank()) {
            event.reply("url 옵션은 필수입니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val title = event.getOption("title")?.asString
        val roleIds = member.roles.map { it.idLong }.toSet()
        when (
            val result = agendaService.setTodayAgenda(
                guildId = guild.idLong,
                requesterUserId = member.idLong,
                requesterRoleIds = roleIds,
                rawUrl = url,
                rawTitle = title,
            )
        ) {
            is AgendaService.SetAgendaResult.Success -> {
                val action = if (result.updated) "업데이트" else "등록"
                event.reply("오늘 안건 링크를 $action 했습니다.\n제목: ${result.title}\nURL: ${result.url}")
                    .setEphemeral(true)
                    .queue()
            }

            AgendaService.SetAgendaResult.MissingGuildConfig -> {
                event.reply("guild_config가 없어 처리할 수 없습니다. 먼저 서버 설정을 등록해 주세요.")
                    .setEphemeral(true)
                    .queue()
            }

            AgendaService.SetAgendaResult.AdminRoleNotConfigured -> {
                event.reply("guild_config.admin_role_id가 설정되지 않았습니다.")
                    .setEphemeral(true)
                    .queue()
            }

            AgendaService.SetAgendaResult.Forbidden -> {
                event.reply("운영진만 사용할 수 있습니다.")
                    .setEphemeral(true)
                    .queue()
            }

            AgendaService.SetAgendaResult.InvalidUrl -> {
                event.reply("URL 형식이 올바르지 않습니다. http/https만 허용됩니다.")
                    .setEphemeral(true)
                    .queue()
            }

            AgendaService.SetAgendaResult.InvalidTitle -> {
                event.reply("title은 1~255자여야 합니다.")
                    .setEphemeral(true)
                    .queue()
            }
        }
    }

    private fun handleToday(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            event.reply("길드에서만 사용할 수 있습니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val agenda = agendaService.getTodayAgenda(guild.idLong)
        if (agenda == null) {
            event.reply("아직 등록되지 않았습니다.")
                .queue()
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
}
