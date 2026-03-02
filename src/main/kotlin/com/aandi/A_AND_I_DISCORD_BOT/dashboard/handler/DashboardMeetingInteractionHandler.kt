package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.common.config.FeatureFlagsProperties
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.InteractionReliabilityGuard
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeCustomIdParser
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionPrefixHandler
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.service.MeetingService
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class DashboardMeetingInteractionHandler(
    private val permissionGate: PermissionGate,
    private val meetingService: MeetingService,
    private val meetingSessionRepository: MeetingSessionRepository,
    private val featureFlags: FeatureFlagsProperties,
    private val interactionReliabilityGuard: InteractionReliabilityGuard,
) : InteractionPrefixHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(prefix: String): Boolean {
        return prefix in supportedPrefixes
    }

    override fun onButton(event: ButtonInteractionEvent): Boolean {
        if (event.componentId == DashboardActionIds.MEETING_START) {
            startMeetingFromDashboard(event)
            return true
        }

        val parsed = HomeCustomIdParser.parse(event.componentId) ?: return false
        if (parsed.domain != "meeting" || parsed.action != "start") {
            return false
        }

        startMeetingFromDashboard(event)
        return true
    }

    private fun startMeetingFromDashboard(event: ButtonInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!permissionGate.canStartMeeting(guild.idLong, member)) {
            event.reply("회의 시작 권한이 없습니다.").setEphemeral(true).queue()
            return
        }

        val guildId = guild.idLong
        val requestedBy = member.idLong
        val fallbackChannelId = event.channel.idLong
        val activeSession = if (featureFlags.homeV2) {
            meetingSessionRepository.findFirstByGuildIdAndStatusOrderByStartedAtDesc(
                guildId,
                MeetingSessionStatus.ACTIVE,
            )
        } else {
            null
        }
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        if (activeSession == null) {
                            meetingService.startMeeting(
                                guildId = guildId,
                                requestedBy = requestedBy,
                                targetChannelId = null,
                                fallbackChannelId = fallbackChannelId,
                                rawTitle = null,
                            )
                        } else {
                            meetingService.endMeeting(
                                guildId = guildId,
                                requestedBy = requestedBy,
                                fallbackThreadId = event.channel.takeIf { event.channelType.isThread }?.idLong,
                                requestedThreadId = activeSession.threadId,
                            )
                        }
                    }.fold(
                        onSuccess = { result ->
                            val message = when (result) {
                                is MeetingService.StartResult -> startResultMessage(result)
                                is MeetingService.EndResult -> endResultMessage(result)
                                else -> "회의 처리 결과를 확인하지 못했습니다."
                            }
                            interactionReliabilityGuard.safeEditReply(ctx, message)
                        },
                        onFailure = { exception ->
                            val action = if (activeSession == null) "start" else "end"
                            log.error(
                                "Dashboard meeting {} failed: guildId={}, requestedBy={}, channelId={}",
                                action,
                                guildId,
                                requestedBy,
                                fallbackChannelId,
                                exception,
                            )
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/회의 종료` 또는 `/홈 설치`를 사용해 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/회의 시작` 명령으로 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun startResultMessage(result: MeetingService.StartResult): String {
        return when (result) {
            is MeetingService.StartResult.Success -> {
                "회의 스레드를 생성했습니다: <#${result.threadId}>"
            }

            is MeetingService.StartResult.AlreadyActive -> {
                "이미 진행 중인 회의가 있습니다: <#${result.threadId}>"
            }

            MeetingService.StartResult.ChannelNotConfigured -> {
                "회의 채널이 설정되지 않았습니다. `/홈 생성` 후 다시 시도해 주세요."
            }

            MeetingService.StartResult.ChannelNotFound -> {
                "회의 채널을 찾지 못했습니다."
            }

            MeetingService.StartResult.ThreadCreateFailed -> {
                "회의 스레드 생성에 실패했습니다."
            }
        }
    }

    private fun endResultMessage(result: MeetingService.EndResult): String {
        return when (result) {
            is MeetingService.EndResult.Success -> {
                "회의를 종료했습니다. 요약 메시지: `${result.summaryMessageId}`"
            }

            is MeetingService.EndResult.ClosedMissingThread -> {
                "회의 스레드를 찾지 못해 세션만 종료했습니다. threadId=${result.threadId}"
            }

            MeetingService.EndResult.SessionNotFound -> {
                "종료할 진행 중 회의를 찾지 못했습니다."
            }

            MeetingService.EndResult.AlreadyEnded -> {
                "이미 종료된 회의입니다."
            }

            is MeetingService.EndResult.ThreadNotFound -> {
                "회의 스레드를 찾지 못했습니다: <#${result.threadId}>"
            }
        }
    }

    companion object {
        private val supportedPrefixes = setOf("dash", "home")
    }
}
