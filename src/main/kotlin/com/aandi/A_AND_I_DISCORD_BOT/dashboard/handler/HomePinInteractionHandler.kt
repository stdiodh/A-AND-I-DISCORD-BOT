package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.InteractionReliabilityGuard
import com.aandi.A_AND_I_DISCORD_BOT.common.log.StructuredLog
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.service.HomeDashboardService
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionPrefixHandler
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class HomePinInteractionHandler(
    private val homeDashboardService: HomeDashboardService,
    private val permissionGate: PermissionGate,
    private val interactionReliabilityGuard: InteractionReliabilityGuard,
) : InteractionPrefixHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(prefix: String): Boolean = prefix == "home"

    override fun onButton(event: ButtonInteractionEvent): Boolean {
        if (event.componentId != DashboardActionIds.HOME_PIN_RECHECK) {
            return false
        }
        recheck(event)
        return true
    }

    private fun recheck(event: ButtonInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!canInstall(member, guild.idLong)) {
            event.reply("홈 설치/재확인 권한이 없습니다. `서버 관리(Manage Guild)` 권한이 필요합니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        log.info(
            StructuredLog.event(
                name = "home.pin.recheck.start",
                "guildId" to guild.idLong,
                "memberId" to member.idLong,
            ),
        )

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        homeDashboardService.install(guild.idLong, guild.name, null)
                    }.fold(
                        onSuccess = { result ->
                            replyRecheckResult(ctx, result)
                        },
                        onFailure = { exception ->
                            log.error(
                                StructuredLog.event(
                                    name = "home.pin.recheck.failed",
                                    "guildId" to guild.idLong,
                                    "memberId" to member.idLong,
                                ),
                                exception,
                            )
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/홈 설치` 명령으로 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                log.warn(
                    StructuredLog.event(
                        name = "home.pin.recheck.defer_failed",
                        "guildId" to guild.idLong,
                        "memberId" to member.idLong,
                    ),
                )
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/홈 설치` 명령으로 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun replyRecheckResult(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        result: HomeDashboardService.Result,
    ) {
        val message = when (result) {
            is HomeDashboardService.Result.Success -> buildString {
                appendLine("고정 상태를 다시 점검했습니다.")
                appendLine("대상: <#${result.channelId}> / 메시지 ID: `${result.messageId}`")
                append(result.pinStatusLine)
            }

            HomeDashboardService.Result.NotConfigured -> {
                "설치할 홈 채널 정보가 없습니다. `/홈 설치 채널:#채널명`으로 다시 설정해 주세요."
            }

            HomeDashboardService.Result.ChannelNotFound -> {
                "저장된 홈 채널을 찾지 못했습니다. `/홈 설치`를 다시 실행해 주세요."
            }

            HomeDashboardService.Result.MessageNotFound -> {
                "홈 메시지를 찾지 못했습니다. `/홈 설치`를 다시 실행해 주세요."
            }
        }
        log.info(
            StructuredLog.event(
                name = "home.pin.recheck.done",
                "guildId" to ctx.guildId,
                "resultType" to result::class.simpleName,
            ),
        )
        interactionReliabilityGuard.safeEditReply(
            ctx = ctx,
            message = message,
            components = listOf(ActionRow.of(Button.secondary(DashboardActionIds.HOME_PIN_RECHECK, "고정 상태 재확인"))),
        )
    }

    private fun canInstall(member: Member, guildId: Long): Boolean {
        if (member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MANAGE_SERVER)) {
            return true
        }
        return permissionGate.canAdminAction(guildId, member)
    }
}
