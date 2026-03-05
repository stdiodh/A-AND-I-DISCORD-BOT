package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.InteractionReliabilityGuard
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.service.HomeDashboardService
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionPrefixHandler
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
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
                                        message = "회의 공지 채널이 설정되지 않았습니다. `/설정 회의채널 채널:#회의`를 먼저 설정해 주세요.",
                                    )
                                } else {
                                    interactionReliabilityGuard.safeEditReply(
                                        ctx = ctx,
                                        message = "안건 설정은 회의 채널에서 진행해 주세요.\n채널: <#${meetingChannelId}>\n명령어: `/안건 생성 링크:<URL> 제목:<선택>`",
                                        components = listOf(ActionRow.of(Button.link(channelJumpUrl(guild.idLong, meetingChannelId), "회의 채널 이동"))),
                                    )
                                }
                            }

                            HomeDashboardService.HOME_MORE_MOGAKCO_ME -> {
                                routeToMogakcoChannel(ctx, guild.idLong, "내 기록은 모각코 채널에서 실행해 주세요.\n명령어: `/모각코 오늘` 또는 `/모각코 내정보 기간:day`")
                            }

                            HomeDashboardService.HOME_MORE_SETTINGS_HELP -> {
                                interactionReliabilityGuard.safeEditReply(
                                    ctx = ctx,
                                    message = "설정/도움말\n" +
                                        "• 운영진 역할 설정: `/설정 운영진역할`\n" +
                                        "• 회의 공지 채널: `/설정 회의채널`\n" +
                                        "• 모각코 공지 채널: `/설정 모각코채널`\n" +
                                        "• 과제 공지 채널: `/설정 과제공지채널`\n" +
                                        "• 홈 재설치/핀 점검: `/홈 설치`\n" +
                                        "• 회의 안건 등록: `/안건 생성 링크:<URL>`",
                                )
                            }

                            LEGACY_HOME_MORE_TASK_LIST,
                            LEGACY_HOME_MORE_MOGAKCO_RANK,
                            -> {
                                interactionReliabilityGuard.safeEditReply(
                                    ctx = ctx,
                                    message = "홈 레이아웃이 업데이트되었습니다. 1행의 채널 이동 버튼(회의/과제/모각코)을 이용해 주세요.",
                                )
                            }

                            else -> {
                                interactionReliabilityGuard.safeEditReply(ctx, "지원하지 않는 메뉴입니다.")
                            }
                        }
                    }.onFailure {
                        interactionReliabilityGuard.safeFailureReply(
                            ctx = ctx,
                            alternativeCommandGuide = "`/홈 설치` 또는 `/홈 갱신`을 사용해 주세요.",
                        )
                    }
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/홈 설치` 또는 `/홈 갱신`을 사용해 주세요.",
                )
            },
        )
        return true
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
                "모각코 공지 채널이 설정되지 않았습니다. `/설정 모각코채널 채널:#모각코`를 먼저 설정해 주세요.",
            )
            return
        }
        interactionReliabilityGuard.safeEditReply(
            ctx = ctx,
            message = "$baseMessage\n채널: <#${mogakcoChannelId}>",
            components = listOf(ActionRow.of(Button.link(channelJumpUrl(guildId, mogakcoChannelId), "모각코 채널 이동"))),
        )
    }

    private fun channelJumpUrl(guildId: Long, channelId: Long): String {
        return "https://discord.com/channels/$guildId/$channelId"
    }

    companion object {
        private const val LEGACY_HOME_MORE_TASK_LIST = "assignment_list"
        private const val LEGACY_HOME_MORE_MOGAKCO_RANK = "mogakco_rank"
    }

}
