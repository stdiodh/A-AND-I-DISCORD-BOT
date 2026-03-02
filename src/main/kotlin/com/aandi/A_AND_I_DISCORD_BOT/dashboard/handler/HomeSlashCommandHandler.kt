package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.DiscordReplyFactory
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.InteractionReliabilityGuard
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.service.HomeDashboardService
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class HomeSlashCommandHandler(
    private val homeDashboardService: HomeDashboardService,
    private val permissionGate: PermissionGate,
    private val discordReplyFactory: DiscordReplyFactory,
    private val interactionReliabilityGuard: InteractionReliabilityGuard,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND_NAME_KO && event.name != COMMAND_NAME_EN) {
            return
        }
        when {
            isSubcommand(event, SUBCOMMAND_CREATE_KO, SUBCOMMAND_CREATE_EN) -> handleCreate(event)
            isSubcommand(event, SUBCOMMAND_REFRESH_KO, SUBCOMMAND_REFRESH_EN) -> handleRefresh(event)
            isSubcommand(event, SUBCOMMAND_INSTALL_KO, SUBCOMMAND_INSTALL_EN) -> handleInstall(event)
            else -> discordReplyFactory.invalidInput(event, "지원하지 않는 하위 명령입니다.")
        }
    }

    private fun handleCreate(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (!permissionGate.canAdminAction(guild.idLong, member)) {
            discordReplyFactory.accessDenied(event, "홈 생성 권한이 없습니다.")
            return
        }

        val channel = resolveChannelOption(event)
        if (channel == null || channel.type != ChannelType.TEXT) {
            discordReplyFactory.invalidInput(event, "텍스트 채널을 지정해 주세요.")
            return
        }

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        homeDashboardService.create(guild.idLong, guild.name, channel.idLong)
                    }.fold(
                        onSuccess = { result -> replyCreateResult(ctx, result) },
                        onFailure = {
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/홈 생성` 또는 `/홈 설치`를 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/홈 생성` 또는 `/홈 설치`를 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun handleRefresh(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (!permissionGate.canAdminAction(guild.idLong, member)) {
            discordReplyFactory.accessDenied(event, "홈 갱신 권한이 없습니다.")
            return
        }

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        homeDashboardService.refresh(guild.idLong, guild.name)
                    }.fold(
                        onSuccess = { result -> replyRefreshResult(ctx, result) },
                        onFailure = {
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/홈 갱신` 또는 `/홈 설치`를 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/홈 갱신` 또는 `/홈 설치`를 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun handleInstall(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (!canInstall(member, guild.idLong)) {
            discordReplyFactory.accessDenied(event, "홈 설치 권한이 없습니다. `서버 관리(Manage Guild)` 권한이 필요합니다.")
            return
        }

        val optionChannel = resolveChannelOption(event)
        if (optionChannel != null && optionChannel.type != ChannelType.TEXT) {
            discordReplyFactory.invalidInput(event, "텍스트 채널만 선택할 수 있습니다.")
            return
        }
        val preferredChannelId = when {
            optionChannel != null -> optionChannel.idLong
            event.channel.type == ChannelType.TEXT -> event.channel.idLong
            else -> null
        }

        interactionReliabilityGuard.safeDefer(
            interaction = event,
            preferUpdate = false,
            onDeferred = { ctx ->
                CompletableFuture.runAsync {
                    runCatching {
                        homeDashboardService.install(guild.idLong, guild.name, preferredChannelId)
                    }.fold(
                        onSuccess = { result -> replyInstallResult(ctx, result) },
                        onFailure = {
                            interactionReliabilityGuard.safeFailureReply(
                                ctx = ctx,
                                alternativeCommandGuide = "`/홈 설치`를 다시 시도해 주세요.",
                            )
                        },
                    )
                }
            },
            onFailure = { ctx, _ ->
                interactionReliabilityGuard.safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/홈 설치`를 다시 시도해 주세요.",
                )
            },
        )
    }

    private fun canInstall(member: net.dv8tion.jda.api.entities.Member, guildId: Long): Boolean {
        if (member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MANAGE_SERVER)) {
            return true
        }
        return permissionGate.canAdminAction(guildId, member)
    }

    private fun resolveChannelOption(event: SlashCommandInteractionEvent) =
        event.getOption(OPTION_CHANNEL_KO)?.asChannel ?: event.getOption(OPTION_CHANNEL_EN)?.asChannel

    private fun replyCreateResult(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        result: HomeDashboardService.Result,
    ) {
        val message = when (result) {
            is HomeDashboardService.Result.Success -> {
                val actionMessage = resolveCreateActionMessage(result)
                "$actionMessage <#${result.channelId}> / 메시지 ID: `${result.messageId}`\n${buildPinNote(result.pinResult)}"
            }

            HomeDashboardService.Result.ChannelNotFound -> "대상 채널을 찾을 수 없습니다."
            HomeDashboardService.Result.MessageNotFound -> "홈 메시지 생성에 실패했습니다."
            HomeDashboardService.Result.NotConfigured -> "홈 메시지 생성에 실패했습니다."
        }
        interactionReliabilityGuard.safeEditReply(ctx, message)
    }

    private fun replyRefreshResult(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        result: HomeDashboardService.Result,
    ) {
        val message = when (result) {
            is HomeDashboardService.Result.Success ->
                "홈 메시지를 갱신했습니다.\n${buildPinNote(result.pinResult)}"

            HomeDashboardService.Result.NotConfigured ->
                "먼저 `/홈 설치`를 실행해 홈 메시지를 만들어 주세요."

            HomeDashboardService.Result.ChannelNotFound ->
                "저장된 홈 채널을 찾을 수 없습니다. `/홈 설치`로 다시 생성해 주세요."

            HomeDashboardService.Result.MessageNotFound ->
                "저장된 홈 메시지를 찾을 수 없습니다. `/홈 설치`로 다시 생성해 주세요."
        }
        interactionReliabilityGuard.safeEditReply(ctx, message)
    }

    private fun replyInstallResult(
        ctx: InteractionReliabilityGuard.InteractionCtx,
        result: HomeDashboardService.Result,
    ) {
        val message = when (result) {
            is HomeDashboardService.Result.Success -> buildString {
                appendLine("${resolveInstallActionMessage(result)} <#${result.channelId}> / 메시지 ID: `${result.messageId}`")
                append(result.pinStatusLine)
            }

            HomeDashboardService.Result.NotConfigured ->
                "설치할 채널을 찾지 못했습니다. `/홈 설치 채널:#채널명`으로 실행해 주세요."

            HomeDashboardService.Result.ChannelNotFound ->
                "대상 홈 채널을 찾지 못했습니다. 채널을 다시 지정해 주세요."

            HomeDashboardService.Result.MessageNotFound ->
                "홈 메시지 복구/생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
        }
        val components = resolveInstallComponents(result)
        interactionReliabilityGuard.safeEditReply(
            ctx = ctx,
            message = message,
            components = components,
        )
    }

    private fun resolveInstallComponents(result: HomeDashboardService.Result): List<ActionRow> {
        if (result !is HomeDashboardService.Result.Success) {
            return emptyList()
        }
        return listOf(ActionRow.of(Button.secondary(DashboardActionIds.HOME_PIN_RECHECK, "고정 상태 재확인")))
    }

    private fun resolveCreateActionMessage(result: HomeDashboardService.Result.Success): String {
        if (result.createdNew) {
            return "홈 메시지를 생성했습니다."
        }
        return "기존 홈 메시지를 갱신했습니다."
    }

    private fun resolveInstallActionMessage(result: HomeDashboardService.Result.Success): String {
        if (result.createdNew) {
            return "홈 메시지를 생성했습니다."
        }
        return "기존 홈 메시지를 재사용/복구했습니다."
    }

    private fun isSubcommand(event: SlashCommandInteractionEvent, ko: String, en: String): Boolean {
        return event.subcommandName == ko || event.subcommandName == en
    }

    private fun buildPinNote(pinResult: HomeDashboardService.PinResult): String {
        return when (pinResult) {
            HomeDashboardService.PinResult.PINNED -> "홈 고정 상태: ✅ 고정됨"
            HomeDashboardService.PinResult.ALREADY_PINNED -> "홈 고정 상태: ✅ 이미 고정됨"
            HomeDashboardService.PinResult.NO_PERMISSION -> "홈 고정 상태: ❌ 권한 부족 (메시지 관리 권한 필요)"
            HomeDashboardService.PinResult.PIN_LIMIT_REACHED -> "홈 고정 상태: ❌ 핀 한도 초과 (해당 채널 핀 정리 필요)"
            HomeDashboardService.PinResult.FAILED -> "홈 고정 상태: ❌ 고정 실패 (채널 상태/권한 확인 필요)"
        }
    }

    companion object {
        private const val COMMAND_NAME_KO = "홈"
        private const val COMMAND_NAME_EN = "home"
        private const val SUBCOMMAND_CREATE_KO = "생성"
        private const val SUBCOMMAND_CREATE_EN = "create"
        private const val SUBCOMMAND_REFRESH_KO = "갱신"
        private const val SUBCOMMAND_REFRESH_EN = "refresh"
        private const val SUBCOMMAND_INSTALL_KO = "설치"
        private const val SUBCOMMAND_INSTALL_EN = "install"
        private const val OPTION_CHANNEL_KO = "채널"
        private const val OPTION_CHANNEL_EN = "channel"
    }
}
