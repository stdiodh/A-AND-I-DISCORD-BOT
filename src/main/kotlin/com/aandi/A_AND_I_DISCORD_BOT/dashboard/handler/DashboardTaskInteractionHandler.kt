package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.preference.service.GuildUserTaskPreferenceService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.common.config.FeatureFlagsProperties
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.InteractionReliabilityGuard
import com.aandi.A_AND_I_DISCORD_BOT.common.log.StructuredLog
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.service.TaskQuickRegisterDraftService
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeCustomIdParser
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionPrefixHandler
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.selections.SelectOption
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.modals.Modal
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.awt.Color
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.CompletableFuture

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class DashboardTaskInteractionHandler(
    private val permissionGate: PermissionGate,
    private val assignmentTaskService: AssignmentTaskService,
    private val guildConfigService: GuildConfigService,
    private val guildUserTaskPreferenceService: GuildUserTaskPreferenceService,
    private val quickDraftService: TaskQuickRegisterDraftService,
    private val featureFlags: FeatureFlagsProperties,
    private val clock: Clock,
    private val interactionReliabilityGuard: InteractionReliabilityGuard,
) : InteractionPrefixHandler {

    private val log = LoggerFactory.getLogger(javaClass)

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
        if (isV2Component(event.componentId, DashboardActionIds.ASSIGNMENT_V2_CONFIRM_PREFIX)) {
            confirmQuickRegisterV2(event)
            return true
        }
        if (isV2Component(event.componentId, DashboardActionIds.ASSIGNMENT_V2_CANCEL_PREFIX)) {
            cancelQuickRegisterV2(event)
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

    override fun onStringSelect(event: StringSelectInteractionEvent): Boolean {
        if (!isV2Component(event.componentId, DashboardActionIds.ASSIGNMENT_V2_MENTION_SELECT_PREFIX)) {
            return false
        }
        val guild = event.guild ?: run {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return true
        }
        val member = event.member ?: run {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return true
        }

        val draftId = extractDraftId(event.componentId, DashboardActionIds.ASSIGNMENT_V2_MENTION_SELECT_PREFIX)
            ?: run {
                event.reply("초안 식별자를 찾지 못했습니다.").setEphemeral(true).queue()
                return true
            }

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = true,
            onDeferred = { ctx ->
                val draft = requireOwnedDraft(
                    ctx = ctx,
                    draftId = draftId,
                    guildId = guild.idLong,
                    userId = member.idLong,
                ) ?: return@safeDefer

                val selected = event.values.firstOrNull()
                val mentionEnabled = selected == MENTION_ON
                val updated = quickDraftService.updateSelection(
                    draftId = draft.id,
                    mentionEnabled = mentionEnabled,
                ) ?: run {
                    interactionReliabilityGuard.safeEditReply(ctx, "초안이 만료되었습니다. 다시 등록을 시작해 주세요.")
                    return@safeDefer
                }
                logQuickRegisterSelectionUpdated(
                    guildId = guild.idLong,
                    userId = member.idLong,
                    draft = updated,
                    selectionType = QuickRegisterSelectionType.MENTION,
                )
                renderV2Draft(
                    ctx = ctx,
                    guild = guild,
                    draft = updated,
                )
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "과제 빠른 등록을 다시 시작해 주세요.",
                )
            },
        )
        return true
    }

    override fun onEntitySelect(event: EntitySelectInteractionEvent): Boolean {
        val guild = event.guild ?: run {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return true
        }
        val member = event.member ?: run {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return true
        }

        val channelDraftId = extractDraftId(event.componentId, DashboardActionIds.ASSIGNMENT_V2_CHANNEL_SELECT_PREFIX)
        if (channelDraftId != null) {
            interactionReliabilityGuard.safeDefer(
                interaction = event,
                preferUpdate = true,
                onDeferred = { ctx ->
                    val draft = requireOwnedDraft(
                        ctx = ctx,
                        draftId = channelDraftId,
                        guildId = guild.idLong,
                        userId = member.idLong,
                    ) ?: return@safeDefer

                    val selectedChannelId = event.mentions
                        .getChannels(TextChannel::class.java)
                        .firstOrNull()
                        ?.idLong
                        ?: run {
                            interactionReliabilityGuard.safeEditReply(ctx, "텍스트 채널을 선택해 주세요.")
                            return@safeDefer
                        }
                    val updated = quickDraftService.updateSelection(
                        draftId = draft.id,
                        selectedChannelId = selectedChannelId,
                    ) ?: run {
                        interactionReliabilityGuard.safeEditReply(ctx, "초안이 만료되었습니다. 다시 등록을 시작해 주세요.")
                        return@safeDefer
                    }
                    logQuickRegisterSelectionUpdated(
                        guildId = guild.idLong,
                        userId = member.idLong,
                        draft = updated,
                        selectionType = QuickRegisterSelectionType.CHANNEL,
                    )
                    renderV2Draft(ctx, guild, updated)
                },
                onFailure = { ctx, _ ->
                    interactionReliabilityGuard.safeFailureReply(
                        ctx = ctx,
                        alternativeCommandGuide = "과제 빠른 등록을 다시 시작해 주세요.",
                    )
                },
            )
            return true
        }

        val roleDraftId = extractDraftId(event.componentId, DashboardActionIds.ASSIGNMENT_V2_ROLE_SELECT_PREFIX)
        if (roleDraftId != null) {
            interactionReliabilityGuard.safeDefer(
                interaction = event,
                preferUpdate = true,
                onDeferred = { ctx ->
                    val draft = requireOwnedDraft(
                        ctx = ctx,
                        draftId = roleDraftId,
                        guildId = guild.idLong,
                        userId = member.idLong,
                    ) ?: return@safeDefer

                    val selectedRoleId = event.mentions.roles.firstOrNull()?.idLong
                    val updated = quickDraftService.updateSelection(
                        draftId = draft.id,
                        selectedRoleId = selectedRoleId,
                        mentionEnabled = resolveMentionEnabledForRoleSelection(selectedRoleId),
                    ) ?: run {
                        interactionReliabilityGuard.safeEditReply(ctx, "초안이 만료되었습니다. 다시 등록을 시작해 주세요.")
                        return@safeDefer
                    }
                    logQuickRegisterSelectionUpdated(
                        guildId = guild.idLong,
                        userId = member.idLong,
                        draft = updated,
                        selectionType = QuickRegisterSelectionType.ROLE,
                    )
                    renderV2Draft(ctx, guild, updated)
                },
                onFailure = { ctx, _ ->
                    interactionReliabilityGuard.safeFailureReply(
                        ctx = ctx,
                        alternativeCommandGuide = "과제 빠른 등록을 다시 시작해 주세요.",
                    )
                },
            )
            return true
        }

        return false
    }

    override fun onModal(event: ModalInteractionEvent): Boolean {
        if (event.modalId == DashboardActionIds.ASSIGNMENT_MODAL) {
            submitTaskCreateV1(event)
            return true
        }
        if (event.modalId == DashboardActionIds.ASSIGNMENT_MODAL_V2) {
            submitTaskCreateV2(event)
            return true
        }

        val parsed = HomeCustomIdParser.parse(event.modalId) ?: return false
        if (parsed.domain != "task" || parsed.action != "modal") {
            return false
        }

        submitTaskCreateV1(event)
        return true
    }

    private fun showTaskModal(event: ButtonInteractionEvent) {
        if (featureFlags.taskQuickregisterV2) {
            showTaskModalV2(event)
            return
        }

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
            .setPlaceholder("예) <#과제공지> <@&백엔드팀> 또는 채널 ID")
            .setMaxLength(120)
            .build()
        val modal = Modal.create(DashboardActionIds.ASSIGNMENT_MODAL, "과제 빠른 등록")
            .addComponents(
                Label.of("과제 제목", title),
                Label.of("과제 링크(http/https)", link),
                Label.of("알림시각(KST)", remindAt),
                Label.of("마감시각(KST)", dueAt),
                Label.of("채널/알림역할(선택)", channelId),
            )
            .build()
        event.replyModal(modal).queue()
    }

    private fun showTaskModalV2(event: ButtonInteractionEvent) {
        val title = TextInput.create(V2_TITLE_KEY, TextInputStyle.SHORT)
            .setRequired(true)
            .setPlaceholder("예) 3주차 API 과제 제출")
            .setMaxLength(200)
            .build()
        val link = TextInput.create(V2_LINK_KEY, TextInputStyle.SHORT)
            .setRequired(false)
            .setPlaceholder("예) https://lms.example.com/tasks/123")
            .setMaxLength(500)
            .build()
        val dueDate = TextInput.create(V2_DUE_DATE_KEY, TextInputStyle.SHORT)
            .setRequired(false)
            .setPlaceholder("예) 2026-03-05 (미입력 시 내일 23:59)")
            .setMaxLength(10)
            .build()
        val remindOption = TextInput.create(V2_REMIND_OPTION_KEY, TextInputStyle.SHORT)
            .setRequired(false)
            .setPlaceholder("예) 2026-03-04 20:00 / 6h / 24,3,1")
            .setMaxLength(32)
            .build()

        val modal = Modal.create(DashboardActionIds.ASSIGNMENT_MODAL_V2, "과제 빠른 등록 (V2)")
            .addComponents(
                Label.of("제목", title),
                Label.of("링크 (선택)", link),
                Label.of("마감일 (선택)", dueDate),
                Label.of("알림 시간/옵션 (선택)", remindOption),
            )
            .build()
        event.replyModal(modal).queue()
    }

    private fun showTaskList(event: ButtonInteractionEvent) {
        val guild = event.guild ?: run {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    val result = assignmentTaskService.list(guild.idLong, null)
                    if (result is AssignmentTaskService.ListResult.InvalidStatus) {
                        interactionReliabilityGuard.safeEditReply(ctx, "과제 상태값이 올바르지 않습니다.")
                        return@runAsync
                    }

                    val tasks = (result as AssignmentTaskService.ListResult.Success).tasks
                    if (tasks.isEmpty()) {
                        interactionReliabilityGuard.safeEditReply(ctx, "등록된 과제가 없습니다.")
                        return@runAsync
                    }

                    val lines = tasks.take(10).map {
                        val role = it.notifyRoleId?.let { roleId -> "<@&$roleId>" } ?: "없음"
                        "• [${it.id}] ${it.title} | 알림:${KstTime.formatInstantToKst(it.remindAt)} | 마감:${KstTime.formatInstantToKst(it.dueAt)} | 역할:$role"
                    }
                    interactionReliabilityGuard.safeEditReply(ctx, "과제 목록(최대 10건)\n${lines.joinToString("\n")}")
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/과제 목록` 명령으로 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun submitTaskCreateV1(event: ModalInteractionEvent) {
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

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    val title = event.getValue("제목")?.asString.orEmpty()
                    val link = event.getValue("링크")?.asString.orEmpty()
                    val remindRaw = event.getValue("알림시각")?.asString.orEmpty()
                    val dueRaw = event.getValue("마감시각")?.asString.orEmpty()
                    val remindAtUtc = runCatching { KstTime.parseKstToInstant(remindRaw) }.getOrElse {
                        interactionReliabilityGuard.safeEditReply(ctx, "알림시각 형식이 올바르지 않습니다. 예: 2026-03-01 21:30")
                        return@runAsync
                    }
                    val dueAtUtc = runCatching { KstTime.parseKstToInstant(dueRaw) }.getOrElse {
                        interactionReliabilityGuard.safeEditReply(ctx, "마감시각 형식이 올바르지 않습니다. 예: 2026-03-02 23:59")
                        return@runAsync
                    }

                    val channelRaw = event.getValue("채널")?.asString?.trim().orEmpty()
                    val notifyRoleId = parseRoleId(channelRaw, guild)
                    if (notifyRoleId == null && ROLE_HINT_REGEX.containsMatchIn(channelRaw)) {
                        interactionReliabilityGuard.safeEditReply(ctx, "알림역할을 찾지 못했습니다. `@역할명` 또는 `<@&역할ID>` 형식으로 입력해 주세요.")
                        return@runAsync
                    }
                    if (notifyRoleId != null && guild.getRoleById(notifyRoleId) == null) {
                        interactionReliabilityGuard.safeEditReply(ctx, "알림역할을 찾지 못했습니다. `@역할` 멘션 형식으로 입력해 주세요.")
                        return@runAsync
                    }
                    val channelId = parseChannelId(channelRaw, guild) ?: event.channel.idLong
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
                        preReminderHoursRaw = null,
                        closingMessageRaw = null,
                    )
                    when (result) {
                        is AssignmentTaskService.CreateResult.Success -> {
                            val task = result.task
                            val roleDisplay = task.notifyRoleId?.let { "<@&$it>" } ?: "없음"
                            interactionReliabilityGuard.safeEditReply(
                                ctx,
                                "과제를 등록했습니다. ID: ${task.id}\n알림시각(KST): ${KstTime.formatInstantToKst(task.remindAt)}\n마감시각(KST): ${KstTime.formatInstantToKst(task.dueAt)}\n알림역할: $roleDisplay\n※ 빠른 등록은 임박알림/종료메시지 기본값이 적용됩니다.",
                            )
                        }

                        AssignmentTaskService.CreateResult.InvalidUrl -> {
                            interactionReliabilityGuard.safeEditReply(ctx, "링크는 http/https만 허용됩니다.")
                        }

                        AssignmentTaskService.CreateResult.InvalidTitle -> {
                            interactionReliabilityGuard.safeEditReply(ctx, "제목은 1~200자여야 합니다.")
                        }

                        AssignmentTaskService.CreateResult.RemindAtMustBeFuture -> {
                            interactionReliabilityGuard.safeEditReply(ctx, "알림시각은 현재보다 미래여야 합니다.")
                        }

                        AssignmentTaskService.CreateResult.DueAtMustBeFuture -> {
                            interactionReliabilityGuard.safeEditReply(ctx, "마감시각은 현재보다 미래여야 합니다.")
                        }

                        AssignmentTaskService.CreateResult.DueAtMustBeAfterRemindAt -> {
                            interactionReliabilityGuard.safeEditReply(ctx, "마감시각은 알림시각 이후여야 합니다.")
                        }

                        AssignmentTaskService.CreateResult.InvalidPreReminderHours -> {
                            interactionReliabilityGuard.safeEditReply(ctx, "임박알림 형식이 올바르지 않습니다. 예: 24,3,1")
                        }

                        AssignmentTaskService.CreateResult.InvalidClosingMessage -> {
                            interactionReliabilityGuard.safeEditReply(ctx, "종료메시지는 500자 이하여야 합니다.")
                        }
                    }
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/과제 등록` 명령으로 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun submitTaskCreateV2(event: ModalInteractionEvent) {
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
        if (!featureFlags.taskQuickregisterV2) {
            event.reply("빠른 등록 V2 기능이 비활성화되어 있습니다.").setEphemeral(true).queue()
            return
        }

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    val title = event.getValue(V2_TITLE_KEY)?.asString.orEmpty().trim()
                    if (title.isBlank()) {
                        interactionReliabilityGuard.safeEditReply(ctx, "제목은 필수입니다.")
                        return@runAsync
                    }

                    val nowUtc = Instant.now(clock)
                    val dueDateRaw = event.getValue(V2_DUE_DATE_KEY)?.asString
                    val dueAtUtc = resolveDueAtUtc(dueDateRaw, nowUtc) ?: run {
                        interactionReliabilityGuard.safeEditReply(ctx, "마감일 형식이 올바르지 않습니다. 예: 2026-03-05")
                        return@runAsync
                    }

                    val remindOptionRaw = event.getValue(V2_REMIND_OPTION_KEY)?.asString
                    val reminderConfig = resolveReminderConfig(remindOptionRaw, dueAtUtc, nowUtc)
                    if (reminderConfig == null) {
                        interactionReliabilityGuard.safeEditReply(ctx, "알림 시간/옵션 형식이 올바르지 않습니다. 예: 2026-03-04 20:00 또는 6h 또는 24,3,1")
                        return@runAsync
                    }
                    if (!reminderConfig.remindAtUtc.isAfter(nowUtc)) {
                        interactionReliabilityGuard.safeEditReply(ctx, "알림시각이 현재보다 미래여야 합니다.")
                        return@runAsync
                    }
                    if (!dueAtUtc.isAfter(reminderConfig.remindAtUtc)) {
                        interactionReliabilityGuard.safeEditReply(ctx, "마감일은 알림시각 이후여야 합니다.")
                        return@runAsync
                    }

                    val guildDefaults = guildConfigService.getTaskDefaults(guild.idLong)
                    val userPref = guildUserTaskPreferenceService.find(guild.idLong, member.idLong)

                    val selectedChannelId = resolveInitialChannelId(
                        guild = guild,
                        userChannelId = userPref?.lastTaskChannelId,
                        guildDefaultChannelId = guildDefaults.defaultTaskChannelId,
                        currentChannelId = event.channel.takeIf { event.channelType == ChannelType.TEXT }?.idLong,
                    ) ?: run {
                        interactionReliabilityGuard.safeEditReply(ctx, "알림 채널을 찾지 못했습니다. 서버에 텍스트 채널이 있는지 확인해 주세요.")
                        return@runAsync
                    }

                    val selectedRoleId = resolveInitialRoleId(
                        guild = guild,
                        userRoleId = userPref?.lastNotifyRoleId,
                        guildDefaultRoleId = guildDefaults.defaultNotifyRoleId,
                    )
                    val mentionEnabled = userPref?.lastMentionEnabled ?: (selectedRoleId != null)

                    val draft = quickDraftService.create(
                        guildId = guild.idLong,
                        userId = member.idLong,
                        title = title,
                        link = event.getValue(V2_LINK_KEY)?.asString?.trim()?.takeIf { it.isNotBlank() },
                        dueAtUtc = dueAtUtc,
                        remindAtUtc = reminderConfig.remindAtUtc,
                        preReminderHoursRaw = reminderConfig.preReminderHoursRaw,
                        selectedChannelId = selectedChannelId,
                        selectedRoleId = selectedRoleId,
                        mentionEnabled = mentionEnabled,
                    )
                    log.info(
                        StructuredLog.event(
                            name = "task.quick_register.draft_created",
                            "guildId" to guild.idLong,
                            "userId" to member.idLong,
                            "draftId" to draft.id,
                            "selectedChannelId" to draft.selectedChannelId,
                            "selectedRoleId" to draft.selectedRoleId,
                            "mentionEnabled" to draft.mentionEnabled,
                        ),
                    )

                    renderV2Draft(
                        ctx = ctx,
                        guild = guild,
                        draft = draft,
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/과제 등록` 명령으로 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun confirmQuickRegisterV2(event: ButtonInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }

        val draftId = extractDraftId(event.componentId, DashboardActionIds.ASSIGNMENT_V2_CONFIRM_PREFIX)
            ?: run {
                event.reply("초안 식별자를 찾지 못했습니다.").setEphemeral(true).queue()
                return
            }

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    val draft = requireOwnedDraft(
                        ctx = ctx,
                        draftId = draftId,
                        guildId = guild.idLong,
                        userId = member.idLong,
                    ) ?: return@runAsync

                    val channel = guild.getTextChannelById(draft.selectedChannelId)
                    if (channel == null || !canBotSendToChannel(guild, channel)) {
                        logQuickRegisterConfirmOutcome(
                            guildId = guild.idLong,
                            userId = member.idLong,
                            draftId = draft.id,
                            success = false,
                            reason = QuickRegisterConfirmReason.CHANNEL_UNAVAILABLE_OR_NO_SEND_PERMISSION,
                            selectedChannelId = draft.selectedChannelId,
                            selectedRoleId = draft.selectedRoleId,
                            mentionEnabled = draft.mentionEnabled,
                        )
                        renderV2Draft(
                            ctx = ctx,
                            guild = guild,
                            draft = draft,
                            notice = "선택한 채널에 봇이 접근/전송할 수 없습니다. 채널을 다시 선택해 주세요.",
                        )
                        return@runAsync
                    }

                    val notifyResolution = resolveNotifyRoleForQuickRegister(
                        guild = guild,
                        channel = channel,
                        draft = draft,
                    )

                    val verifyUrl = resolveVerifyUrl(
                        rawLink = draft.link,
                        guildId = guild.idLong,
                        channelId = channel.idLong,
                    )

                    val result = assignmentTaskService.create(
                        guildId = guild.idLong,
                        channelId = channel.idLong,
                        title = draft.title,
                        verifyUrl = verifyUrl,
                        remindAtUtc = draft.remindAtUtc,
                        dueAtUtc = draft.dueAtUtc,
                        createdBy = member.idLong,
                        nowUtc = Instant.now(clock),
                        notifyRoleId = notifyResolution.notifyRoleId,
                        preReminderHoursRaw = draft.preReminderHoursRaw,
                        closingMessageRaw = null,
                    )

                    val successResult = result as? AssignmentTaskService.CreateResult.Success
                    if (successResult != null) {
                        handleQuickRegisterCreateSuccess(
                            ctx = ctx,
                            guildId = guild.idLong,
                            userId = member.idLong,
                            draft = draft,
                            task = successResult.task,
                            mentionDegraded = notifyResolution.mentionDegraded,
                        )
                        return@runAsync
                    }
                    handleQuickRegisterCreateFailure(
                        ctx = ctx,
                        guildId = guild.idLong,
                        userId = member.idLong,
                        draft = draft,
                        result = result,
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "과제 빠른 등록을 다시 시작해 주세요.",
                )
            },
        )
    }

    private fun cancelQuickRegisterV2(event: ButtonInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }

        val draftId = extractDraftId(event.componentId, DashboardActionIds.ASSIGNMENT_V2_CANCEL_PREFIX)
            ?: run {
                event.reply("초안 식별자를 찾지 못했습니다.").setEphemeral(true).queue()
                return
            }

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                val draft = quickDraftService.get(draftId)
                if (draft == null || draft.guildId != guild.idLong || draft.userId != member.idLong) {
                    interactionReliabilityGuard.safeEditReply(ctx, "이미 만료되었거나 접근할 수 없는 초안입니다.")
                    return@safeDefer
                }
                quickDraftService.remove(draftId)
                log.info(
                    StructuredLog.event(
                        name = "task.quick_register.canceled",
                        "guildId" to guild.idLong,
                        "userId" to member.idLong,
                        "draftId" to draftId,
                    ),
                )
                interactionReliabilityGuard.safeEditReply(ctx, "과제 빠른 등록을 취소했습니다.")
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "과제 빠른 등록을 다시 시작해 주세요.",
                )
            },
        )
    }

    private fun renderV2Draft(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        guild: Guild,
        draft: TaskQuickRegisterDraftService.QuickDraft,
        notice: String? = null,
    ) {
        val response = buildV2DraftResponse(guild, draft, notice)
        interactionReliabilityGuard.safeEditReply(
            ctx = ctx,
            message = response.message,
            embeds = listOf(response.previewEmbed),
            components = response.components,
        )
    }

    private fun buildV2DraftResponse(
        guild: Guild,
        draft: TaskQuickRegisterDraftService.QuickDraft,
        notice: String?,
    ): V2DraftResponse {
        val channelMention = "<#${draft.selectedChannelId}>"
        val roleMention = draft.selectedRoleId?.let { "<@&$it>" } ?: "없음"
        val mentionText = resolveMentionText(draft.mentionEnabled)
        val reminderOptions = draft.preReminderHoursRaw ?: "기본값(24,3,1)"
        val preview = EmbedBuilder()
            .setColor(Color(0x2D9CDB))
            .setTitle("과제 등록 미리보기")
            .setDescription("등록 확정 전에 채널/역할/멘션 여부를 확인하세요.")
            .addField("제목", draft.title, false)
            .addField("링크", draft.link ?: "(미입력: Discord 채널 링크 사용)", false)
            .addField("알림 채널", channelMention, true)
            .addField("알림 역할", roleMention, true)
            .addField("멘션 여부", mentionText, true)
            .addField("알림시각(KST)", KstTime.formatInstantToKst(draft.remindAtUtc), true)
            .addField("마감시각(KST)", KstTime.formatInstantToKst(draft.dueAtUtc), true)
            .addField("임박알림 옵션", reminderOptions, true)
            .build()

        val channelSelect = EntitySelectMenu.create(
            "${DashboardActionIds.ASSIGNMENT_V2_CHANNEL_SELECT_PREFIX}:${draft.id}",
            EntitySelectMenu.SelectTarget.CHANNEL,
        )
            .setChannelTypes(ChannelType.TEXT)
            .setMinValues(1)
            .setMaxValues(1)
            .setPlaceholder("알림 채널 선택")
            .setDefaultValues(EntitySelectMenu.DefaultValue.channel(draft.selectedChannelId))
            .build()

        val roleBuilder = EntitySelectMenu.create(
            "${DashboardActionIds.ASSIGNMENT_V2_ROLE_SELECT_PREFIX}:${draft.id}",
            EntitySelectMenu.SelectTarget.ROLE,
        )
            .setMinValues(0)
            .setMaxValues(1)
            .setPlaceholder("알림 역할 선택 (선택)")
        if (draft.selectedRoleId != null) {
            roleBuilder.setDefaultValues(EntitySelectMenu.DefaultValue.role(draft.selectedRoleId!!))
        }
        val roleSelect = roleBuilder.build()

        val mentionSelect = StringSelectMenu.create("${DashboardActionIds.ASSIGNMENT_V2_MENTION_SELECT_PREFIX}:${draft.id}")
            .setPlaceholder("멘션 여부")
            .addOptions(
                SelectOption.of("멘션 사용", MENTION_ON)
                    .withDescription("선택한 역할을 알림에 멘션합니다.")
                    .withDefault(draft.mentionEnabled),
                SelectOption.of("멘션 안함", MENTION_OFF)
                    .withDescription("역할 멘션 없이 알림을 전송합니다.")
                    .withDefault(!draft.mentionEnabled),
            )
            .build()

        val components = listOf(
            ActionRow.of(channelSelect),
            ActionRow.of(roleSelect),
            ActionRow.of(mentionSelect),
            ActionRow.of(
                Button.success("${DashboardActionIds.ASSIGNMENT_V2_CONFIRM_PREFIX}:${draft.id}", "등록 확정"),
                Button.danger("${DashboardActionIds.ASSIGNMENT_V2_CANCEL_PREFIX}:${draft.id}", "취소"),
            ),
        )

        val message = buildString {
            append("과제 빠른 등록 V2 설정을 선택해 주세요.")
            if (!notice.isNullOrBlank()) {
                append("\n")
                append(notice)
            }
        }

        return V2DraftResponse(
            message = message,
            previewEmbed = preview,
            components = components,
        )
    }

    private fun requireOwnedDraft(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        draftId: String,
        guildId: Long,
        userId: Long,
    ): TaskQuickRegisterDraftService.QuickDraft? {
        val draft = quickDraftService.get(draftId)
        if (draft == null) {
            interactionReliabilityGuard.safeEditReply(ctx, "초안이 만료되었습니다. 다시 등록을 시작해 주세요.")
            return null
        }
        if (draft.guildId != guildId || draft.userId != userId) {
            interactionReliabilityGuard.safeEditReply(ctx, "다른 사용자의 초안은 수정할 수 없습니다.")
            return null
        }
        return draft
    }

    private fun resolveDueAtUtc(raw: String?, nowUtc: Instant): Instant? {
        val nowKst = nowUtc.atZone(KST_ZONE)
        if (raw.isNullOrBlank()) {
            val defaultLocalDate = nowKst.toLocalDate().plusDays(1)
            return defaultLocalDate.atTime(LocalTime.of(23, 59)).atZone(KST_ZONE).toInstant()
        }
        val localDate = runCatching { LocalDate.parse(raw.trim()) }.getOrNull() ?: return null
        return localDate.atTime(LocalTime.of(23, 59)).atZone(KST_ZONE).toInstant()
    }

    private fun resolveReminderConfig(raw: String?, dueAtUtc: Instant, nowUtc: Instant): ReminderConfig? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return ReminderConfig(
                remindAtUtc = defaultRemindAt(dueAtUtc, nowUtc),
                preReminderHoursRaw = null,
            )
        }

        val asAbsolute = runCatching { KstTime.parseKstToInstant(trimmed) }.getOrNull()
        if (asAbsolute != null) {
            return ReminderConfig(
                remindAtUtc = normalizeRemindAt(asAbsolute, dueAtUtc, nowUtc) ?: return null,
                preReminderHoursRaw = null,
            )
        }

        val relativeHours = RELATIVE_HOURS_REGEX.matchEntire(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        if (relativeHours != null && relativeHours in 1..168) {
            val remindAt = dueAtUtc.minusSeconds(relativeHours * 3600)
            return ReminderConfig(
                remindAtUtc = normalizeRemindAt(remindAt, dueAtUtc, nowUtc) ?: return null,
                preReminderHoursRaw = null,
            )
        }

        if (COMMA_HOURS_REGEX.matches(trimmed)) {
            val parsed = assignmentTaskService.parsePreReminderHours(trimmed) ?: return null
            val preReminderRaw = parsed.sortedDescending().joinToString(",")
            val maxHour = parsed.maxOrNull() ?: return null
            val remindAt = dueAtUtc.minusSeconds(maxHour.toLong() * 3600)
            return ReminderConfig(
                remindAtUtc = normalizeRemindAt(remindAt, dueAtUtc, nowUtc) ?: return null,
                preReminderHoursRaw = preReminderRaw,
            )
        }

        return null
    }

    private fun defaultRemindAt(dueAtUtc: Instant, nowUtc: Instant): Instant {
        val candidate = dueAtUtc.minusSeconds(24 * 3600)
        return normalizeRemindAt(candidate, dueAtUtc, nowUtc)
            ?: nowUtc.plusSeconds(300)
    }

    private fun normalizeRemindAt(candidate: Instant, dueAtUtc: Instant, nowUtc: Instant): Instant? {
        var remindAt = candidate
        if (!remindAt.isAfter(nowUtc)) {
            remindAt = nowUtc.plusSeconds(300)
        }
        if (!remindAt.isBefore(dueAtUtc)) {
            remindAt = dueAtUtc.minusSeconds(300)
        }
        if (!remindAt.isAfter(nowUtc)) {
            return null
        }
        if (!remindAt.isBefore(dueAtUtc)) {
            return null
        }
        return remindAt
    }

    private fun resolveInitialChannelId(
        guild: Guild,
        userChannelId: Long?,
        guildDefaultChannelId: Long?,
        currentChannelId: Long?,
    ): Long? {
        val candidateIds = listOf(userChannelId, guildDefaultChannelId, currentChannelId)
            .filterNotNull()
            .distinct()

        candidateIds.forEach { channelId ->
            val channel = guild.getTextChannelById(channelId) ?: return@forEach
            if (canBotSendToChannel(guild, channel)) {
                return channel.idLong
            }
        }

        return guild.textChannels.firstOrNull { canBotSendToChannel(guild, it) }?.idLong
    }

    private fun resolveInitialRoleId(
        guild: Guild,
        userRoleId: Long?,
        guildDefaultRoleId: Long?,
    ): Long? {
        val candidateIds = listOf(userRoleId, guildDefaultRoleId)
            .filterNotNull()
            .distinct()

        return candidateIds.firstOrNull { guild.getRoleById(it) != null }
    }

    private fun canBotSendToChannel(guild: Guild, channel: TextChannel): Boolean {
        val selfMember = guild.selfMember
        if (!selfMember.hasAccess(channel)) {
            return false
        }
        return selfMember.hasPermission(channel, Permission.MESSAGE_SEND)
    }

    private fun canMentionRole(guild: Guild, channel: TextChannel, roleId: Long): Boolean {
        val role = guild.getRoleById(roleId) ?: return false
        if (role.isMentionable) {
            return true
        }
        return guild.selfMember.hasPermission(channel, Permission.MESSAGE_MENTION_EVERYONE)
    }

    private fun resolveVerifyUrl(rawLink: String?, guildId: Long, channelId: Long): String {
        val fallback = "https://discord.com/channels/$guildId/$channelId"
        val trimmed = rawLink?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return fallback
        }
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return fallback
        val scheme = uri.scheme?.lowercase() ?: return fallback
        if (scheme != "http" && scheme != "https") {
            return fallback
        }
        if (uri.host.isNullOrBlank()) {
            return fallback
        }
        return uri.toString()
    }

    private fun parseChannelId(raw: String, guild: Guild): Long? {
        if (raw.isBlank()) {
            return null
        }
        val fromMention = CHANNEL_MENTION_REGEX.find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        if (fromMention != null) {
            return fromMention
        }
        val numeric = raw.trim().toLongOrNull()
        if (numeric != null) {
            return numeric
        }
        val channelName = CHANNEL_NAME_REGEX.find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return guild.textChannels.firstOrNull { it.name.equals(channelName, ignoreCase = true) }?.idLong
    }

    private fun parseRoleId(raw: String, guild: Guild): Long? {
        if (raw.isBlank()) {
            return null
        }
        val fromMention = ROLE_MENTION_REGEX.find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        if (fromMention != null) {
            return fromMention
        }

        val roleName = ROLE_NAME_REGEX.find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return guild.roles.firstOrNull { it.name.equals(roleName, ignoreCase = true) }?.idLong
    }

    private fun isV2Component(componentId: String, prefix: String): Boolean {
        return componentId.startsWith("$prefix:")
    }

    private fun extractDraftId(componentId: String, prefix: String): String? {
        if (!isV2Component(componentId, prefix)) {
            return null
        }
        val draftId = componentId.removePrefix("$prefix:").trim()
        if (draftId.isBlank()) {
            return null
        }
        return draftId
    }

    private fun resolveMentionEnabledForRoleSelection(selectedRoleId: Long?): Boolean? {
        if (selectedRoleId == null) {
            return false
        }
        return null
    }

    private fun resolveMentionText(mentionEnabled: Boolean): String {
        if (mentionEnabled) {
            return "사용"
        }
        return "안함"
    }

    private fun logQuickRegisterSelectionUpdated(
        guildId: Long,
        userId: Long,
        draft: TaskQuickRegisterDraftService.QuickDraft,
        selectionType: QuickRegisterSelectionType,
    ) {
        log.info(
            StructuredLog.event(
                name = "task.quick_register.selection_updated",
                "guildId" to guildId,
                "userId" to userId,
                "draftId" to draft.id,
                "selectionType" to selectionType.value,
                "selectedChannelId" to draft.selectedChannelId,
                "selectedRoleId" to draft.selectedRoleId,
                "mentionEnabled" to draft.mentionEnabled,
            ),
        )
    }

    private fun resolveNotifyRoleForQuickRegister(
        guild: Guild,
        channel: TextChannel,
        draft: TaskQuickRegisterDraftService.QuickDraft,
    ): NotifyRoleResolution {
        if (!draft.mentionEnabled) {
            return NotifyRoleResolution(notifyRoleId = null, mentionDegraded = false)
        }
        val selectedRoleId = draft.selectedRoleId ?: return NotifyRoleResolution(notifyRoleId = null, mentionDegraded = false)
        if (canMentionRole(guild, channel, selectedRoleId)) {
            return NotifyRoleResolution(notifyRoleId = selectedRoleId, mentionDegraded = false)
        }
        return NotifyRoleResolution(notifyRoleId = null, mentionDegraded = true)
    }

    private fun handleQuickRegisterCreateSuccess(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        guildId: Long,
        userId: Long,
        draft: TaskQuickRegisterDraftService.QuickDraft,
        task: AssignmentTaskService.AssignmentTaskView,
        mentionDegraded: Boolean,
    ) {
        guildUserTaskPreferenceService.save(
            guildId = guildId,
            userId = userId,
            lastTaskChannelId = draft.selectedChannelId,
            lastNotifyRoleId = draft.selectedRoleId,
            lastMentionEnabled = draft.mentionEnabled,
        )

        val defaults = guildConfigService.getTaskDefaults(guildId)
        if (defaults.defaultTaskChannelId == null || defaults.defaultNotifyRoleId == null) {
            guildConfigService.setTaskDefaults(
                guildId = guildId,
                defaultTaskChannelId = defaults.defaultTaskChannelId ?: draft.selectedChannelId,
                defaultNotifyRoleId = defaults.defaultNotifyRoleId ?: draft.selectedRoleId,
            )
        }

        quickDraftService.remove(draft.id)
        val roleDisplay = task.notifyRoleId?.let { "<@&$it>" } ?: "없음"
        interactionReliabilityGuard.safeEditReply(
            ctx = ctx,
            message = "과제를 등록했습니다.\n" +
                "ID: `${task.id}`\n" +
                "알림 채널: <#${task.channelId}>\n" +
                "알림 역할: $roleDisplay\n" +
                "알림시각(KST): `${KstTime.formatInstantToKst(task.remindAt)}`\n" +
                "마감시각(KST): `${KstTime.formatInstantToKst(task.dueAt)}`\n" +
                "과제 목록: `/과제 목록`${quickRegisterDegradedLine(mentionDegraded)}",
            components = listOf(
                ActionRow.of(
                    Button.secondary(DashboardActionIds.ASSIGNMENT_LIST, "과제 목록 보기"),
                ),
            ),
        )
        val reason = QuickRegisterConfirmReason.SUCCESS
        if (mentionDegraded) {
            logQuickRegisterConfirmOutcome(
                guildId = guildId,
                userId = userId,
                draftId = draft.id,
                success = true,
                reason = QuickRegisterConfirmReason.SUCCESS_MENTION_DEGRADED,
                selectedChannelId = task.channelId,
                selectedRoleId = task.notifyRoleId,
                mentionEnabled = draft.mentionEnabled,
                taskId = task.id,
            )
            return
        }
        logQuickRegisterConfirmOutcome(
            guildId = guildId,
            userId = userId,
            draftId = draft.id,
            success = true,
            reason = reason,
            selectedChannelId = task.channelId,
            selectedRoleId = task.notifyRoleId,
            mentionEnabled = draft.mentionEnabled,
            taskId = task.id,
        )
    }

    private fun handleQuickRegisterCreateFailure(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        guildId: Long,
        userId: Long,
        draft: TaskQuickRegisterDraftService.QuickDraft,
        result: AssignmentTaskService.CreateResult,
    ) {
        val failure = resolveQuickRegisterFailure(result)
        if (failure == null) {
            interactionReliabilityGuard.safeEditReply(ctx, "처리 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요.")
            return
        }
        logQuickRegisterConfirmOutcome(
            guildId = guildId,
            userId = userId,
            draftId = draft.id,
            success = false,
            reason = failure.reason,
            selectedChannelId = draft.selectedChannelId,
            selectedRoleId = draft.selectedRoleId,
            mentionEnabled = draft.mentionEnabled,
        )
        interactionReliabilityGuard.safeEditReply(ctx, failure.message)
    }

    private fun resolveQuickRegisterFailure(result: AssignmentTaskService.CreateResult): QuickRegisterCreateFailure? {
        if (result is AssignmentTaskService.CreateResult.Success) {
            return null
        }
        return when (result) {
            AssignmentTaskService.CreateResult.InvalidUrl ->
                QuickRegisterCreateFailure(
                    reason = QuickRegisterConfirmReason.INVALID_URL,
                    message = "링크는 http/https만 허용됩니다.",
                )
            AssignmentTaskService.CreateResult.InvalidTitle ->
                QuickRegisterCreateFailure(
                    reason = QuickRegisterConfirmReason.INVALID_TITLE,
                    message = "제목은 1~200자여야 합니다.",
                )
            AssignmentTaskService.CreateResult.RemindAtMustBeFuture ->
                QuickRegisterCreateFailure(
                    reason = QuickRegisterConfirmReason.REMIND_AT_MUST_BE_FUTURE,
                    message = "알림시각은 현재보다 미래여야 합니다.",
                )
            AssignmentTaskService.CreateResult.DueAtMustBeFuture ->
                QuickRegisterCreateFailure(
                    reason = QuickRegisterConfirmReason.DUE_AT_MUST_BE_FUTURE,
                    message = "마감시각은 현재보다 미래여야 합니다.",
                )
            AssignmentTaskService.CreateResult.DueAtMustBeAfterRemindAt ->
                QuickRegisterCreateFailure(
                    reason = QuickRegisterConfirmReason.DUE_AT_MUST_BE_AFTER_REMIND_AT,
                    message = "마감시각은 알림시각 이후여야 합니다.",
                )
            AssignmentTaskService.CreateResult.InvalidPreReminderHours ->
                QuickRegisterCreateFailure(
                    reason = QuickRegisterConfirmReason.INVALID_PRE_REMINDER_HOURS,
                    message = "임박알림 형식이 올바르지 않습니다. 예: 24,3,1",
                )
            AssignmentTaskService.CreateResult.InvalidClosingMessage ->
                QuickRegisterCreateFailure(
                    reason = QuickRegisterConfirmReason.INVALID_CLOSING_MESSAGE,
                    message = "종료메시지는 500자 이하여야 합니다.",
                )
            is AssignmentTaskService.CreateResult.Success -> null
        }
    }

    private fun quickRegisterDegradedLine(mentionDegraded: Boolean): String {
        if (mentionDegraded) {
            return "\n※ 역할 멘션 권한이 없어 멘션 없이 등록했습니다."
        }
        return ""
    }

    private fun logQuickRegisterConfirmOutcome(
        guildId: Long,
        userId: Long,
        draftId: String,
        success: Boolean,
        reason: QuickRegisterConfirmReason,
        selectedChannelId: Long,
        selectedRoleId: Long?,
        mentionEnabled: Boolean,
        taskId: Long? = null,
    ) {
        val eventName = "task.quick_register.confirm.failed"
        if (success) {
            log.info(
                StructuredLog.event(
                    name = "task.quick_register.confirm.success",
                    "guildId" to guildId,
                    "userId" to userId,
                    "draftId" to draftId,
                    "taskId" to taskId,
                    "reason" to reason.name,
                    "selectedChannelId" to selectedChannelId,
                    "selectedRoleId" to selectedRoleId,
                    "mentionEnabled" to mentionEnabled,
                ),
            )
            return
        }
        val event = StructuredLog.event(
            name = eventName,
            "guildId" to guildId,
            "userId" to userId,
            "draftId" to draftId,
            "taskId" to taskId,
            "reason" to reason.name,
            "selectedChannelId" to selectedChannelId,
            "selectedRoleId" to selectedRoleId,
            "mentionEnabled" to mentionEnabled,
        )
        log.warn(event)
    }

    private data class ReminderConfig(
        val remindAtUtc: Instant,
        val preReminderHoursRaw: String?,
    )

    private data class V2DraftResponse(
        val message: String,
        val previewEmbed: MessageEmbed,
        val components: List<ActionRow>,
    )

    private data class NotifyRoleResolution(
        val notifyRoleId: Long?,
        val mentionDegraded: Boolean,
    )

    private data class QuickRegisterCreateFailure(
        val reason: QuickRegisterConfirmReason,
        val message: String,
    )

    private enum class QuickRegisterSelectionType(val value: String) {
        MENTION("mention"),
        CHANNEL("channel"),
        ROLE("role"),
    }

    private enum class QuickRegisterConfirmReason {
        SUCCESS,
        SUCCESS_MENTION_DEGRADED,
        CHANNEL_UNAVAILABLE_OR_NO_SEND_PERMISSION,
        INVALID_URL,
        INVALID_TITLE,
        REMIND_AT_MUST_BE_FUTURE,
        DUE_AT_MUST_BE_FUTURE,
        DUE_AT_MUST_BE_AFTER_REMIND_AT,
        INVALID_PRE_REMINDER_HOURS,
        INVALID_CLOSING_MESSAGE,
    }

    companion object {
        private val supportedPrefixes = setOf("dash", "assign", "home")
        private val CHANNEL_MENTION_REGEX = Regex("""<#(\\d+)>""")
        private val ROLE_MENTION_REGEX = Regex("""<@&(\\d+)>""")
        private val CHANNEL_NAME_REGEX = Regex("""#([^\s>]+)""")
        private val ROLE_NAME_REGEX = Regex("""@([^\s>]+)""")
        private val ROLE_HINT_REGEX = Regex("""<@&\d+>|@[^\s]+""")
        private val RELATIVE_HOURS_REGEX = Regex("""^(\\d{1,3})\s*(?:h|시간|시간전|h전)$""")
        private val COMMA_HOURS_REGEX = Regex("""^[0-9,\s]+$""")
        private val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        private const val V2_TITLE_KEY = "제목"
        private const val V2_LINK_KEY = "링크"
        private const val V2_DUE_DATE_KEY = "마감일"
        private const val V2_REMIND_OPTION_KEY = "알림옵션"

        private const val MENTION_ON = "on"
        private const val MENTION_OFF = "off"
    }
}
