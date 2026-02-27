package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.agenda.service.AgendaService
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeCustomIdParser
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionPrefixHandler
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.modals.Modal
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class DashboardAgendaInteractionHandler(
    private val permissionGate: PermissionGate,
    private val agendaService: AgendaService,
) : InteractionPrefixHandler {

    override fun supports(prefix: String): Boolean {
        return prefix in supportedPrefixes
    }

    override fun onButton(event: ButtonInteractionEvent): Boolean {
        if (event.componentId == DashboardActionIds.AGENDA_SET) {
            showAgendaModal(event)
            return true
        }

        val parsed = HomeCustomIdParser.parse(event.componentId) ?: return false
        if (parsed.domain != "agenda" || parsed.action != "set") {
            return false
        }

        showAgendaModal(event)
        return true
    }

    override fun onModal(event: ModalInteractionEvent): Boolean {
        if (event.modalId == DashboardActionIds.AGENDA_MODAL) {
            submitAgendaSet(event)
            return true
        }

        val parsed = HomeCustomIdParser.parse(event.modalId) ?: return false
        if (parsed.domain != "agenda" || parsed.action != "modal") {
            return false
        }

        submitAgendaSet(event)
        return true
    }

    private fun showAgendaModal(event: ButtonInteractionEvent) {
        val link = TextInput.create("링크", TextInputStyle.SHORT)
            .setRequired(true)
            .setPlaceholder("https://docs.google.com/...")
            .setMaxLength(500)
            .build()
        val title = TextInput.create("제목", TextInputStyle.SHORT)
            .setRequired(false)
            .setPlaceholder("오늘 안건")
            .setMaxLength(255)
            .build()
        val modal = Modal.create(DashboardActionIds.AGENDA_MODAL, "안건 설정")
            .addComponents(
                Label.of("안건 링크", link),
                Label.of("안건 제목(선택)", title),
            )
            .build()
        event.replyModal(modal).queue()
    }

    private fun submitAgendaSet(event: ModalInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!permissionGate.canAdminAction(guild.idLong, member)) {
            event.reply("안건 설정 권한이 없습니다.").setEphemeral(true).queue()
            return
        }

        val url = event.getValue("링크")?.asString
        if (url.isNullOrBlank()) {
            event.reply("링크는 필수입니다.").setEphemeral(true).queue()
            return
        }

        val result = agendaService.setTodayAgenda(
            guildId = guild.idLong,
            requesterUserId = member.idLong,
            requesterRoleIds = member.roles.map { it.idLong }.toSet(),
            hasManageServerPermission = permissionGate.canAdminAction(guild.idLong, member),
            rawUrl = url,
            rawTitle = event.getValue("제목")?.asString,
        )
        when (result) {
            is AgendaService.SetAgendaResult.Success -> {
                event.reply("오늘 안건 링크를 저장했습니다: ${result.title}")
                    .setEphemeral(true)
                    .queue()
            }

            AgendaService.SetAgendaResult.Forbidden -> {
                event.reply("안건 설정 권한이 없습니다.").setEphemeral(true).queue()
            }

            AgendaService.SetAgendaResult.InvalidUrl -> {
                event.reply("URL 형식이 올바르지 않습니다.").setEphemeral(true).queue()
            }

            AgendaService.SetAgendaResult.InvalidTitle -> {
                event.reply("제목 길이가 너무 깁니다.").setEphemeral(true).queue()
            }
        }
    }

    companion object {
        private val supportedPrefixes = setOf("dash", "meeting", "home")
    }
}
