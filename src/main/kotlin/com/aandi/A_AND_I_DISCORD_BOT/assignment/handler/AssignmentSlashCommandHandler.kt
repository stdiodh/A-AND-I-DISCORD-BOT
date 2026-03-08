package com.aandi.A_AND_I_DISCORD_BOT.assignment.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.auth.AdminPermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentStatus
import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.HomeChannelGuard
import com.aandi.A_AND_I_DISCORD_BOT.common.config.FeatureFlagsProperties
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorCode
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorResponse
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeCustomIdParser
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.modals.Modal
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class AssignmentSlashCommandHandler(
    private val assignmentTaskService: AssignmentTaskService,
    private val adminPermissionChecker: AdminPermissionChecker,
    private val guildConfigService: GuildConfigService,
    private val homeChannelGuard: HomeChannelGuard,
    private val featureFlags: FeatureFlagsProperties,
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
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        if (!ensureAdmin(event, guild.idLong, member)) {
            return
        }

        if (shouldOpenQuickRegisterModal(event)) {
            openQuickRegisterModal(event)
            return
        }

        val title = event.getOption(OPTION_TITLE_KO)?.asString?.trim()
        val link = event.getOption(OPTION_LINK_KO)?.asString?.trim()?.takeIf { it.isNotBlank() }
        val remindAtRaw = resolveRemindAtOption(event)
        val dueAtRaw = resolveDueAtOption(event)
        if (title.isNullOrBlank() || remindAtRaw.isNullOrBlank() || dueAtRaw.isNullOrBlank()) {
            replyInvalidInputError(event, "레거시 등록은 `제목/알림/마감`이 필수입니다. 옵션 없이 `/과제 등록`을 실행하면 V2 모달이 열립니다.", true)
            return
        }

        val remindAtUtc = runCatching { KstTime.parseKstToInstant(remindAtRaw) }.getOrElse {
            replyInvalidInputError(event, "알림 형식이 올바르지 않습니다. 예: 2026-03-01 21:30", true)
            return
        }
        val dueAtUtc = runCatching { KstTime.parseKstToInstant(dueAtRaw) }.getOrElse {
            replyInvalidInputError(event, "마감 형식이 올바르지 않습니다. 예: 2026-03-02 23:59", true)
            return
        }

        val boardChannels = guildConfigService.getBoardChannels(guild.idLong)
        val channelId = event.getOption(OPTION_CHANNEL_KO)?.asChannel?.idLong
            ?: boardChannels.assignmentChannelId
            ?: event.channel.idLong
        val notifyRoleId = resolveNotifyRoleOption(event)?.idLong
        val preReminderHoursRaw = resolvePreReminderOption(event)
        val closingMessageRaw = resolveClosingMessageOption(event)

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
                    "과제를 등록했습니다.\nID: `${task.id}`\n알림(KST): `${KstTime.formatInstantToKst(task.remindAt)}`\n마감(KST): `${KstTime.formatInstantToKst(task.dueAt)}`\n역할: $roleDisplay\n임박알림(시간): `$preHours`",
                )
                    .addComponents(
                        net.dv8tion.jda.api.components.actionrow.ActionRow.of(
                            Button.secondary(DashboardActionIds.ASSIGNMENT_LIST, "목록 보기"),
                            Button.secondary(HomeCustomIdParser.of("task", "detail", task.id.toString()), "상세 보기"),
                            Button.secondary(HomeCustomIdParser.of("task", "edit", task.id.toString()), "수정"),
                        ),
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
                replyInvalidInputError(event, "알림은 현재보다 미래여야 합니다.", true)
            }

            AssignmentTaskService.CreateResult.DueAtMustBeFuture -> {
                replyInvalidInputError(event, "마감은 현재보다 미래여야 합니다.", true)
            }

            AssignmentTaskService.CreateResult.DueAtMustBeAfterRemindAt -> {
                replyInvalidInputError(event, "마감은 알림 이후여야 합니다.", true)
            }

            AssignmentTaskService.CreateResult.InvalidPreReminderHours -> {
                replyInvalidInputError(event, "임박알림 형식이 올바르지 않습니다. 예: 24,3,1", true)
            }

            AssignmentTaskService.CreateResult.InvalidClosingMessage -> {
                replyInvalidInputError(event, "마감메시지는 500자 이하여야 합니다.", true)
            }
        }
    }

    private fun handleList(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.", true)
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
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
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }

        val taskId = resolveTaskIdOption(event)
        if (taskId == null) {
            replyInvalidInputError(event, "아이디 옵션이 필요합니다.", true)
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
            appendLine("- 알림(KST): ${KstTime.formatInstantToKst(task.remindAt)}")
            appendLine("- 마감(KST): ${KstTime.formatInstantToKst(task.dueAt)}")
            appendLine("- 역할: $roleDisplay")
            appendLine("- 임박알림(시간): $preHours")
            appendLine("- 마감메시지: $closingMessage")
            appendLine("- 등록자: <@${task.createdBy}>")
            append("- 링크: ${task.verifyUrl ?: "(미입력)"}")
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
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        if (!ensureAdmin(event, guild.idLong, member)) {
            return
        }

        val taskId = resolveTaskIdOption(event)
        if (taskId == null) {
            replyInvalidInputError(event, "아이디 옵션이 필요합니다.", true)
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
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        if (!ensureAdmin(event, guild.idLong, member)) {
            return
        }

        val taskId = resolveTaskIdOption(event)
        if (taskId == null) {
            replyInvalidInputError(event, "아이디 옵션이 필요합니다.", true)
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

    private fun shouldOpenQuickRegisterModal(event: SlashCommandInteractionEvent): Boolean {
        if (!featureFlags.taskQuickregisterV2) {
            return false
        }
        return !hasLegacyCreateInputs(event)
    }

    private fun hasLegacyCreateInputs(event: SlashCommandInteractionEvent): Boolean {
        return listOf(
            OPTION_TITLE_KO,
            OPTION_LINK_KO,
            OPTION_REMIND_AT_KO,
            OPTION_REMIND_AT_LEGACY_KO,
            OPTION_REMIND_AT_EN,
            OPTION_DUE_AT_KO,
            OPTION_DUE_AT_LEGACY_KO,
            OPTION_DUE_AT_EN,
            OPTION_CHANNEL_KO,
            OPTION_NOTIFY_ROLE_KO,
            OPTION_NOTIFY_ROLE_LEGACY_KO,
            OPTION_NOTIFY_ROLE_EN,
            OPTION_PRE_REMIND_PRESET_KO,
            OPTION_PRE_REMIND_CUSTOM_KO,
            OPTION_PRE_REMIND_EN,
            OPTION_CLOSING_MESSAGE_KO,
            OPTION_CLOSING_MESSAGE_LEGACY_KO,
            OPTION_CLOSING_MESSAGE_EN,
        ).any { option -> event.getOption(option) != null }
    }

    private fun openQuickRegisterModal(event: SlashCommandInteractionEvent) {
        val titleInput = TextInput.create(V2_TITLE_KEY, TextInputStyle.SHORT)
            .setRequired(true)
            .setPlaceholder("예) 3주차 API 과제 제출")
            .setMaxLength(200)
            .build()
        val linkInput = TextInput.create(V2_LINK_KEY, TextInputStyle.SHORT)
            .setRequired(false)
            .setPlaceholder("예) https://lms.example.com/tasks/123")
            .setMaxLength(500)
            .build()
        val dueDateInput = TextInput.create(V2_DUE_DATE_KEY, TextInputStyle.SHORT)
            .setRequired(false)
            .setPlaceholder("예) 2026-03-05 (미입력 시 내일 23:59)")
            .setMaxLength(10)
            .build()

        val modal = Modal.create(DashboardActionIds.ASSIGNMENT_MODAL_V2, "빠른 과제 생성")
            .addComponents(
                Label.of("제목", titleInput),
                Label.of("링크 (선택)", linkInput),
                Label.of("마감일 (선택)", dueDateInput),
            )
            .build()
        event.replyModal(modal).queue()
    }

    private fun ensureAdmin(event: SlashCommandInteractionEvent, guildId: Long, member: net.dv8tion.jda.api.entities.Member): Boolean {
        if (adminPermissionChecker.isAdmin(guildId, member)) {
            return true
        }
        if (adminPermissionChecker.canSetAdminRole(guildId, member)) {
            return true
        }
        val configuredRole = guildConfigService.getAdminRole(guildId)
        if (configuredRole == null) {
            replyAccessDeniedError(event, "운영 권한이 필요합니다. 서버 관리자 권한(Manage Server/Administrator)으로 실행해 주세요.")
            return false
        }
        replyAccessDeniedError(event, "이 명령은 운영진 역할 또는 서버 관리자 권한 사용자만 사용할 수 있습니다.")
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

    private fun isBlockedByHomeChannelGuard(event: SlashCommandInteractionEvent, guildId: Long): Boolean {
        val assignmentChannelId = guildConfigService.getBoardChannels(guildId).assignmentChannelId
        val guardResult = homeChannelGuard.validate(
            guildId = guildId,
            currentChannelId = event.channel.idLong,
            featureChannelId = assignmentChannelId,
            featureName = "과제",
            setupCommand = "/설정 마법사 과제공지채널:#과제",
            usageCommand = "/과제 목록",
        )
        if (guardResult is HomeChannelGuard.GuardResult.Allowed) {
            return false
        }
        val blocked = guardResult as HomeChannelGuard.GuardResult.Blocked
        replyInvalidInputError(event, blocked.message, true)
        return true
    }

    private fun resolveRemindAtOption(event: SlashCommandInteractionEvent): String? {
        return event.getOption(OPTION_REMIND_AT_KO)?.asString
            ?: event.getOption(OPTION_REMIND_AT_LEGACY_KO)?.asString
            ?: event.getOption(OPTION_REMIND_AT_EN)?.asString
    }

    private fun resolveDueAtOption(event: SlashCommandInteractionEvent): String? {
        return event.getOption(OPTION_DUE_AT_KO)?.asString
            ?: event.getOption(OPTION_DUE_AT_LEGACY_KO)?.asString
            ?: event.getOption(OPTION_DUE_AT_EN)?.asString
    }

    private fun resolveNotifyRoleOption(event: SlashCommandInteractionEvent) =
        event.getOption(OPTION_NOTIFY_ROLE_KO)?.asRole
            ?: event.getOption(OPTION_NOTIFY_ROLE_LEGACY_KO)?.asRole
            ?: event.getOption(OPTION_NOTIFY_ROLE_EN)?.asRole

    private fun resolvePreReminderOption(event: SlashCommandInteractionEvent): String? {
        val custom = event.getOption(OPTION_PRE_REMIND_CUSTOM_KO)?.asString
            ?: event.getOption(OPTION_PRE_REMIND_EN)?.asString
        if (!custom.isNullOrBlank()) {
            return custom
        }
        return event.getOption(OPTION_PRE_REMIND_PRESET_KO)?.asString
    }

    private fun resolveClosingMessageOption(event: SlashCommandInteractionEvent): String? {
        return event.getOption(OPTION_CLOSING_MESSAGE_KO)?.asString
            ?: event.getOption(OPTION_CLOSING_MESSAGE_LEGACY_KO)?.asString
            ?: event.getOption(OPTION_CLOSING_MESSAGE_EN)?.asString
    }

    private fun resolveTaskIdOption(event: SlashCommandInteractionEvent): Long? {
        return event.getOption(OPTION_ID_KO)?.asLong
            ?: event.getOption(OPTION_TASK_ID_KO_LEGACY)?.asLong
            ?: event.getOption(OPTION_TASK_ID_EN)?.asLong
    }

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
        private const val OPTION_REMIND_AT_KO = "알림"
        private const val OPTION_REMIND_AT_LEGACY_KO = "알림시각"
        private const val OPTION_REMIND_AT_EN = "remindAt"
        private const val OPTION_DUE_AT_KO = "마감"
        private const val OPTION_DUE_AT_LEGACY_KO = "마감시각"
        private const val OPTION_DUE_AT_EN = "dueAt"
        private const val OPTION_CHANNEL_KO = "채널"
        private const val OPTION_NOTIFY_ROLE_KO = "역할"
        private const val OPTION_NOTIFY_ROLE_LEGACY_KO = "알림역할"
        private const val OPTION_NOTIFY_ROLE_EN = "role"
        private const val OPTION_PRE_REMIND_PRESET_KO = "임박알림옵션"
        private const val OPTION_PRE_REMIND_CUSTOM_KO = "임박알림"
        private const val OPTION_PRE_REMIND_EN = "preReminder"
        private const val OPTION_CLOSING_MESSAGE_KO = "마감메시지"
        private const val OPTION_CLOSING_MESSAGE_LEGACY_KO = "종료메시지"
        private const val OPTION_CLOSING_MESSAGE_EN = "closingMessage"
        private const val OPTION_STATUS_KO = "상태"
        private const val OPTION_ID_KO = "아이디"
        private const val OPTION_TASK_ID_KO_LEGACY = "과제아이디"
        private const val OPTION_TASK_ID_EN = "taskId"

        private const val V2_TITLE_KEY = "제목"
        private const val V2_LINK_KEY = "링크"
        private const val V2_DUE_DATE_KEY = "마감일"
    }
}
