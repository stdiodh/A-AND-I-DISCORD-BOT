package com.aandi.A_AND_I_DISCORD_BOT.assignment.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.auth.AdminPermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentStatus
import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorCode
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorResponse
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.modals.Modal
import org.springframework.stereotype.Component

@Component
class AssignmentSlashCommandHandler(
    private val assignmentTaskService: AssignmentTaskService,
    private val adminPermissionChecker: AdminPermissionChecker,
    private val guildConfigService: GuildConfigService,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND_NAME_KO && event.name != COMMAND_NAME_EN) {
            return
        }

        if (isSubcommand(event, SUBCOMMAND_CREATE_KO, SUBCOMMAND_CREATE_EN)) {
            handleCreate(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_LIST_KO, SUBCOMMAND_LIST_EN)) {
            handleList(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_DETAIL_KO, SUBCOMMAND_DETAIL_EN)) {
            handleDetail(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_DONE_KO, SUBCOMMAND_DONE_EN)) {
            handleDone(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_DELETE_KO, SUBCOMMAND_DELETE_EN)) {
            handleDelete(event)
            return
        }

        replyInvalidInputError(event, "지원하지 않는 하위 명령입니다.", true)
    }

    private fun handleCreate(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.", true)
            return
        }
        if (!ensureAdmin(event, guild.idLong, member)) {
            return
        }

        val title = TextInput.create(OPTION_TITLE_KO, TextInputStyle.SHORT)
            .setRequired(true)
            .setMaxLength(200)
            .build()
        val link = TextInput.create(OPTION_LINK_KO, TextInputStyle.SHORT)
            .setRequired(true)
            .setMaxLength(500)
            .build()
        val remindAt = TextInput.create(OPTION_REMIND_AT_KO, TextInputStyle.SHORT)
            .setRequired(true)
            .setMaxLength(19)
            .build()
        val channel = TextInput.create("채널", TextInputStyle.SHORT)
            .setRequired(false)
            .setMaxLength(40)
            .build()
        val modal = Modal.create("home:task:modal", "과제 등록")
            .addComponents(
                Label.of("과제 제목", title),
                Label.of("검증 링크(http/https)", link),
                Label.of("알림시각 (예: 2026-03-01 21:30)", remindAt),
                Label.of("채널(선택, #멘션 또는 ID)", channel),
            )
            .build()
        event.replyModal(modal).queue()
    }

    private fun handleList(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.", true)
            return
        }

        val rawStatus = event.getOption(OPTION_STATUS_KO)?.asString
        when (val result = assignmentTaskService.list(guild.idLong, rawStatus)) {
            AssignmentTaskService.ListResult.InvalidStatus -> {
                replyInvalidInputError(event, "상태는 대기/완료/취소 중 하나로 입력해 주세요.", true)
            }

            is AssignmentTaskService.ListResult.Success -> {
                if (result.tasks.isEmpty()) {
                    event.reply("조회된 과제가 없습니다.")
                        .setEphemeral(true)
                        .queue()
                    return
                }

                val lines = result.tasks.map {
                    "• ${statusEmoji(it.status)} [${it.id}] ${it.title} | ${KstTime.formatInstantToKst(it.remindAt)} | <@${it.createdBy}>"
                }
                val body = lines.joinToString(separator = "\n")
                val clipped = body
                    .takeIf { it.length <= 1800 }
                    ?: "${body.take(1800)}\n... (생략됨)"
                event.reply("과제 목록\n$clipped")
                    .setEphemeral(false)
                    .queue()
            }
        }
    }

    private fun handleDetail(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.", true)
            return
        }

        val taskId = event.getOption(OPTION_TASK_ID_KO)?.asLong
        if (taskId == null) {
            replyInvalidInputError(event, "과제아이디 옵션이 필요합니다.", true)
            return
        }

        val result = assignmentTaskService.detail(guild.idLong, taskId)
        if (result is AssignmentTaskService.DetailResult.NotFound) {
            replyResourceNotFoundError(event, "해당 과제를 찾을 수 없습니다.", true)
            return
        }

        val task = (result as AssignmentTaskService.DetailResult.Success).task
        val payload = buildString {
            appendLine("과제 상세")
            appendLine("- ID: ${task.id}")
            appendLine("- 상태: ${statusLabel(task.status)}")
            appendLine("- 제목: ${task.title}")
            appendLine("- 알림시각(KST): ${KstTime.formatInstantToKst(task.remindAt)}")
            appendLine("- 등록자: <@${task.createdBy}>")
            append("- 링크: ${task.verifyUrl}")
        }
        event.reply(payload)
            .setEphemeral(true)
            .queue()
    }

    private fun handleDone(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.", true)
            return
        }
        if (!ensureAdmin(event, guild.idLong, member)) {
            return
        }

        val taskId = event.getOption(OPTION_TASK_ID_KO)?.asLong
        if (taskId == null) {
            replyInvalidInputError(event, "과제아이디 옵션이 필요합니다.", true)
            return
        }

        val result = assignmentTaskService.markDone(guild.idLong, taskId)
        if (result is AssignmentTaskService.UpdateResult.NotFound) {
            replyResourceNotFoundError(event, "해당 과제를 찾을 수 없습니다.", true)
            return
        }

        event.reply("과제를 완료 처리했습니다. (ID: $taskId)")
            .setEphemeral(true)
            .queue()
    }

    private fun handleDelete(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.", true)
            return
        }
        if (!ensureAdmin(event, guild.idLong, member)) {
            return
        }

        val taskId = event.getOption(OPTION_TASK_ID_KO)?.asLong
        if (taskId == null) {
            replyInvalidInputError(event, "과제아이디 옵션이 필요합니다.", true)
            return
        }

        val result = assignmentTaskService.cancel(guild.idLong, taskId)
        if (result is AssignmentTaskService.UpdateResult.NotFound) {
            replyResourceNotFoundError(event, "해당 과제를 찾을 수 없습니다.", true)
            return
        }

        event.reply("과제를 삭제(취소) 처리했습니다. (ID: $taskId)")
            .setEphemeral(true)
            .queue()
    }

    private fun ensureAdmin(event: SlashCommandInteractionEvent, guildId: Long, member: net.dv8tion.jda.api.entities.Member): Boolean {
        if (adminPermissionChecker.isAdmin(guildId, member)) {
            return true
        }
        val configuredRole = guildConfigService.getAdminRole(guildId)
        if (configuredRole == null) {
            replyAccessDeniedError(event, "운영진 역할이 아직 설정되지 않았습니다. `/설정 운영진역할 역할:@운영진` 으로 먼저 설정해 주세요.")
            return false
        }
        replyAccessDeniedError(event, "이 명령은 운영진만 사용할 수 있습니다.")
        return false
    }

    private fun statusLabel(status: AssignmentStatus): String = when (status) {
        AssignmentStatus.PENDING -> "대기"
        AssignmentStatus.DONE -> "완료"
        AssignmentStatus.CANCELED -> "취소"
    }

    private fun statusEmoji(status: AssignmentStatus): String = when (status) {
        AssignmentStatus.PENDING -> "🕒"
        AssignmentStatus.DONE -> "✅"
        AssignmentStatus.CANCELED -> "❌"
    }

    private fun isSubcommand(
        event: SlashCommandInteractionEvent,
        ko: String,
        en: String,
    ): Boolean = event.subcommandName == ko || event.subcommandName == en

    private fun replyInvalidInputError(event: SlashCommandInteractionEvent, message: String, ephemeral: Boolean) {
        replyError(
            event = event,
            code = DiscordErrorCode.COMMON_INVALID_INPUT,
            message = message,
            ephemeral = ephemeral,
        )
    }

    private fun replyAccessDeniedError(event: SlashCommandInteractionEvent, message: String) {
        replyError(
            event = event,
            code = DiscordErrorCode.ACCESS_DENIED,
            message = message,
            ephemeral = true,
        )
    }

    private fun replyResourceNotFoundError(event: SlashCommandInteractionEvent, message: String, ephemeral: Boolean) {
        replyError(
            event = event,
            code = DiscordErrorCode.RESOURCE_NOT_FOUND,
            message = message,
            ephemeral = ephemeral,
        )
    }

    private fun replyError(
        event: SlashCommandInteractionEvent,
        code: DiscordErrorCode,
        message: String,
        ephemeral: Boolean,
    ) {
        val payload = DiscordErrorFormatter.format(
            DiscordErrorResponse(
                code = code,
                message = message,
                retryable = false,
            ),
        )
        event.reply(payload)
            .setEphemeral(ephemeral)
            .queue()
    }

    companion object {
        private const val COMMAND_NAME_KO = "과제"
        private const val COMMAND_NAME_EN = "assignment"
        private const val SUBCOMMAND_CREATE_KO = "등록"
        private const val SUBCOMMAND_CREATE_EN = "create"
        private const val SUBCOMMAND_LIST_KO = "목록"
        private const val SUBCOMMAND_LIST_EN = "list"
        private const val SUBCOMMAND_DETAIL_KO = "상세"
        private const val SUBCOMMAND_DETAIL_EN = "detail"
        private const val SUBCOMMAND_DONE_KO = "완료"
        private const val SUBCOMMAND_DONE_EN = "done"
        private const val SUBCOMMAND_DELETE_KO = "삭제"
        private const val SUBCOMMAND_DELETE_EN = "delete"
        private const val OPTION_TITLE_KO = "제목"
        private const val OPTION_LINK_KO = "링크"
        private const val OPTION_REMIND_AT_KO = "알림시각"
        private const val OPTION_STATUS_KO = "상태"
        private const val OPTION_TASK_ID_KO = "과제아이디"
    }
}
