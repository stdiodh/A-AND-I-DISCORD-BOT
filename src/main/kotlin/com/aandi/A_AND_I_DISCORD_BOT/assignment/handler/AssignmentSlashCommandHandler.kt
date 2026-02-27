package com.aandi.A_AND_I_DISCORD_BOT.assignment.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.auth.AdminPermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentStatus
import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorCode
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorResponse
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class AssignmentSlashCommandHandler(
    private val assignmentTaskService: AssignmentTaskService,
    private val adminPermissionChecker: AdminPermissionChecker,
    private val guildConfigService: GuildConfigService,
    private val clock: Clock,
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

        val title = event.getOption(OPTION_TITLE_KO)?.asString
        val link = event.getOption(OPTION_LINK_KO)?.asString
        val remindAtRaw = event.getOption(OPTION_REMIND_AT_KO)?.asString
        val dueAtRaw = event.getOption(OPTION_DUE_AT_KO)?.asString
        if (title.isNullOrBlank() || link.isNullOrBlank() || remindAtRaw.isNullOrBlank() || dueAtRaw.isNullOrBlank()) {
            replyInvalidInputError(event, "제목/링크/알림시각/마감시각은 필수입니다.", true)
            return
        }

        val remindAtUtc = runCatching { KstTime.parseKstToInstant(remindAtRaw) }.getOrElse {
            replyInvalidInputError(event, "알림시각 형식이 올바르지 않습니다. 예: 2026-03-01 21:30", true)
            return
        }
        val dueAtUtc = runCatching { KstTime.parseKstToInstant(dueAtRaw) }.getOrElse {
            replyInvalidInputError(event, "마감시각 형식이 올바르지 않습니다. 예: 2026-03-02 23:59", true)
            return
        }

        val channelId = event.getOption(OPTION_CHANNEL_KO)?.asChannel?.idLong ?: event.channel.idLong
        val notifyRoleId = event.getOption(OPTION_NOTIFY_ROLE_KO)?.asRole?.idLong
        val preReminderHoursRaw = event.getOption(OPTION_PRE_REMIND_KO)?.asString
        val closingMessageRaw = event.getOption(OPTION_CLOSING_MESSAGE_KO)?.asString

        val result = assignmentTaskService.create(
            guildId = guild.idLong,
            channelId = channelId,
            title = title,
            verifyUrl = link,
            remindAtUtc = remindAtUtc,
            dueAtUtc = dueAtUtc,
            createdBy = member.idLong,
            nowUtc = Instant.now(clock),
            notifyRoleId = notifyRoleId,
            preReminderHoursRaw = preReminderHoursRaw,
            closingMessageRaw = closingMessageRaw,
        )

        when (result) {
            is AssignmentTaskService.CreateResult.Success -> {
                val task = result.task
                val roleDisplay = task.notifyRoleId?.let { "<@&$it>" } ?: "없음"
                val preHours = task.preRemindHours.sortedDescending().joinToString(",")
                event.reply(
                    "과제를 등록했습니다.\nID: `${task.id}`\n알림시각(KST): `${KstTime.formatInstantToKst(task.remindAt)}`\n마감시각(KST): `${KstTime.formatInstantToKst(task.dueAt)}`\n알림역할: $roleDisplay\n임박알림(시간): `$preHours`",
                )
                    .setEphemeral(true)
                    .queue()
            }

            AssignmentTaskService.CreateResult.InvalidUrl -> {
                replyInvalidInputError(event, "링크는 http/https만 허용됩니다.", true)
            }

            AssignmentTaskService.CreateResult.InvalidTitle -> {
                replyInvalidInputError(event, "제목은 1~200자여야 합니다.", true)
            }

            AssignmentTaskService.CreateResult.RemindAtMustBeFuture -> {
                replyInvalidInputError(event, "알림시각은 현재보다 미래여야 합니다.", true)
            }

            AssignmentTaskService.CreateResult.DueAtMustBeFuture -> {
                replyInvalidInputError(event, "마감시각은 현재보다 미래여야 합니다.", true)
            }

            AssignmentTaskService.CreateResult.DueAtMustBeAfterRemindAt -> {
                replyInvalidInputError(event, "마감시각은 알림시각 이후여야 합니다.", true)
            }

            AssignmentTaskService.CreateResult.InvalidPreReminderHours -> {
                replyInvalidInputError(event, "임박알림 형식이 올바르지 않습니다. 예: 24,3,1", true)
            }

            AssignmentTaskService.CreateResult.InvalidClosingMessage -> {
                replyInvalidInputError(event, "종료메시지는 500자 이하여야 합니다.", true)
            }
        }
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
                replyInvalidInputError(event, "상태는 대기/완료/종료 중 하나로 입력해 주세요.", true)
            }

            AssignmentTaskService.ListResult.HiddenDeleted -> {
                replyInvalidInputError(event, "삭제(취소)된 과제는 목록에서 조회할 수 없습니다.", true)
            }

            is AssignmentTaskService.ListResult.Success -> {
                if (result.tasks.isEmpty()) {
                    event.reply("조회된 과제가 없습니다.")
                        .setEphemeral(true)
                        .queue()
                    return
                }

                val lines = result.tasks.take(10).map {
                    val role = it.notifyRoleId?.let { roleId -> "<@&$roleId>" } ?: "없음"
                    "${statusEmoji(it.status)} [${it.id}] ${it.title} | 알림:${KstTime.formatInstantToKst(it.remindAt)} | 마감:${KstTime.formatInstantToKst(it.dueAt)} | 역할:$role"
                }
                val body = lines.joinToString(separator = "\n")
                val clipped = body.takeIf { it.length <= 1800 } ?: "${body.take(1800)}\n... (생략됨)"
                event.reply("과제 목록(최대 10건)\n$clipped")
                    .setEphemeral(true)
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
        val preHours = task.preRemindHours.sortedDescending().joinToString(",")
        val roleDisplay = task.notifyRoleId?.let { "<@&$it>" } ?: "없음"
        val closingMessage = task.closingMessage ?: "(기본 메시지 사용)"
        val payload = buildString {
            appendLine("과제 상세")
            appendLine("- ID: ${task.id}")
            appendLine("- 상태: ${statusLabel(task.status)}")
            appendLine("- 제목: ${task.title}")
            appendLine("- 알림시각(KST): ${KstTime.formatInstantToKst(task.remindAt)}")
            appendLine("- 마감시각(KST): ${KstTime.formatInstantToKst(task.dueAt)}")
            appendLine("- 알림역할: $roleDisplay")
            appendLine("- 임박알림(시간): $preHours")
            appendLine("- 종료메시지: $closingMessage")
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
            replyAccessDeniedError(event, "운영진 역할이 아직 설정되지 않았습니다. `/설정 운영진역할`에서 `대상역할`을 선택해 먼저 설정해 주세요.")
            return false
        }
        replyAccessDeniedError(event, "이 명령은 운영진만 사용할 수 있습니다.")
        return false
    }

    private fun statusLabel(status: AssignmentStatus): String = when (status) {
        AssignmentStatus.PENDING -> "대기"
        AssignmentStatus.DONE -> "완료"
        AssignmentStatus.CANCELED -> "취소"
        AssignmentStatus.CLOSED -> "종료"
    }

    private fun statusEmoji(status: AssignmentStatus): String = when (status) {
        AssignmentStatus.PENDING -> "🕒"
        AssignmentStatus.DONE -> "✅"
        AssignmentStatus.CANCELED -> "❌"
        AssignmentStatus.CLOSED -> "🏁"
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
        private const val OPTION_DUE_AT_KO = "마감시각"
        private const val OPTION_CHANNEL_KO = "채널"
        private const val OPTION_NOTIFY_ROLE_KO = "알림역할"
        private const val OPTION_PRE_REMIND_KO = "임박알림"
        private const val OPTION_CLOSING_MESSAGE_KO = "종료메시지"
        private const val OPTION_STATUS_KO = "상태"
        private const val OPTION_TASK_ID_KO = "과제아이디"
    }
}
