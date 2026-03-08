package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.InteractionReliabilityGuard
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeCustomIdParser
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionPrefixHandler
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
        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        meetingService.startMeeting(
                            guildId = guildId,
                            requestedBy = requestedBy,
                            targetChannelId = null,
                            fallbackChannelId = fallbackChannelId,
                            rawTitle = null,
                        )
                    }.fold(
                        onSuccess = { result ->
                            interactionReliabilityGuard.safeEditReply(ctx, startResultMessage(result))
                        },
                        onFailure = { exception ->
                            log.error(
                                "Dashboard meeting start failed: guildId={}, requestedBy={}, channelId={}",
                                guildId,
                                requestedBy,
                                fallbackChannelId,
                                exception,
                            )
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/회의 시작` 또는 `/홈 설치`를 사용해 다시 시도해 주세요.",
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
                "회의 채널이 설정되지 않았습니다. `/설정 마법사 회의채널:#회의`로 먼저 설정해 주세요."
            }

            MeetingService.StartResult.ChannelNotFound -> {
                "회의 채널을 찾지 못했습니다."
            }

            MeetingService.StartResult.ThreadCreateFailed -> {
                "회의 스레드 생성에 실패했습니다."
            }
        }
    }

    companion object {
        private val supportedPrefixes = setOf("dash", "home")
    }
}
