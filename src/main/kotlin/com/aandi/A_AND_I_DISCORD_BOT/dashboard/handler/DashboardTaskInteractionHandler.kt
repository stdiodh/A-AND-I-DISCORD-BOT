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
            .setPlaceholder("예) 3주차 API 과제 제출")
            .setMaxLength(200)
            .build()
        val link = TextInput.create("링크", TextInputStyle.SHORT)
            .setRequired(true)
            .setPlaceholder("예) https://lms.example.com/tasks/123")
            .setMaxLength(500)
            .build()
        val remindAt = TextInput.create("알림시각", TextInputStyle.SHORT)
            .setRequired(true)
            .setPlaceholder("예) 2026-03-01 21:30")
            .setMaxLength(19)
            .build()
        val dueAt = TextInput.create("마감시각", TextInputStyle.SHORT)
            .setRequired(true)
            .setPlaceholder("예) 2026-03-02 23:59")
            .setMaxLength(19)
            .build()
        val channelId = TextInput.create("채널", TextInputStyle.SHORT)
            .setRequired(false)
            .setPlaceholder("예) #과제공지 또는 123456789012345678")
            .setMaxLength(40)
            .build()
        val modal = Modal.create(DashboardActionIds.ASSIGNMENT_MODAL, "과제 빠른 등록")
            .addComponents(
                Label.of("과제 제목", title),
                Label.of("검증 링크(http/https)", link),
                Label.of("알림시각(KST)", remindAt),
                Label.of("마감시각(KST)", dueAt),
                Label.of("채널(선택)", channelId),
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
            val role = it.notifyRoleId?.let { roleId -> "<@&$roleId>" } ?: "없음"
            "• [${it.id}] ${it.title} | 알림:${KstTime.formatInstantToKst(it.remindAt)} | 마감:${KstTime.formatInstantToKst(it.dueAt)} | 역할:$role"
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
        val dueRaw = event.getValue("마감시각")?.asString.orEmpty()
        val remindAtUtc = runCatching { KstTime.parseKstToInstant(remindRaw) }.getOrElse {
            event.reply("알림시각 형식이 올바르지 않습니다. 예: 2026-03-01 21:30")
                .setEphemeral(true)
                .queue()
            return
        }
        val dueAtUtc = runCatching { KstTime.parseKstToInstant(dueRaw) }.getOrElse {
            event.reply("마감시각 형식이 올바르지 않습니다. 예: 2026-03-02 23:59")
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
            dueAtUtc = dueAtUtc,
            createdBy = member.idLong,
            nowUtc = Instant.now(clock),
            notifyRoleId = null,
            preReminderHoursRaw = null,
            closingMessageRaw = null,
        )
        when (result) {
            is AssignmentTaskService.CreateResult.Success -> {
                val task = result.task
                event.reply(
                    "과제를 등록했습니다. ID: ${task.id}\n알림시각(KST): ${KstTime.formatInstantToKst(task.remindAt)}\n마감시각(KST): ${KstTime.formatInstantToKst(task.dueAt)}\n※ 빠른 등록은 알림역할/종료메시지 기본값이 적용됩니다.",
                )
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

            AssignmentTaskService.CreateResult.DueAtMustBeFuture -> {
                event.reply("마감시각은 현재보다 미래여야 합니다.").setEphemeral(true).queue()
            }

            AssignmentTaskService.CreateResult.DueAtMustBeAfterRemindAt -> {
                event.reply("마감시각은 알림시각 이후여야 합니다.").setEphemeral(true).queue()
            }

            AssignmentTaskService.CreateResult.InvalidPreReminderHours -> {
                event.reply("임박알림 형식이 올바르지 않습니다. 예: 24,3,1").setEphemeral(true).queue()
            }

            AssignmentTaskService.CreateResult.InvalidClosingMessage -> {
                event.reply("종료메시지는 500자 이하여야 합니다.").setEphemeral(true).queue()
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
