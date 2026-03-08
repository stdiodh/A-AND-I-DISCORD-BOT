package com.aandi.A_AND_I_DISCORD_BOT.meeting.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.agenda.service.AgendaService
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.HomeChannelGuard
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.common.config.FeatureFlagsProperties
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.DiscordReplyFactory
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.InteractionReliabilityGuard
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import com.aandi.A_AND_I_DISCORD_BOT.meeting.service.MeetingService
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.service.MeetingSummaryArtifactService
import com.aandi.A_AND_I_DISCORD_BOT.meeting.ui.MeetingSummaryActionIds
import com.aandi.A_AND_I_DISCORD_BOT.meeting.ui.MeetingThreadActionIds
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.modals.Modal
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingSlashCommandHandler(
    private val meetingService: MeetingService,
    private val agendaService: AgendaService,
    private val permissionGate: PermissionGate,
    private val guildConfigService: GuildConfigService,
    private val homeChannelGuard: HomeChannelGuard,
    private val featureFlags: FeatureFlagsProperties,
    private val discordReplyFactory: DiscordReplyFactory,
    private val interactionReliabilityGuard: InteractionReliabilityGuard,
    private val meetingSummaryArtifactService: MeetingSummaryArtifactService,
) : ListenerAdapter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name == COMMAND_DECISION_KO) {
            captureDecision(event)
            return
        }
        if (event.name == COMMAND_ACTION_KO) {
            captureAction(event)
            return
        }
        if (event.name == COMMAND_TODO_KO) {
            captureTodo(event)
            return
        }
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
        if (event.subcommandName == SUBCOMMAND_ACTIVE_KO || event.subcommandName == SUBCOMMAND_ACTIVE_EN) {
            listActiveMeetings(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_HISTORY_KO || event.subcommandName == SUBCOMMAND_HISTORY_EN) {
            listMeetingHistory(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_DETAIL_KO || event.subcommandName == SUBCOMMAND_DETAIL_EN) {
            showMeetingDetail(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_RECORD_KO) {
            captureMeetingRecord(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_ITEM_KO) {
            handleMeetingItem(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_ITEM_LIST_KO) {
            listMeetingItems(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_ITEM_CANCEL_KO) {
            cancelMeetingItem(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_AGENDA_SET_KO || event.subcommandName == SUBCOMMAND_AGENDA_SET_EN) {
            handleMeetingAgendaSet(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_AGENDA_GET_KO || event.subcommandName == SUBCOMMAND_AGENDA_GET_EN) {
            handleMeetingAgendaToday(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_AGENDA_RECENT_KO || event.subcommandName == SUBCOMMAND_AGENDA_RECENT_EN) {
            handleMeetingAgendaRecent(event)
            return
        }
        discordReplyFactory.invalidInput(event, "지원하지 않는 하위 명령입니다.")
    }

    private fun captureDecision(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        if (!featureFlags.meetingSummaryV2) {
            discordReplyFactory.invalidInput(event, "`FEATURE_MEETING_SUMMARY_V2=true`에서 사용할 수 있는 기능입니다.")
            return
        }
        val content = event.getOption(OPTION_CONTENT_KO)?.asString?.trim().orEmpty()
        if (content.isBlank()) {
            discordReplyFactory.invalidInput(event, "내용 옵션은 필수입니다.")
            return
        }
        val fallbackThreadId = event.channel.takeIf { event.channelType.isThread }?.idLong
        val requestedMeetingSessionId = parseMeetingSessionIdOption(event)
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        meetingService.captureDecision(
                            guildId = guild.idLong,
                            requestedBy = member.idLong,
                            fallbackThreadId = fallbackThreadId,
                            requestedMeetingSessionId = requestedMeetingSessionId,
                            content = content,
                        )
                    }.fold(
                        onSuccess = { result ->
                            replyCaptureResult(ctx, result)
                        },
                        onFailure = { exception ->
                            log.error("결정 기록 실패: guildId={}, userId={}", guild.idLong, member.idLong, exception)
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/결정` 명령을 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/결정` 명령을 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun captureAction(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        if (!featureFlags.meetingSummaryV2) {
            discordReplyFactory.invalidInput(event, "`FEATURE_MEETING_SUMMARY_V2=true`에서 사용할 수 있는 기능입니다.")
            return
        }
        val content = event.getOption(OPTION_CONTENT_KO)?.asString?.trim().orEmpty()
        if (content.isBlank()) {
            discordReplyFactory.invalidInput(event, "내용 옵션은 필수입니다.")
            return
        }
        val dueDateRaw = event.getOption(OPTION_DUE_DATE_KO)?.asString
        val dueDateLocal = runCatching {
            if (dueDateRaw.isNullOrBlank()) null else LocalDate.parse(dueDateRaw.trim())
        }.getOrElse {
            discordReplyFactory.invalidInput(event, "기한 형식이 올바르지 않습니다. 예: 2026-03-01")
            return
        }
        val assigneeUserId = event.getOption(OPTION_ASSIGNEE_KO)?.asUser?.idLong
        val fallbackThreadId = event.channel.takeIf { event.channelType.isThread }?.idLong
        val requestedMeetingSessionId = parseMeetingSessionIdOption(event)
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        meetingService.captureAction(
                            guildId = guild.idLong,
                            requestedBy = member.idLong,
                            fallbackThreadId = fallbackThreadId,
                            requestedMeetingSessionId = requestedMeetingSessionId,
                            content = content,
                            assigneeUserId = assigneeUserId,
                            dueDateLocal = dueDateLocal,
                        )
                    }.fold(
                        onSuccess = { result ->
                            replyCaptureResult(ctx, result)
                        },
                        onFailure = { exception ->
                            log.error("액션 기록 실패: guildId={}, userId={}", guild.idLong, member.idLong, exception)
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/액션` 명령을 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/액션` 명령을 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun captureTodo(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        if (!featureFlags.meetingSummaryV2) {
            discordReplyFactory.invalidInput(event, "`FEATURE_MEETING_SUMMARY_V2=true`에서 사용할 수 있는 기능입니다.")
            return
        }
        val content = event.getOption(OPTION_CONTENT_KO)?.asString?.trim().orEmpty()
        if (content.isBlank()) {
            discordReplyFactory.invalidInput(event, "내용 옵션은 필수입니다.")
            return
        }
        val fallbackThreadId = event.channel.takeIf { event.channelType.isThread }?.idLong
        val requestedMeetingSessionId = parseMeetingSessionIdOption(event)
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        meetingService.captureTodo(
                            guildId = guild.idLong,
                            requestedBy = member.idLong,
                            fallbackThreadId = fallbackThreadId,
                            requestedMeetingSessionId = requestedMeetingSessionId,
                            content = content,
                        )
                    }.fold(
                        onSuccess = { result ->
                            replyCaptureResult(ctx, result)
                        },
                        onFailure = { exception ->
                            log.error("투두 기록 실패: guildId={}, userId={}", guild.idLong, member.idLong, exception)
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/투두` 명령을 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/투두` 명령을 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun captureMeetingRecord(event: SlashCommandInteractionEvent) {
        val recordType = parseRecordType(event.getOption(OPTION_RECORD_TYPE_KO)?.asString)
        if (recordType == null) {
            discordReplyFactory.invalidInput(event, "`유형`은 결정/액션/투두 중 하나를 선택해 주세요.")
            return
        }
        when (recordType) {
            MeetingService.StructuredCaptureType.DECISION -> captureDecision(event)
            MeetingService.StructuredCaptureType.ACTION -> captureAction(event)
            MeetingService.StructuredCaptureType.TODO -> captureTodo(event)
        }
    }

    private fun handleMeetingItem(event: SlashCommandInteractionEvent) {
        val action = parseItemAction(event.getOption(OPTION_ITEM_ACTION_KO)?.asString)
        if (action == null) {
            discordReplyFactory.invalidInput(event, "`동작`은 조회/취소 중 하나를 선택해 주세요.")
            return
        }
        when (action) {
            ItemAction.LIST -> listMeetingItems(event)
            ItemAction.CANCEL -> cancelMeetingItem(event)
        }
    }

    private fun replyCaptureResult(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        result: MeetingService.StructuredCaptureResult,
    ) {
        when (result) {
            is MeetingService.StructuredCaptureResult.Success -> {
                val typeLabel = resolveCaptureTypeLabel(result.type)
                interactionReliabilityGuard.safeEditReply(
                    ctx,
                    "$typeLabel 기록 완료\n" +
                        "세션ID `${result.sessionId}` / 스레드 <#${result.threadId}> / 항목ID `${result.itemId}`\n" +
                        "내용: ${result.summaryLine}",
                )
            }

            MeetingService.StructuredCaptureResult.SessionNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "진행 중인 회의를 찾지 못했습니다. `/회의 시작` 후 다시 시도해 주세요.")
            }

            MeetingService.StructuredCaptureResult.MeetingIdRequired -> {
                interactionReliabilityGuard.safeEditReply(
                    ctx,
                    "현재 채널에서는 `회의아이디`가 필요합니다. 예: `/회의 기록 유형:<결정|액션|투두> 내용:... 회의아이디:<ID>`",
                )
            }

            MeetingService.StructuredCaptureResult.MeetingNotActive -> {
                interactionReliabilityGuard.safeEditReply(ctx, "현재 스레드의 회의는 이미 종료되었습니다. 진행 중인 회의에서 다시 시도해 주세요.")
            }

            is MeetingService.StructuredCaptureResult.ThreadNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "회의 스레드를 찾지 못했습니다. (threadId=${result.threadId})")
            }
        }
    }

    private fun listMeetingItems(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        if (!featureFlags.meetingSummaryV2) {
            discordReplyFactory.invalidInput(event, "`FEATURE_MEETING_SUMMARY_V2=true`에서 사용할 수 있는 기능입니다.")
            return
        }
        val fallbackThreadId = event.channel.takeIf { event.channelType.isThread }?.idLong
        val requestedMeetingSessionId = parseMeetingSessionIdOption(event)
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        meetingService.listStructuredItems(
                            guildId = guild.idLong,
                            fallbackThreadId = fallbackThreadId,
                            requestedMeetingSessionId = requestedMeetingSessionId,
                        )
                    }.fold(
                        onSuccess = { result ->
                            replyListItemsResult(ctx, result)
                        },
                        onFailure = { exception ->
                            log.error("회의 항목 조회 실패: guildId={}, userId={}", guild.idLong, member.idLong, exception)
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/회의 항목 동작:조회` 명령을 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/회의 항목 동작:조회` 명령을 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun cancelMeetingItem(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        if (!featureFlags.meetingSummaryV2) {
            discordReplyFactory.invalidInput(event, "`FEATURE_MEETING_SUMMARY_V2=true`에서 사용할 수 있는 기능입니다.")
            return
        }
        val itemId = event.getOption(OPTION_ITEM_ID_KO)?.asLong
        if (itemId == null || itemId <= 0) {
            discordReplyFactory.invalidInput(event, "취소할 항목 ID(아이디)를 입력해 주세요.")
            return
        }
        val fallbackThreadId = event.channel.takeIf { event.channelType.isThread }?.idLong
        val requestedMeetingSessionId = parseMeetingSessionIdOption(event)
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        meetingService.cancelStructuredItem(
                            guildId = guild.idLong,
                            requestedBy = member.idLong,
                            fallbackThreadId = fallbackThreadId,
                            requestedMeetingSessionId = requestedMeetingSessionId,
                            itemId = itemId,
                        )
                    }.fold(
                        onSuccess = { result ->
                            replyCancelItemResult(ctx, result)
                        },
                        onFailure = { exception ->
                            log.error("회의 항목 취소 실패: guildId={}, userId={}, itemId={}", guild.idLong, member.idLong, itemId, exception)
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/회의 항목 동작:취소 아이디:<ID>` 명령을 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/회의 항목 동작:취소 아이디:<ID>` 명령을 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun replyListItemsResult(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        result: MeetingService.StructuredListResult,
    ) {
        when (result) {
            is MeetingService.StructuredListResult.Success -> {
                if (result.items.isEmpty()) {
                    interactionReliabilityGuard.safeEditReply(
                        ctx,
                        "회의 항목이 아직 없습니다.\n`/회의 기록`(또는 `/결정` `/액션` `/투두`)로 먼저 기록해 주세요.",
                    )
                    return
                }
                val lines = result.items.map { item ->
                    "[${item.id}] ${resolveCaptureTypeLabel(item.type)}: ${item.summary}"
                }
                interactionReliabilityGuard.safeEditReply(
                    ctx,
                    "회의 항목 목록 (세션 `${result.sessionId}` / 스레드 <#${result.threadId}>)\n${lines.joinToString("\n")}",
                )
            }

            MeetingService.StructuredListResult.SessionNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "진행 중인 회의를 찾지 못했습니다.")
            }

            MeetingService.StructuredListResult.MeetingIdRequired -> {
                interactionReliabilityGuard.safeEditReply(
                    ctx,
                    "현재 채널에서는 `회의아이디`가 필요합니다. 예: `/회의 항목 동작:조회 회의아이디:<ID>`",
                )
            }

            MeetingService.StructuredListResult.MeetingNotActive -> {
                interactionReliabilityGuard.safeEditReply(ctx, "현재 스레드의 회의는 이미 종료되었습니다.")
            }

            is MeetingService.StructuredListResult.ThreadNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "회의 스레드를 찾지 못했습니다. (threadId=${result.threadId})")
            }
        }
    }

    private fun replyCancelItemResult(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        result: MeetingService.StructuredCancelResult,
    ) {
        when (result) {
            is MeetingService.StructuredCancelResult.Success -> {
                interactionReliabilityGuard.safeEditReply(
                    ctx,
                    "회의 항목을 취소했습니다.\n" +
                        "세션 `${result.sessionId}` / 스레드 <#${result.threadId}>\n" +
                        "항목 `[${result.item.id}]` ${resolveCaptureTypeLabel(result.item.type)}: ${result.item.summary}",
                )
            }

            MeetingService.StructuredCancelResult.SessionNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "진행 중인 회의를 찾지 못했습니다.")
            }

            MeetingService.StructuredCancelResult.MeetingIdRequired -> {
                interactionReliabilityGuard.safeEditReply(
                    ctx,
                    "현재 채널에서는 `회의아이디`가 필요합니다. 예: `/회의 항목 동작:취소 아이디:<항목ID> 회의아이디:<ID>`",
                )
            }

            MeetingService.StructuredCancelResult.MeetingNotActive -> {
                interactionReliabilityGuard.safeEditReply(ctx, "현재 스레드의 회의는 이미 종료되었습니다.")
            }

            MeetingService.StructuredCancelResult.ItemNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "취소할 항목 ID를 찾지 못했습니다.")
            }

            is MeetingService.StructuredCancelResult.ThreadNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "회의 스레드를 찾지 못했습니다. (threadId=${result.threadId})")
            }
        }
    }

    private fun resolveCaptureTypeLabel(type: MeetingService.StructuredCaptureType): String {
        return when (type) {
            MeetingService.StructuredCaptureType.DECISION -> "결정"
            MeetingService.StructuredCaptureType.ACTION -> "액션"
            MeetingService.StructuredCaptureType.TODO -> "투두"
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val threadToken = parseThreadInteractionToken(event.componentId)
        if (threadToken != null) {
            handleThreadActionButton(event, threadToken)
            return
        }

        val token = parseSummaryInteractionToken(event.componentId) ?: return
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!permissionGate.canAdminAction(guild.idLong, member)) {
            event.reply("요약 작업 권한이 없습니다.").setEphemeral(true).queue()
            return
        }

        when (token.action) {
            SummaryAction.REGENERATE -> {
                handleRegenerateButton(event, guild.idLong, member.idLong, token.sessionId)
            }

            SummaryAction.ADD_DECISION -> {
                val modal = Modal.create(MeetingSummaryActionIds.decisionModal(token.sessionId), "결정 추가")
                    .addComponents(
                        Label.of(
                            "결정 내용",
                            TextInput.create(MODAL_FIELD_CONTENT, TextInputStyle.PARAGRAPH)
                                .setRequired(true)
                                .setMaxLength(500)
                                .setPlaceholder("예) 결정: 다음 주까지 MVP 배포")
                                .build(),
                        ),
                    )
                    .build()
                event.replyModal(modal).queue()
            }

            SummaryAction.ADD_ACTION -> {
                val modal = Modal.create(MeetingSummaryActionIds.actionModal(token.sessionId), "액션 추가")
                    .addComponents(
                        Label.of(
                            "액션 내용",
                            TextInput.create(MODAL_FIELD_CONTENT, TextInputStyle.PARAGRAPH)
                                .setRequired(true)
                                .setMaxLength(500)
                                .setPlaceholder("예) 액션: 홍길동이 CI 파이프라인 정리")
                                .build(),
                        ),
                    )
                    .build()
                event.replyModal(modal).queue()
            }

            SummaryAction.ADD_TODO -> {
                val modal = Modal.create(MeetingSummaryActionIds.todoModal(token.sessionId), "할 일 추가")
                    .addComponents(
                        Label.of(
                            "할 일 내용",
                            TextInput.create(MODAL_FIELD_CONTENT, TextInputStyle.PARAGRAPH)
                                .setRequired(true)
                                .setMaxLength(500)
                                .setPlaceholder("예) TODO: QA 체크리스트 최종 점검")
                                .build(),
                        ),
                    )
                    .build()
                event.replyModal(modal).queue()
            }

            SummaryAction.ITEM_MANAGE -> {
                interactionReliabilityGuard.safeDefer(
                    interaction = event,
                    preferUpdate = false,
                    onDeferred = { ctx ->
                        interactionReliabilityGuard.safeEditReply(
                            ctx,
                            "항목 수정 가이드\n" +
                                "• 결정/액션/할 일 추가: 요약 카드의 추가 버튼 사용\n" +
                                "• 항목 조회: `/회의 항목 동작:조회 회의아이디:${token.sessionId}`\n" +
                                "• 항목 취소: `/회의 항목 동작:취소 아이디:<항목ID> 회의아이디:${token.sessionId}`",
                        )
                    },
                    onFailure = { ctx, _ ->
                        interactionReliabilityGuard.safeFailureReply(
                            ctx = ctx,
                            alternativeCommandGuide = "`/회의 항목 동작:조회`로 상태를 확인해 주세요.",
                        )
                    },
                )
            }

            SummaryAction.SOURCE -> {
                interactionReliabilityGuard.safeDefer(
                    interaction = event,
                    preferUpdate = false,
                    onDeferred = { ctx ->
                        val artifact = meetingSummaryArtifactService.findLatestByMeetingSessionId(token.sessionId)
                        if (artifact == null) {
                            interactionReliabilityGuard.safeEditReply(ctx, "원문 메타데이터를 찾지 못했습니다.")
                            return@safeDefer
                        }
                        interactionReliabilityGuard.safeEditReply(
                            ctx,
                            "원문 수집 정보\n" +
                                "• sessionId: `${artifact.meetingSessionId}`\n" +
                                "• 메시지 수: `${artifact.messageCount}`개\n" +
                                "• 수집 범위: `${artifact.sourceWindowStart}` ~ `${artifact.sourceWindowEnd}`\n" +
                                "• 버전: `${artifact.version}`",
                        )
                    },
                    onFailure = { ctx, _ ->
                        interactionReliabilityGuard.safeFailureReply(
                            ctx = ctx,
                            alternativeCommandGuide = "`/회의 종료`를 다시 실행해 주세요.",
                        )
                    },
                )
            }
        }
    }

    private fun handleThreadActionButton(event: ButtonInteractionEvent, token: ThreadToken) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!featureFlags.meetingSummaryV2) {
            event.reply("`FEATURE_MEETING_SUMMARY_V2=true`에서 사용할 수 있는 기능입니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        when (token.action) {
            ThreadAction.ADD_DECISION -> {
                val modal = Modal.create(MeetingThreadActionIds.decisionModal(token.sessionId), "결정 추가")
                    .addComponents(
                        Label.of(
                            "결정 내용",
                            TextInput.create(MODAL_FIELD_CONTENT, TextInputStyle.PARAGRAPH)
                                .setRequired(true)
                                .setMaxLength(500)
                                .setPlaceholder("예) 결정: 다음 주까지 MVP 배포")
                                .build(),
                        ),
                    )
                    .build()
                event.replyModal(modal).queue()
            }

            ThreadAction.ADD_ACTION -> {
                val modal = Modal.create(MeetingThreadActionIds.actionModal(token.sessionId), "액션 추가")
                    .addComponents(
                        Label.of(
                            "액션 내용",
                            TextInput.create(MODAL_FIELD_CONTENT, TextInputStyle.PARAGRAPH)
                                .setRequired(true)
                                .setMaxLength(500)
                                .setPlaceholder("예) 액션: 홍길동이 CI 파이프라인 정리")
                                .build(),
                        ),
                    )
                    .build()
                event.replyModal(modal).queue()
            }

            ThreadAction.ADD_TODO -> {
                val modal = Modal.create(MeetingThreadActionIds.todoModal(token.sessionId), "할 일 추가")
                    .addComponents(
                        Label.of(
                            "할 일 내용",
                            TextInput.create(MODAL_FIELD_CONTENT, TextInputStyle.PARAGRAPH)
                                .setRequired(true)
                                .setMaxLength(500)
                                .setPlaceholder("예) TODO: 인증 예외 케이스 테스트 추가")
                                .build(),
                        ),
                    )
                    .build()
                event.replyModal(modal).queue()
            }

            ThreadAction.END_MEETING -> {
                if (!permissionGate.canAdminAction(guild.idLong, member)) {
                    event.reply("회의 종료 권한이 없습니다.").setEphemeral(true).queue()
                    return
                }
                handleEndMeetingFromThreadButton(
                    event = event,
                    guildId = guild.idLong,
                    requestedBy = member.idLong,
                    meetingSessionId = token.sessionId,
                )
            }
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        val threadToken = parseThreadModalToken(event.modalId)
        if (threadToken != null) {
            handleThreadActionModal(event, threadToken)
            return
        }

        val token = parseSummaryModalToken(event.modalId) ?: return
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!permissionGate.canAdminAction(guild.idLong, member)) {
            event.reply("요약 작업 권한이 없습니다.").setEphemeral(true).queue()
            return
        }

        val content = event.getValue(MODAL_FIELD_CONTENT)?.asString.orEmpty().trim()
        if (content.isBlank()) {
            event.reply("내용은 비워둘 수 없습니다.").setEphemeral(true).queue()
            return
        }

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                val result = when (token.action) {
                    SummaryAction.ADD_DECISION -> meetingService.addManualDecision(
                        guildId = guild.idLong,
                        requestedBy = member.idLong,
                        meetingSessionId = token.sessionId,
                        decision = content,
                    )

                    SummaryAction.ADD_ACTION -> meetingService.addManualAction(
                        guildId = guild.idLong,
                        requestedBy = member.idLong,
                        meetingSessionId = token.sessionId,
                        action = content,
                    )

                    SummaryAction.ADD_TODO -> meetingService.addManualTodo(
                        guildId = guild.idLong,
                        requestedBy = member.idLong,
                        meetingSessionId = token.sessionId,
                        todo = content,
                    )

                    else -> MeetingService.SummaryMutationResult.ArtifactNotFound
                }
                replyMutationResult(ctx, result)
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/회의 종료`를 다시 실행해 주세요.",
                )
            },
        )
    }

    private fun handleThreadActionModal(event: ModalInteractionEvent, token: ThreadToken) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!featureFlags.meetingSummaryV2) {
            event.reply("`FEATURE_MEETING_SUMMARY_V2=true`에서 사용할 수 있는 기능입니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val content = event.getValue(MODAL_FIELD_CONTENT)?.asString.orEmpty().trim()
        if (content.isBlank()) {
            event.reply("내용은 비워둘 수 없습니다.").setEphemeral(true).queue()
            return
        }

        val fallbackThreadId = event.channel.takeIf { it.type.isThread }?.idLong
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                val result = when (token.action) {
                    ThreadAction.ADD_DECISION -> meetingService.captureDecision(
                        guildId = guild.idLong,
                        requestedBy = member.idLong,
                        fallbackThreadId = fallbackThreadId,
                        requestedMeetingSessionId = token.sessionId,
                        content = content,
                    )

                    ThreadAction.ADD_ACTION -> meetingService.captureAction(
                        guildId = guild.idLong,
                        requestedBy = member.idLong,
                        fallbackThreadId = fallbackThreadId,
                        requestedMeetingSessionId = token.sessionId,
                        content = content,
                        assigneeUserId = null,
                        dueDateLocal = null,
                    )

                    ThreadAction.ADD_TODO -> meetingService.captureTodo(
                        guildId = guild.idLong,
                        requestedBy = member.idLong,
                        fallbackThreadId = fallbackThreadId,
                        requestedMeetingSessionId = token.sessionId,
                        content = content,
                    )

                    ThreadAction.END_MEETING -> MeetingService.StructuredCaptureResult.SessionNotFound
                }
                replyCaptureResult(ctx, result)
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/회의 기록` 명령으로 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun handleRegenerateButton(
        event: ButtonInteractionEvent,
        guildId: Long,
        requestedBy: Long,
        sessionId: Long,
    ) {
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                interactionReliabilityGuard.safeEditReply(ctx, "요약 생성 중... (수집 메시지: 계산 중)")
                CompletableFuture.runAsync {
                    runCatching {
                        meetingService.regenerateSummary(
                            guildId = guildId,
                            requestedBy = requestedBy,
                            meetingSessionId = sessionId,
                            progress = { progress ->
                                if (progress is MeetingService.SummaryProgress.Collected) {
                                    interactionReliabilityGuard.safeEditReply(
                                        ctx,
                                        "요약 생성 중... (수집 메시지: ${progress.messageCount}개)",
                                    )
                                }
                            },
                        )
                    }.fold(
                        onSuccess = { result ->
                            replyMutationResult(ctx, result)
                        },
                        onFailure = { exception ->
                            log.error("요약 재생성 실패: sessionId={}", sessionId, exception)
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/회의 종료`를 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/회의 종료`를 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun handleEndMeetingFromThreadButton(
        event: ButtonInteractionEvent,
        guildId: Long,
        requestedBy: Long,
        meetingSessionId: Long,
    ) {
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                interactionReliabilityGuard.safeEditReply(ctx, "요약 생성 중... (수집 메시지: 계산 중)")
                CompletableFuture.runAsync {
                    runCatching {
                        meetingService.endMeeting(
                            guildId = guildId,
                            requestedBy = requestedBy,
                            meetingSessionId = meetingSessionId,
                            progress = { progress ->
                                if (progress is MeetingService.SummaryProgress.Collected) {
                                    interactionReliabilityGuard.safeEditReply(
                                        ctx,
                                        "요약 생성 중... (수집 메시지: ${progress.messageCount}개)",
                                    )
                                }
                            },
                        )
                    }.fold(
                        onSuccess = { result -> replyEndMeetingFromThreadResult(ctx, result) },
                        onFailure = { exception ->
                            log.error("회의 종료 실패: sessionId={}", meetingSessionId, exception)
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/회의 종료`를 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/회의 종료`를 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun replyEndMeetingFromThreadResult(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        result: MeetingService.EndResult,
    ) {
        when (result) {
            is MeetingService.EndResult.Success -> {
                val archiveState = if (result.archived) "성공" else "실패(권한/상태 확인 필요)"
                val agendaLine = buildAgendaSummaryLine(result.agendaTitle, result.agendaUrl)
                interactionReliabilityGuard.safeEditReply(
                    ctx,
                    "회의 종료 완료\n" +
                        "세션ID `${result.sessionId}`\n" +
                        "스레드 <#${result.threadId}>\n" +
                        "요약 메시지ID `${result.summaryMessageId}` / 요약ID `${result.summaryArtifactId}`\n" +
                        "분석 메시지 `${result.sourceMessageCount}`건 / 참여자 `${result.participantCount}`명\n" +
                        "$agendaLine\n" +
                        "결정 `${result.decisions.size}`건 / 액션 `${result.actionItems.size}`건 / TODO `${result.todos.size}`건\n" +
                        "아카이브 `${archiveState}`",
                )
            }

            MeetingService.EndResult.SessionNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "종료할 회의 세션을 찾지 못했습니다.")
            }

            MeetingService.EndResult.AlreadyEnded -> {
                interactionReliabilityGuard.safeEditReply(ctx, "이미 종료된 회의입니다.")
            }

            is MeetingService.EndResult.ClosedMissingThread -> {
                interactionReliabilityGuard.safeEditReply(
                    ctx,
                    "회의 스레드가 이미 삭제되어 세션만 종료 처리했습니다. 세션ID `${result.sessionId}` / threadId `${result.threadId}`",
                )
            }

            is MeetingService.EndResult.ThreadNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "요약 대상 스레드를 찾지 못했습니다. (threadId=${result.threadId})")
            }
        }
    }

    private fun replyMutationResult(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        result: MeetingService.SummaryMutationResult,
    ) {
        when (result) {
            is MeetingService.SummaryMutationResult.Success -> {
                interactionReliabilityGuard.safeEditReply(
                    ctx,
                    "요약이 업데이트되었습니다.\n" +
                        "세션ID `${result.sessionId}` / 스레드 <#${result.threadId}> / 요약 메시지ID `${result.summaryMessageId}`\n" +
                        "메시지 `${result.sourceMessageCount}`개 / 참여자 `${result.participantCount}`명 / 요약ID `${result.summaryArtifactId}`\n" +
                        "결정 `${result.decisions.size}`건 / 액션 `${result.actionItems.size}`건 / TODO `${result.todos.size}`건",
                )
            }

            MeetingService.SummaryMutationResult.SessionNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "대상 회의 세션을 찾지 못했습니다.")
            }

            MeetingService.SummaryMutationResult.ArtifactNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "요약 아티팩트를 찾지 못했습니다. 먼저 요약을 생성해 주세요.")
            }

            is MeetingService.SummaryMutationResult.ThreadNotFound -> {
                interactionReliabilityGuard.safeEditReply(ctx, "요약 대상 스레드를 찾지 못했습니다. (threadId=${result.threadId})")
            }
        }
    }

    private fun startMeeting(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        if (!permissionGate.canStartMeeting(guild.idLong, member)) {
            discordReplyFactory.accessDenied(event, "회의 시작 권한이 없습니다.")
            return
        }

        val channelOption = event.getOption(OPTION_CHANNEL_KO)?.asChannel
        if (channelOption != null && channelOption.type != ChannelType.TEXT) {
            discordReplyFactory.invalidInput(event, "텍스트 채널만 지정할 수 있습니다.")
            return
        }
        val targetChannelId = channelOption?.idLong
        val fallbackChannelId = if (event.channelType == ChannelType.TEXT) event.channel.idLong else null

        when (
            val result = meetingService.startMeeting(
                guildId = guild.idLong,
                requestedBy = member.idLong,
                targetChannelId = targetChannelId,
                fallbackChannelId = fallbackChannelId,
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
                discordReplyFactory.invalidInput(event, "회의 채널을 찾지 못했습니다. `/설정 마법사`로 회의채널을 먼저 지정해 주세요.")
            }

            MeetingService.StartResult.ChannelNotFound -> {
                discordReplyFactory.invalidInput(event, "회의 스레드를 만들 채널을 찾을 수 없습니다.")
            }

            MeetingService.StartResult.ThreadCreateFailed -> {
                discordReplyFactory.invalidInput(event, "회의 스레드 생성에 실패했습니다.")
            }
        }
    }

    private fun listActiveMeetings(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (!permissionGate.canAdminAction(guild.idLong, member)) {
            discordReplyFactory.accessDenied(event, "회의 진행 현황 조회 권한이 없습니다.")
            return
        }
        val result = meetingService.listActiveMeetings(guild.idLong)
        when (result) {
            is MeetingService.ActiveMeetingsResult.Success -> {
                if (result.meetings.isEmpty()) {
                    event.reply("현재 진행 중인 회의가 없습니다.")
                        .setEphemeral(true)
                        .queue()
                    return
                }
                val lines = result.meetings.mapIndexed { index, meeting ->
                    val board = meeting.boardChannelId?.let { "<#$it>" } ?: "미지정"
                    "${index + 1}. 회의ID `${meeting.sessionId}` | 채널 $board | 스레드 <#${meeting.threadId}> | 시작 ${KstTime.format(meeting.startedAt)} | 시작자 <@${meeting.startedBy}>"
                }
                event.reply("진행 중 회의 목록\n${lines.joinToString("\n")}")
                    .setEphemeral(true)
                    .queue()
            }
        }
    }

    private fun listMeetingHistory(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: run {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        val days = event.getOption(OPTION_DAYS_KO)?.asInt ?: DEFAULT_HISTORY_DAYS
        val statusFilter = parseHistoryStatusFilter(event.getOption(OPTION_STATUS_KO)?.asString)
        val result = meetingService.listMeetingHistory(
            guildId = guild.idLong,
            days = days,
            statusFilter = statusFilter,
        )
        when (result) {
            MeetingService.HistoryResult.InvalidDays -> {
                discordReplyFactory.invalidInput(event, "일수는 1~90 사이로 입력해 주세요.")
            }

            is MeetingService.HistoryResult.Success -> {
                if (result.meetings.isEmpty()) {
                    event.reply("최근 ${days}일 회의 내역이 없습니다.").setEphemeral(true).queue()
                    return
                }
                val lines = result.meetings.mapIndexed { index, meeting ->
                    val channel = meeting.boardChannelId?.let { "<#$it>" } ?: "미지정"
                    val endedAt = meeting.endedAt?.let { KstTime.format(it) } ?: "-"
                    "${index + 1}. ID `${meeting.sessionId}` | ${meeting.status.name} | 채널 $channel | 스레드 <#${meeting.threadId}> | 시작 ${KstTime.format(meeting.startedAt)} | 종료 $endedAt | 결정/액션/TODO ${meeting.decisionCount}/${meeting.actionCount}/${meeting.todoCount}"
                }
                event.reply("최근 ${days}일 회의 내역\n${lines.joinToString("\n")}")
                    .setEphemeral(true)
                    .queue()
            }
        }
    }

    private fun showMeetingDetail(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: run {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        val meetingSessionId = parseMeetingSessionIdOption(event)
        if (meetingSessionId == null || meetingSessionId <= 0) {
            discordReplyFactory.invalidInput(event, "조회할 `회의아이디`를 입력해 주세요. 예: `/회의 상세 회의아이디:123`")
            return
        }
        val result = meetingService.getMeetingDetail(
            guildId = guild.idLong,
            meetingSessionId = meetingSessionId,
        )
        when (result) {
            MeetingService.MeetingDetailResult.NotFound -> {
                event.reply("해당 회의를 찾지 못했습니다. 회의ID를 확인해 주세요.")
                    .setEphemeral(true)
                    .queue()
            }

            is MeetingService.MeetingDetailResult.Success -> {
                val detail = result.detail
                val endedAt = detail.endedAt?.let { KstTime.format(it) } ?: "-"
                val agendaLine = buildAgendaSummaryLine(detail.agendaTitle, detail.agendaUrl)
                val summaryLine = detail.summaryMessageId?.let { "`$it`" } ?: "-"
                val decisions = toInlineList(detail.decisions)
                val actions = toInlineList(detail.actions)
                val todos = toInlineList(detail.todos)
                event.reply(
                    "회의 상세\n" +
                        "세션ID `${detail.sessionId}` / 상태 `${detail.status.name}`\n" +
                        "채널 ${detail.boardChannelId?.let { "<#$it>" } ?: "미지정"} / 스레드 <#${detail.threadId}>\n" +
                        "시작 ${KstTime.format(detail.startedAt)} / 종료 $endedAt\n" +
                        "시작자 <@${detail.startedBy}> / 종료자 ${detail.endedBy?.let { "<@$it>" } ?: "-"}\n" +
                        "요약 메시지ID $summaryLine\n" +
                        "$agendaLine\n" +
                        "결정/액션/TODO ${detail.decisionCount}/${detail.actionCount}/${detail.todoCount}\n" +
                        "결정: $decisions\n" +
                        "액션: $actions\n" +
                        "TODO: $todos",
                ).setEphemeral(true).queue()
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

        val meetingSessionId = parseMeetingSessionIdOption(event)
        if (meetingSessionId == null || meetingSessionId <= 0) {
            discordReplyFactory.invalidInput(event, "종료할 `회의아이디`를 입력해 주세요. 예: `/회의 종료 회의아이디:123`")
            return
        }
        event.deferReply(true).queue(
            {
                event.hook.editOriginal("요약 생성 중... (수집 메시지: 계산 중)").queue()
                CompletableFuture.runAsync {
                    runCatching {
                        handleEndMeetingAfterDefer(
                            event = event,
                            guildId = guild.idLong,
                            requestedBy = member.idLong,
                            meetingSessionId = meetingSessionId,
                        )
                    }.onFailure { exception ->
                        handleEndMeetingFailure(event, guild.idLong, meetingSessionId, exception)
                    }
                }
            },
            {
                discordReplyFactory.internalError(event, "응답 초기화에 실패했습니다. 잠시 후 다시 시도해 주세요.")
            },
        )
    }

    private fun handleMeetingAgendaSet(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        val rawUrl = event.getOption(OPTION_URL_KO)?.asString
        if (rawUrl.isNullOrBlank()) {
            discordReplyFactory.invalidInput(event, "링크 옵션은 필수입니다. 예: `/회의 안건등록 링크:https://...`")
            return
        }
        val result = agendaService.setTodayAgenda(
            guildId = guild.idLong,
            requesterUserId = member.idLong,
            requesterRoleIds = member.roles.map { it.idLong }.toSet(),
            hasManageServerPermission = hasManageServerPermission(member),
            rawUrl = rawUrl,
            rawTitle = event.getOption(OPTION_TITLE_KO)?.asString,
        )
        when (result) {
            is AgendaService.SetAgendaResult.Success -> {
                val action = if (result.updated) "업데이트" else "등록"
                event.reply("오늘 안건 링크를 $action 했습니다.\n제목: ${result.title}")
                    .addComponents(ActionRow.of(Button.link(result.url, "안건 링크 열기")))
                    .setEphemeral(true)
                    .queue()
            }

            AgendaService.SetAgendaResult.Forbidden -> {
                discordReplyFactory.accessDenied(event, "안건 등록 권한이 없습니다.")
            }

            AgendaService.SetAgendaResult.InvalidUrl -> {
                discordReplyFactory.invalidInput(event, "URL 형식이 올바르지 않습니다. http/https만 허용됩니다.")
            }

            AgendaService.SetAgendaResult.InvalidTitle -> {
                discordReplyFactory.invalidInput(event, "제목은 255자 이하여야 합니다.")
            }
        }
    }

    private fun handleMeetingAgendaToday(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: run {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        val agenda = agendaService.getTodayAgenda(guild.idLong)
        if (agenda == null) {
            event.reply("오늘 안건 링크가 아직 등록되지 않았습니다.")
                .setEphemeral(true)
                .queue()
            return
        }
        event.reply("오늘 안건: ${agenda.title} (${agenda.dateLocal})")
            .addComponents(ActionRow.of(Button.link(agenda.url, "안건 링크 열기")))
            .setEphemeral(true)
            .queue()
    }

    private fun handleMeetingAgendaRecent(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: run {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }
        val days = event.getOption(OPTION_DAYS_KO)?.asInt ?: DEFAULT_AGENDA_RECENT_DAYS
        when (val result = agendaService.getRecentAgendas(guild.idLong, days)) {
            AgendaService.RecentAgendaResult.InvalidDays -> {
                discordReplyFactory.invalidInput(event, "일수는 1 이상의 숫자여야 합니다.")
            }

            AgendaService.RecentAgendaResult.Empty -> {
                event.reply("최근 ${days}일 안건 링크가 없습니다.").setEphemeral(true).queue()
            }

            is AgendaService.RecentAgendaResult.Success -> {
                val lines = result.agendas.map {
                    "- ${it.dateLocal}: ${it.title} (${it.url})"
                }
                event.reply("최근 ${days}일 안건 링크\n${lines.joinToString("\n")}")
                    .setEphemeral(true)
                    .queue()
            }
        }
    }

    private fun handleEndMeetingAfterDefer(
        event: SlashCommandInteractionEvent,
        guildId: Long,
        requestedBy: Long,
        meetingSessionId: Long,
    ) {
        when (
            val result = meetingService.endMeeting(
                guildId = guildId,
                requestedBy = requestedBy,
                meetingSessionId = meetingSessionId,
                progress = { progress ->
                    if (progress is MeetingService.SummaryProgress.Collected) {
                        event.hook.editOriginal("요약 생성 중... (수집 메시지: ${progress.messageCount}개)").queue()
                    }
                },
            )
        ) {
            is MeetingService.EndResult.Success -> {
                val archiveState = if (result.archived) "성공" else "실패(권한/상태 확인 필요)"
                val agendaLine = buildAgendaSummaryLine(result.agendaTitle, result.agendaUrl)
                event.hook.editOriginal(
                    "회의 종료 완료\n" +
                        "세션ID `${result.sessionId}`\n" +
                        "스레드 <#${result.threadId}>\n" +
                        "요약 메시지ID `${result.summaryMessageId}` / 요약ID `${result.summaryArtifactId}`\n" +
                        "분석 메시지 `${result.sourceMessageCount}`건 / 참여자 `${result.participantCount}`명\n" +
                        "$agendaLine\n" +
                        "결정 `${result.decisions.size}`건 / 액션 `${result.actionItems.size}`건 / TODO `${result.todos.size}`건\n" +
                        "아카이브 `${archiveState}`",
                ).queue()
            }

            MeetingService.EndResult.SessionNotFound -> {
                event.hook.editOriginal("종료할 회의 세션을 찾지 못했습니다. `회의아이디`를 확인해 주세요.")
                    .queue()
            }

            MeetingService.EndResult.AlreadyEnded -> {
                event.hook.editOriginal("이미 종료된 회의입니다.").queue()
            }

            is MeetingService.EndResult.ClosedMissingThread -> {
                event.hook.editOriginal(
                    "회의 스레드가 이미 삭제되어 세션만 종료 처리했습니다. 세션ID `${result.sessionId}` / threadId `${result.threadId}`",
                ).queue()
            }

            is MeetingService.EndResult.ThreadNotFound -> {
                event.hook.editOriginal("요약 대상 스레드를 찾지 못했습니다. (threadId=${result.threadId})")
                    .queue()
            }
        }
    }

    private fun handleEndMeetingFailure(
        event: SlashCommandInteractionEvent,
        guildId: Long,
        meetingSessionId: Long,
        exception: Throwable,
    ) {
        log.error(
            "회의 종료 처리 실패: guildId={}, meetingSessionId={}",
            guildId,
            meetingSessionId,
            exception,
        )
        runCatching {
            event.hook.editOriginal("처리 중 문제가 발생했어요. [다시 시도] [대체 명령 안내: `/회의 종료`를 다시 실행해 주세요.]")
                .queue()
        }
    }

    private fun buildAgendaSummaryLine(title: String?, url: String?): String {
        if (title.isNullOrBlank() || url.isNullOrBlank()) {
            return "연결 안건 `없음`"
        }
        return "연결 안건 `${title}` (${url})"
    }

    private fun parseRecordType(raw: String?): MeetingService.StructuredCaptureType? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return when (raw.trim().lowercase()) {
            "decision", "결정" -> MeetingService.StructuredCaptureType.DECISION
            "action", "액션" -> MeetingService.StructuredCaptureType.ACTION
            "todo", "투두" -> MeetingService.StructuredCaptureType.TODO
            else -> null
        }
    }

    private fun parseItemAction(raw: String?): ItemAction? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return when (raw.trim().lowercase()) {
            "list", "조회" -> ItemAction.LIST
            "cancel", "취소" -> ItemAction.CANCEL
            else -> null
        }
    }

    private fun parseHistoryStatusFilter(raw: String?): MeetingService.HistoryStatusFilter {
        if (raw.isNullOrBlank()) {
            return MeetingService.HistoryStatusFilter.ALL
        }
        return when (raw.lowercase()) {
            "active", "진행중" -> MeetingService.HistoryStatusFilter.ACTIVE
            "ended", "종료" -> MeetingService.HistoryStatusFilter.ENDED
            else -> MeetingService.HistoryStatusFilter.ALL
        }
    }

    private fun hasManageServerPermission(member: net.dv8tion.jda.api.entities.Member): Boolean {
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true
        }
        return member.hasPermission(Permission.MANAGE_SERVER)
    }

    private fun toInlineList(items: List<String>): String {
        if (items.isEmpty()) {
            return "-"
        }
        return items.take(3).joinToString(" | ")
    }

    private fun parseMeetingSessionIdOption(event: SlashCommandInteractionEvent): Long? {
        return event.getOption(OPTION_MEETING_ID_KO)?.asLong
    }

    private fun isBlockedByHomeChannelGuard(event: SlashCommandInteractionEvent, guildId: Long): Boolean {
        val meetingChannelId = guildConfigService.getBoardChannels(guildId).meetingChannelId
        val guardResult = homeChannelGuard.validate(
            guildId = guildId,
            currentChannelId = event.channel.idLong,
            featureChannelId = meetingChannelId,
            featureName = "회의",
            setupCommand = "/설정 마법사 회의채널:#회의",
            usageCommand = "/회의 시작",
        )
        if (guardResult is HomeChannelGuard.GuardResult.Allowed) {
            return false
        }
        val blocked = guardResult as HomeChannelGuard.GuardResult.Blocked
        discordReplyFactory.invalidInput(event, blocked.message)
        return true
    }

    private fun parseSummaryInteractionToken(customId: String?): SummaryToken? {
        if (customId.isNullOrBlank()) {
            return null
        }
        val parts = customId.split(":")
        if (parts.size < 4) {
            return null
        }
        if (parts[0] != "meeting" || parts[1] != "summary") {
            return null
        }
        val sessionId = parts.last().toLongOrNull() ?: return null
        val action = when (parts[2]) {
            "regen" -> SummaryAction.REGENERATE
            "add_decision" -> SummaryAction.ADD_DECISION
            "add_action" -> SummaryAction.ADD_ACTION
            "add_todo" -> SummaryAction.ADD_TODO
            "item_manage" -> SummaryAction.ITEM_MANAGE
            "source" -> SummaryAction.SOURCE
            else -> return null
        }
        return SummaryToken(action = action, sessionId = sessionId)
    }

    private fun parseSummaryModalToken(modalId: String?): SummaryToken? {
        if (modalId.isNullOrBlank()) {
            return null
        }
        val parts = modalId.split(":")
        if (parts.size < 5) {
            return null
        }
        if (parts[0] != "meeting" || parts[1] != "summary" || parts[2] != "modal") {
            return null
        }
        val sessionId = parts.last().toLongOrNull() ?: return null
        val action = when (parts[3]) {
            "decision" -> SummaryAction.ADD_DECISION
            "action" -> SummaryAction.ADD_ACTION
            "todo" -> SummaryAction.ADD_TODO
            else -> return null
        }
        return SummaryToken(action = action, sessionId = sessionId)
    }

    private fun parseThreadInteractionToken(customId: String?): ThreadToken? {
        if (customId.isNullOrBlank()) {
            return null
        }
        val parts = customId.split(":")
        if (parts.size < 4) {
            return null
        }
        if (parts[0] != "meeting" || parts[1] != "thread") {
            return null
        }
        val sessionId = parts.last().toLongOrNull() ?: return null
        val action = when (parts[2]) {
            "add_decision" -> ThreadAction.ADD_DECISION
            "add_action" -> ThreadAction.ADD_ACTION
            "add_todo" -> ThreadAction.ADD_TODO
            "end" -> ThreadAction.END_MEETING
            else -> return null
        }
        return ThreadToken(action = action, sessionId = sessionId)
    }

    private fun parseThreadModalToken(modalId: String?): ThreadToken? {
        if (modalId.isNullOrBlank()) {
            return null
        }
        val parts = modalId.split(":")
        if (parts.size < 5) {
            return null
        }
        if (parts[0] != "meeting" || parts[1] != "thread" || parts[2] != "modal") {
            return null
        }
        val sessionId = parts.last().toLongOrNull() ?: return null
        val action = when (parts[3]) {
            "decision" -> ThreadAction.ADD_DECISION
            "action" -> ThreadAction.ADD_ACTION
            "todo" -> ThreadAction.ADD_TODO
            else -> return null
        }
        return ThreadToken(action = action, sessionId = sessionId)
    }

    private data class SummaryToken(
        val action: SummaryAction,
        val sessionId: Long,
    )

    private data class ThreadToken(
        val action: ThreadAction,
        val sessionId: Long,
    )

    private enum class ItemAction {
        LIST,
        CANCEL,
    }

    private enum class SummaryAction {
        REGENERATE,
        ADD_DECISION,
        ADD_ACTION,
        ADD_TODO,
        ITEM_MANAGE,
        SOURCE,
    }

    private enum class ThreadAction {
        ADD_DECISION,
        ADD_ACTION,
        ADD_TODO,
        END_MEETING,
    }

    companion object {
        private const val COMMAND_NAME_KO = "회의"
        private const val COMMAND_NAME_EN = "meeting"
        private const val COMMAND_DECISION_KO = "결정"
        private const val COMMAND_ACTION_KO = "액션"
        private const val COMMAND_TODO_KO = "투두"
        private const val SUBCOMMAND_START_KO = "시작"
        private const val SUBCOMMAND_START_EN = "start"
        private const val SUBCOMMAND_END_KO = "종료"
        private const val SUBCOMMAND_END_EN = "end"
        private const val SUBCOMMAND_ACTIVE_KO = "진행중"
        private const val SUBCOMMAND_ACTIVE_EN = "active"
        private const val SUBCOMMAND_HISTORY_KO = "내역"
        private const val SUBCOMMAND_HISTORY_EN = "history"
        private const val SUBCOMMAND_DETAIL_KO = "상세"
        private const val SUBCOMMAND_DETAIL_EN = "detail"
        private const val SUBCOMMAND_RECORD_KO = "기록"
        private const val SUBCOMMAND_ITEM_KO = "항목"
        private const val SUBCOMMAND_ITEM_LIST_KO = "항목조회"
        private const val SUBCOMMAND_ITEM_CANCEL_KO = "항목취소"
        private const val SUBCOMMAND_AGENDA_SET_KO = "안건등록"
        private const val SUBCOMMAND_AGENDA_SET_EN = "agenda-set"
        private const val SUBCOMMAND_AGENDA_GET_KO = "안건조회"
        private const val SUBCOMMAND_AGENDA_GET_EN = "agenda-get"
        private const val SUBCOMMAND_AGENDA_RECENT_KO = "안건최근"
        private const val SUBCOMMAND_AGENDA_RECENT_EN = "agenda-recent"
        private const val OPTION_CHANNEL_KO = "채널"
        private const val OPTION_MEETING_ID_KO = "회의아이디"
        private const val OPTION_STATUS_KO = "상태"
        private const val OPTION_DAYS_KO = "일수"
        private const val OPTION_URL_KO = "링크"
        private const val OPTION_TITLE_KO = "제목"
        private const val OPTION_CONTENT_KO = "내용"
        private const val OPTION_ASSIGNEE_KO = "담당자"
        private const val OPTION_DUE_DATE_KO = "기한"
        private const val OPTION_RECORD_TYPE_KO = "유형"
        private const val OPTION_ITEM_ACTION_KO = "동작"
        private const val OPTION_ITEM_ID_KO = "아이디"
        private const val MODAL_FIELD_CONTENT = "내용"
        private const val DEFAULT_HISTORY_DAYS = 14
        private const val DEFAULT_AGENDA_RECENT_DAYS = 7
    }
}
