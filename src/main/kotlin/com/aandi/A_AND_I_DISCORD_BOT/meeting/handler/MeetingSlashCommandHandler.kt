package com.aandi.A_AND_I_DISCORD_BOT.meeting.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.HomeChannelGuard
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.common.config.FeatureFlagsProperties
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.DiscordReplyFactory
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.InteractionReliabilityGuard
import com.aandi.A_AND_I_DISCORD_BOT.meeting.service.MeetingService
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.service.MeetingSummaryArtifactService
import com.aandi.A_AND_I_DISCORD_BOT.meeting.ui.MeetingSummaryActionIds
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
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
        if (event.subcommandName == SUBCOMMAND_ITEM_LIST_KO) {
            listMeetingItems(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_ITEM_CANCEL_KO) {
            cancelMeetingItem(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_AGENDA_SET_KO || event.subcommandName == SUBCOMMAND_AGENDA_SET_EN) {
            replyAgendaCommandMigrationForSet(event)
            return
        }
        if (event.subcommandName == SUBCOMMAND_AGENDA_GET_KO || event.subcommandName == SUBCOMMAND_AGENDA_GET_EN) {
            replyAgendaCommandMigrationForGet(event)
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
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        meetingService.listStructuredItems(
                            guildId = guild.idLong,
                            fallbackThreadId = fallbackThreadId,
                        )
                    }.fold(
                        onSuccess = { result ->
                            replyListItemsResult(ctx, result)
                        },
                        onFailure = { exception ->
                            log.error("회의 항목 조회 실패: guildId={}, userId={}", guild.idLong, member.idLong, exception)
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/회의 항목조회` 명령을 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/회의 항목조회` 명령을 다시 시도해 주세요.",
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
                                alternativeCommandGuide = "`/회의 항목취소 아이디:<ID>` 명령을 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/회의 항목취소 아이디:<ID>` 명령을 다시 시도해 주세요.",
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
                        "회의 항목이 아직 없습니다.\n`/결정`, `/액션`, `/투두`로 먼저 기록해 주세요.",
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

    override fun onModalInteraction(event: ModalInteractionEvent) {
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
        if (channelOption == null || channelOption.type != ChannelType.TEXT) {
            discordReplyFactory.invalidInput(event, "텍스트 채널을 지정해 주세요.")
            return
        }

        when (
            val result = meetingService.startMeeting(
                guildId = guild.idLong,
                requestedBy = member.idLong,
                targetChannelId = channelOption.idLong,
                fallbackChannelId = null,
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
                discordReplyFactory.invalidInput(event, "홈 채널이 설정되지 않았습니다. `/홈 생성`을 먼저 실행해 주세요.")
            }

            MeetingService.StartResult.ChannelNotFound -> {
                discordReplyFactory.invalidInput(event, "회의 스레드를 만들 채널을 찾을 수 없습니다.")
            }

            MeetingService.StartResult.ThreadCreateFailed -> {
                discordReplyFactory.invalidInput(event, "회의 스레드 생성에 실패했습니다.")
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

        val requestedThreadId = parseThreadId(event.getOption(OPTION_THREAD_ID_KO)?.asString)
        val fallbackThreadId = event.channel
            .takeIf { event.channelType.isThread }
            ?.idLong
        event.deferReply(true).queue(
            {
                event.hook.editOriginal("요약 생성 중... (수집 메시지: 계산 중)").queue()
                CompletableFuture.runAsync {
                    runCatching {
                        handleEndMeetingAfterDefer(
                            event = event,
                            guildId = guild.idLong,
                            requestedBy = member.idLong,
                            fallbackThreadId = fallbackThreadId,
                            requestedThreadId = requestedThreadId,
                        )
                    }.onFailure { exception ->
                        handleEndMeetingFailure(event, guild.idLong, requestedThreadId, exception)
                    }
                }
            },
            {
                discordReplyFactory.internalError(event, "응답 초기화에 실패했습니다. 잠시 후 다시 시도해 주세요.")
            },
        )
    }

    private fun replyAgendaCommandMigrationForSet(event: SlashCommandInteractionEvent) {
        event.reply(
            "해당 기능은 `/안건 생성`으로 통합되었습니다.\n" +
                "예) `/안건 생성 링크:<URL> 제목:<선택>`",
        )
            .setEphemeral(true)
            .queue()
    }

    private fun replyAgendaCommandMigrationForGet(event: SlashCommandInteractionEvent) {
        event.reply(
            "해당 기능은 `/안건 오늘`로 통합되었습니다.\n" +
                "조회는 `/안건 오늘` 명령을 사용해 주세요.",
        )
            .setEphemeral(true)
            .queue()
    }

    private fun handleEndMeetingAfterDefer(
        event: SlashCommandInteractionEvent,
        guildId: Long,
        requestedBy: Long,
        fallbackThreadId: Long?,
        requestedThreadId: Long?,
    ) {
        when (
            val result = meetingService.endMeeting(
                guildId = guildId,
                requestedBy = requestedBy,
                fallbackThreadId = fallbackThreadId,
                requestedThreadId = requestedThreadId,
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
                event.hook.editOriginal("종료할 회의 세션을 찾지 못했습니다. 회의 스레드에서 실행하거나 스레드아이디를 지정해 주세요.")
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

    private fun parseThreadId(raw: String?): Long? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return raw.trim().toLongOrNull()
    }

    private fun handleEndMeetingFailure(
        event: SlashCommandInteractionEvent,
        guildId: Long,
        requestedThreadId: Long?,
        exception: Throwable,
    ) {
        log.error(
            "회의 종료 처리 실패: guildId={}, requestedThreadId={}",
            guildId,
            requestedThreadId,
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

    private fun isBlockedByHomeChannelGuard(event: SlashCommandInteractionEvent, guildId: Long): Boolean {
        val meetingChannelId = guildConfigService.getBoardChannels(guildId).meetingChannelId
        val guardResult = homeChannelGuard.validate(
            guildId = guildId,
            currentChannelId = event.channel.idLong,
            featureChannelId = meetingChannelId,
            featureName = "회의",
            setupCommand = "/설정 회의채널 채널:#회의",
            usageCommand = "/회의 시작 채널:#회의",
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
            else -> return null
        }
        return SummaryToken(action = action, sessionId = sessionId)
    }

    private data class SummaryToken(
        val action: SummaryAction,
        val sessionId: Long,
    )

    private enum class SummaryAction {
        REGENERATE,
        ADD_DECISION,
        ADD_ACTION,
        SOURCE,
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
        private const val SUBCOMMAND_ITEM_LIST_KO = "항목조회"
        private const val SUBCOMMAND_ITEM_CANCEL_KO = "항목취소"
        private const val SUBCOMMAND_AGENDA_SET_KO = "안건등록"
        private const val SUBCOMMAND_AGENDA_SET_EN = "agenda-set"
        private const val SUBCOMMAND_AGENDA_GET_KO = "안건조회"
        private const val SUBCOMMAND_AGENDA_GET_EN = "agenda-get"
        private const val OPTION_CHANNEL_KO = "채널"
        private const val OPTION_THREAD_ID_KO = "스레드아이디"
        private const val OPTION_CONTENT_KO = "내용"
        private const val OPTION_ASSIGNEE_KO = "담당자"
        private const val OPTION_DUE_DATE_KO = "기한"
        private const val OPTION_ITEM_ID_KO = "아이디"
        private const val MODAL_FIELD_CONTENT = "내용"
    }
}
