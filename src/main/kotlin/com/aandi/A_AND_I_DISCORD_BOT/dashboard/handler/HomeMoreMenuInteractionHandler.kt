package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.InteractionReliabilityGuard
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
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
    private val assignmentTaskService: AssignmentTaskService,
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

                            HomeDashboardService.HOME_MORE_TASK_LIST -> {
                                val boards = guildConfigService.getBoardChannels(guild.idLong)
                                val assignmentChannelId = boards.assignmentChannelId
                                if (assignmentChannelId == null) {
                                    val message = buildTaskListMessage(guild.idLong)
                                    interactionReliabilityGuard.safeEditReply(ctx, "$message\n\n과제 공지 채널을 쓰려면 `/설정 과제공지채널`을 설정해 주세요.")
                                } else {
                                    interactionReliabilityGuard.safeEditReply(
                                        ctx = ctx,
                                        message = "과제 목록은 과제 채널에서 확인해 주세요.\n채널: <#${assignmentChannelId}>\n명령어: `/과제 목록`",
                                        components = listOf(ActionRow.of(Button.link(channelJumpUrl(guild.idLong, assignmentChannelId), "과제 채널 이동"))),
                                    )
                                }
                            }

                            HomeDashboardService.HOME_MORE_MOGAKCO_RANK -> {
                                routeToMogakcoChannel(ctx, guild.idLong, "모각코 랭킹은 모각코 채널에서 확인해 주세요.\n명령어: `/모각코 랭킹 기간:day`")
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

    private fun buildTaskListMessage(guildId: Long): String {
        val result = assignmentTaskService.list(guildId, null)
        if (result is AssignmentTaskService.ListResult.InvalidStatus) {
            return "과제 상태값이 올바르지 않습니다."
        }
        val tasks = (result as AssignmentTaskService.ListResult.Success).tasks
        if (tasks.isEmpty()) {
            return "등록된 과제가 없습니다."
        }
        val lines = tasks.take(10).map {
            val role = it.notifyRoleId?.let { roleId -> "<@&$roleId>" } ?: "없음"
            "• [${it.id}] ${it.title} | 알림:${KstTime.formatInstantToKst(it.remindAt)} | 마감:${KstTime.formatInstantToKst(it.dueAt)} | 역할:$role"
        }
        return "과제 목록(최대 10건)\n${lines.joinToString("\n")}"
    }

}
