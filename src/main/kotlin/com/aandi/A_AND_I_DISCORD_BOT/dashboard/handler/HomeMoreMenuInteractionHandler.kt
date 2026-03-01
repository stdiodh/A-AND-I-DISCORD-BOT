package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.InteractionReliabilityGuard
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.service.HomeDashboardService
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionPrefixHandler
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class HomeMoreMenuInteractionHandler(
    private val assignmentTaskService: AssignmentTaskService,
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
                                interactionReliabilityGuard.safeEditReply(
                                    ctx = ctx,
                                    message = "안건 설정을 진행하려면 아래 버튼을 누르세요.",
                                    components = listOf(ActionRow.of(Button.secondary(DashboardActionIds.AGENDA_SET, "안건 설정"))),
                                )
                            }

                            HomeDashboardService.HOME_MORE_TASK_LIST -> {
                                val message = buildTaskListMessage(guild.idLong)
                                interactionReliabilityGuard.safeEditReply(ctx, message)
                            }

                            HomeDashboardService.HOME_MORE_MOGAKCO_RANK -> {
                                interactionReliabilityGuard.safeEditReply(
                                    ctx = ctx,
                                    message = "조회할 기간을 선택하세요.",
                                    components = listOf(ActionRow.of(periodSelectMenu(DashboardActionIds.MOGAKCO_RANK_SELECT))),
                                )
                            }

                            HomeDashboardService.HOME_MORE_MOGAKCO_ME -> {
                                interactionReliabilityGuard.safeEditReply(
                                    ctx = ctx,
                                    message = "내 기록은 본인에게만 표시됩니다.\n조회할 기간을 선택하세요.",
                                    components = listOf(ActionRow.of(periodSelectMenu(DashboardActionIds.MOGAKCO_ME_SELECT))),
                                )
                            }

                            HomeDashboardService.HOME_MORE_SETTINGS_HELP -> {
                                interactionReliabilityGuard.safeEditReply(
                                    ctx = ctx,
                                    message = "설정/도움말\n" +
                                        "• 운영진 역할 설정: `/설정 운영진역할`\n" +
                                        "• 홈 재설치/핀 점검: `/홈 설치`\n" +
                                        "• 회의 안건 등록: `/회의 안건등록 링크:<URL>`",
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

    private fun periodSelectMenu(customId: String): StringSelectMenu {
        return StringSelectMenu.create(customId)
            .setPlaceholder("기간을 선택하세요")
            .addOption("주간", "week")
            .addOption("월간", "month")
            .build()
    }
}
