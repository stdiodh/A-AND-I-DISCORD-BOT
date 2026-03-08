package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.InteractionReliabilityGuard
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.service.HomeDashboardService
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionPrefixHandler
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class HomeMoreMenuInteractionHandler(
    private val guildConfigService: GuildConfigService,
    private val interactionReliabilityGuard: InteractionReliabilityGuard,
) : InteractionPrefixHandler {

    override fun supports(prefix: String): Boolean = prefix == "home"

    override fun onButton(event: ButtonInteractionEvent): Boolean {
        if (event.componentId == DashboardActionIds.HOME_QUICK_HELP) {
            event.reply(
                "주요 명령\n" +
                    "• `/홈 설치`\n" +
                    "• `/회의 시작|종료|기록|항목`\n" +
                    "• `/안건 설정|오늘|최근`\n" +
                    "• `/과제 목록|상세|등록|완료`\n" +
                    "• `/모각코 오늘|내정보|랭킹`\n" +
                    "• `/설정 마법사|상태`",
            )
                .setEphemeral(true)
                .queue()
            return true
        }
        val setupFocus = parseSetupFocus(event.componentId) ?: return false
        val guild = event.guild ?: run {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return true
        }

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        replySetupGuide(ctx, guild.idLong, setupFocus)
                    }.onFailure {
                        interactionReliabilityGuard.safeFailureReply(
                            ctx = ctx,
                            alternativeCommandGuide = "`/설정 마법사` 또는 `/설정 상태`를 사용해 주세요.",
                        )
                    }
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/설정 마법사` 또는 `/설정 상태`를 사용해 주세요.",
                )
            },
        )
        return true
    }

    override fun onStringSelect(event: StringSelectInteractionEvent): Boolean {
        if (event.componentId != DashboardActionIds.HOME_MORE_SELECT) {
            return false
        }
        val guild = event.guild ?: run {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return true
        }
        val selected = event.values.firstOrNull() ?: run {
            event.reply("선택값이 비어 있습니다.").setEphemeral(true).queue()
            return true
        }

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        when (selected) {
                            HomeDashboardService.HOME_MORE_AGENDA -> {
                                val boards = guildConfigService.getBoardChannels(guild.idLong)
                                val meetingChannelId = boards.meetingChannelId
                                if (meetingChannelId == null) {
                                    interactionReliabilityGuard.safeEditReply(
                                        ctx = ctx,
                                        message = "회의 공지 채널이 설정되지 않았습니다. `/설정 마법사 회의채널:#회의`를 먼저 설정해 주세요.",
                                    )
                                } else {
                                    interactionReliabilityGuard.safeEditReply(
                                        ctx = ctx,
                                        message = "안건 설정은 회의 채널에서 진행해 주세요.\n채널: <#${meetingChannelId}>\n명령어: `/안건 설정 링크:<URL> 제목:<선택>`",
                                        components = listOf(ActionRow.of(Button.link(channelJumpUrl(guild.idLong, meetingChannelId), "회의 채널 이동"))),
                                    )
                                }
                            }

                            HomeDashboardService.HOME_MORE_ASSIGNMENT_LIST,
                            -> {
                                routeToAssignmentChannel(
                                    ctx = ctx,
                                    guildId = guild.idLong,
                                    baseMessage = "과제 목록은 과제 채널에서 실행해 주세요.\n명령어: `/과제 목록`",
                                )
                            }

                            HomeDashboardService.HOME_MORE_MOGAKCO,
                            HomeDashboardService.HOME_MORE_MOGAKCO_ME,
                            LEGACY_HOME_MORE_MOGAKCO_RANK,
                            -> {
                                routeToMogakcoChannel(
                                    ctx = ctx,
                                    guildId = guild.idLong,
                                    baseMessage = "모각코는 모각코 채널에서 실행해 주세요.\n명령어: `/모각코 오늘` `/모각코 내정보` `/모각코 랭킹`",
                                )
                            }

                            HomeDashboardService.HOME_MORE_SETTINGS -> {
                                interactionReliabilityGuard.safeEditReply(
                                    ctx = ctx,
                                    message = "설정/도움말\n" +
                                        "• 빠른 설정: `/설정 마법사`\n" +
                                        "• 현재 설정 상태 확인: `/설정 상태`\n" +
                                        "• 홈 재설치/핀 점검: `/홈 설치`\n" +
                                        "• 안건 등록: `/안건 설정 링크:<URL>`",
                                )
                            }

                            HomeDashboardService.HOME_MORE_SETTINGS_HELP -> {
                                interactionReliabilityGuard.safeEditReply(
                                    ctx = ctx,
                                    message = "설정/도움말\n" +
                                        "• 빠른 설정: `/설정 마법사`\n" +
                                        "• 현재 설정 상태 확인: `/설정 상태`\n" +
                                        "• 홈 재설치/핀 점검: `/홈 설치`",
                                )
                            }

                            HomeDashboardService.HOME_MORE_HELP -> {
                                interactionReliabilityGuard.safeEditReply(
                                    ctx = ctx,
                                    message = "주요 명령\n" +
                                        "• `/홈 설치`\n" +
                                        "• `/회의 시작|종료|기록|항목`\n" +
                                        "• `/안건 오늘|최근|설정`\n" +
                                        "• `/과제 목록|상세|등록|완료`\n" +
                                        "• `/모각코 오늘|내정보|랭킹`\n" +
                                        "• `/설정 마법사|상태`",
                                )
                            }

                            else -> {
                                interactionReliabilityGuard.safeEditReply(ctx, "지원하지 않는 메뉴입니다.")
                            }
                        }
                    }.onFailure {
                        interactionReliabilityGuard.safeFailureReply(
                            ctx = ctx,
                            alternativeCommandGuide = "`/홈 설치`를 사용해 주세요.",
                        )
                    }
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/홈 설치`를 사용해 주세요.",
                )
            },
        )
        return true
    }

    private fun parseSetupFocus(componentId: String): SetupFocus? {
        return when (componentId) {
            DashboardActionIds.HOME_SETUP_START,
            DashboardActionIds.HOME_MEETING_MOVE_UNSET,
            DashboardActionIds.HOME_ASSIGNMENT_MOVE_UNSET,
            DashboardActionIds.HOME_MOGAKCO_MOVE_UNSET,
            -> SetupFocus.ALL

            DashboardActionIds.HOME_SETUP_START_MEETING -> SetupFocus.MEETING
            DashboardActionIds.HOME_SETUP_START_ASSIGNMENT -> SetupFocus.ASSIGNMENT
            DashboardActionIds.HOME_SETUP_START_MOGAKCO -> SetupFocus.MOGAKCO
            else -> null
        }
    }

    private fun replySetupGuide(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        guildId: Long,
        focus: SetupFocus,
    ) {
        val boards = guildConfigService.getBoardChannels(guildId)
        when (focus) {
            SetupFocus.ALL -> interactionReliabilityGuard.safeEditReply(
                ctx = ctx,
                message = buildString {
                    appendLine("설정 마법사를 시작해 주세요.")
                    appendLine("예시: `/설정 마법사 회의채널:#회의 모각코채널:#모각코 과제공지채널:#과제`")
                    appendLine("과제 기본 알림 역할도 함께 설정하려면 `과제알림역할:@역할`을 추가하세요.")
                    append("현재 상태는 `/설정 상태`로 확인할 수 있습니다.")
                },
            )

            SetupFocus.MEETING -> {
                val meetingChannelId = boards.meetingChannelId
                if (meetingChannelId == null) {
                    interactionReliabilityGuard.safeEditReply(
                        ctx = ctx,
                        message = "회의 채널이 아직 설정되지 않았습니다.\n" +
                            "`/설정 마법사 회의채널:#회의`로 먼저 설정해 주세요.",
                    )
                } else {
                    interactionReliabilityGuard.safeEditReply(
                        ctx = ctx,
                        message = "회의 채널은 이미 <#${meetingChannelId}> 로 설정되어 있습니다.\n" +
                            "다른 항목은 `/설정 마법사`, 전체 상태는 `/설정 상태`에서 확인해 주세요.",
                    )
                }
            }

            SetupFocus.ASSIGNMENT -> {
                val assignmentChannelId = boards.assignmentChannelId
                if (assignmentChannelId == null) {
                    interactionReliabilityGuard.safeEditReply(
                        ctx = ctx,
                        message = "과제 공지 채널이 아직 설정되지 않았습니다.\n" +
                            "`/설정 마법사 과제공지채널:#과제`로 먼저 설정해 주세요.",
                    )
                } else {
                    interactionReliabilityGuard.safeEditReply(
                        ctx = ctx,
                        message = "과제 공지 채널은 이미 <#${assignmentChannelId}> 로 설정되어 있습니다.\n" +
                            "과제 기본 알림 역할은 `/설정 마법사 과제알림역할:@역할`로 설정할 수 있습니다.",
                    )
                }
            }

            SetupFocus.MOGAKCO -> {
                val mogakcoChannelId = boards.mogakcoChannelId
                if (mogakcoChannelId == null) {
                    interactionReliabilityGuard.safeEditReply(
                        ctx = ctx,
                        message = "모각코 채널이 아직 설정되지 않았습니다.\n" +
                            "`/설정 마법사 모각코채널:#모각코`로 먼저 설정해 주세요.",
                    )
                } else {
                    interactionReliabilityGuard.safeEditReply(
                        ctx = ctx,
                        message = "모각코 채널은 이미 <#${mogakcoChannelId}> 로 설정되어 있습니다.\n" +
                            "다른 설정은 `/설정 마법사`, 전체 상태는 `/설정 상태`에서 확인해 주세요.",
                    )
                }
            }
        }
    }

    private fun routeToMogakcoChannel(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        guildId: Long,
        baseMessage: String,
    ) {
        val boards = guildConfigService.getBoardChannels(guildId)
        val mogakcoChannelId = boards.mogakcoChannelId
        if (mogakcoChannelId == null) {
            interactionReliabilityGuard.safeEditReply(
                ctx,
                "모각코 공지 채널이 설정되지 않았습니다. `/설정 마법사 모각코채널:#모각코`를 먼저 설정해 주세요.",
            )
            return
        }
        interactionReliabilityGuard.safeEditReply(
            ctx = ctx,
            message = "$baseMessage\n채널: <#${mogakcoChannelId}>",
            components = listOf(ActionRow.of(Button.link(channelJumpUrl(guildId, mogakcoChannelId), "모각코 채널 이동"))),
        )
    }

    private fun routeToAssignmentChannel(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        guildId: Long,
        baseMessage: String,
    ) {
        val boards = guildConfigService.getBoardChannels(guildId)
        val assignmentChannelId = boards.assignmentChannelId
        if (assignmentChannelId == null) {
            interactionReliabilityGuard.safeEditReply(
                ctx,
                "과제 공지 채널이 설정되지 않았습니다. `/설정 마법사 과제공지채널:#과제`를 먼저 설정해 주세요.",
            )
            return
        }
        interactionReliabilityGuard.safeEditReply(
            ctx = ctx,
            message = "$baseMessage\n채널: <#${assignmentChannelId}>",
            components = listOf(ActionRow.of(Button.link(channelJumpUrl(guildId, assignmentChannelId), "과제 채널 이동"))),
        )
    }

    private fun channelJumpUrl(guildId: Long, channelId: Long): String {
        return "https://discord.com/channels/$guildId/$channelId"
    }

    companion object {
        private const val LEGACY_HOME_MORE_MOGAKCO_RANK = "mogakco_rank"
    }

    private enum class SetupFocus {
        ALL,
        MEETING,
        ASSIGNMENT,
        MOGAKCO,
    }

}
