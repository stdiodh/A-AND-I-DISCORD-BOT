package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
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
import java.time.Clock
import java.time.Instant

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class DashboardTaskInteractionHandler(
    private val permissionGate: PermissionGate,
    private val assignmentTaskService: AssignmentTaskService,
    private val clock: Clock,
) : InteractionPrefixHandler {

    override fun supports(prefix: String): Boolean {
        return prefix in supportedPrefixes
    }

    override fun onButton(event: ButtonInteractionEvent): Boolean {
        if (event.componentId == DashboardActionIds.ASSIGNMENT_CREATE) {
            showTaskModal(event)
            return true
        }
        if (event.componentId == DashboardActionIds.ASSIGNMENT_LIST) {
            showTaskList(event)
            return true
        }

        val parsed = HomeCustomIdParser.parse(event.componentId) ?: return false
        if (parsed.domain == "task" && parsed.action == "create") {
            showTaskModal(event)
            return true
        }
        if (parsed.domain == "task" && parsed.action == "list") {
            showTaskList(event)
            return true
        }
        return false
    }

    override fun onModal(event: ModalInteractionEvent): Boolean {
        if (event.modalId == DashboardActionIds.ASSIGNMENT_MODAL) {
            submitTaskCreate(event)
            return true
        }

        val parsed = HomeCustomIdParser.parse(event.modalId) ?: return false
        if (parsed.domain != "task" || parsed.action != "modal") {
            return false
        }

        submitTaskCreate(event)
        return true
    }

    private fun showTaskModal(event: ButtonInteractionEvent) {
        val title = TextInput.create("제목", TextInputStyle.SHORT)
            .setRequired(true)
            .setMaxLength(200)
            .build()
        val link = TextInput.create("링크", TextInputStyle.SHORT)
            .setRequired(true)
            .setMaxLength(500)
            .build()
        val remindAt = TextInput.create("알림시각", TextInputStyle.SHORT)
            .setRequired(true)
            .setMaxLength(19)
            .build()
        val channelId = TextInput.create("채널", TextInputStyle.SHORT)
            .setRequired(false)
            .setMaxLength(40)
            .build()
        val modal = Modal.create(DashboardActionIds.ASSIGNMENT_MODAL, "과제 등록")
            .addComponents(
                Label.of("과제 제목", title),
                Label.of("검증 링크(http/https)", link),
                Label.of("알림시각 (예: 2026-03-01 21:30)", remindAt),
                Label.of("채널(선택, #멘션 또는 ID)", channelId),
            )
            .build()
        event.replyModal(modal).queue()
    }

    private fun showTaskList(event: ButtonInteractionEvent) {
        val guild = event.guild ?: run {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        val result = assignmentTaskService.list(guild.idLong, null)
        if (result is AssignmentTaskService.ListResult.InvalidStatus) {
            event.reply("과제 상태값이 올바르지 않습니다.").setEphemeral(true).queue()
            return
        }

        val tasks = (result as AssignmentTaskService.ListResult.Success).tasks
        if (tasks.isEmpty()) {
            event.reply("등록된 과제가 없습니다.").setEphemeral(true).queue()
            return
        }

        val lines = tasks.take(10).map {
            "• [${it.id}] ${it.title} | ${KstTime.formatInstantToKst(it.remindAt)} | <@${it.createdBy}>"
        }
        event.reply("과제 목록(최대 10건)\n${lines.joinToString("\n")}")
            .setEphemeral(true)
            .queue()
    }

    private fun submitTaskCreate(event: ModalInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!permissionGate.canAdminAction(guild.idLong, member)) {
            event.reply("과제 등록 권한이 없습니다.").setEphemeral(true).queue()
            return
        }

        val title = event.getValue("제목")?.asString.orEmpty()
        val link = event.getValue("링크")?.asString.orEmpty()
        val remindRaw = event.getValue("알림시각")?.asString.orEmpty()
        val remindAtUtc = runCatching { KstTime.parseKstToInstant(remindRaw) }.getOrElse {
            event.reply("알림시각 형식이 올바르지 않습니다. 예: 2026-03-01 21:30")
                .setEphemeral(true)
                .queue()
            return
        }

        val channelRaw = event.getValue("채널")?.asString?.trim().orEmpty()
        val channelId = parseChannelId(channelRaw) ?: event.channel.idLong
        val result = assignmentTaskService.create(
            guildId = guild.idLong,
            channelId = channelId,
            title = title,
            verifyUrl = link,
            remindAtUtc = remindAtUtc,
            createdBy = member.idLong,
            nowUtc = Instant.now(clock),
        )
        when (result) {
            is AssignmentTaskService.CreateResult.Success -> {
                val task = result.task
                event.reply("과제를 등록했습니다. ID: ${task.id}, 알림시각(KST): ${KstTime.formatInstantToKst(task.remindAt)}")
                    .setEphemeral(true)
                    .queue()
            }

            AssignmentTaskService.CreateResult.InvalidUrl -> {
                event.reply("링크는 http/https만 허용됩니다.").setEphemeral(true).queue()
            }

            AssignmentTaskService.CreateResult.InvalidTitle -> {
                event.reply("제목은 1~200자여야 합니다.").setEphemeral(true).queue()
            }

            AssignmentTaskService.CreateResult.RemindAtMustBeFuture -> {
                event.reply("알림시각은 현재보다 미래여야 합니다.").setEphemeral(true).queue()
            }
        }
    }

    private fun parseChannelId(raw: String): Long? {
        if (raw.isBlank()) {
            return null
        }
        if (raw.startsWith("<#") && raw.endsWith(">")) {
            return raw.removePrefix("<#").removeSuffix(">").toLongOrNull()
        }
        return raw.toLongOrNull()
    }

    companion object {
        private val supportedPrefixes = setOf("dash", "assign", "home")
    }
}
