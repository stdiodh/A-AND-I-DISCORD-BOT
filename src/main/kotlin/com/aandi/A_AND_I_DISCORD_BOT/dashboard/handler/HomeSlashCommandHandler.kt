package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.DiscordReplyFactory
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.service.HomeDashboardService
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class HomeSlashCommandHandler(
    private val homeDashboardService: HomeDashboardService,
    private val permissionGate: PermissionGate,
    private val discordReplyFactory: DiscordReplyFactory,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND_NAME_KO && event.name != COMMAND_NAME_EN) {
            return
        }
        if (isSubcommand(event, SUBCOMMAND_CREATE_KO, SUBCOMMAND_CREATE_EN)) {
            handleCreate(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_REFRESH_KO, SUBCOMMAND_REFRESH_EN)) {
            handleRefresh(event)
            return
        }
        discordReplyFactory.invalidInput(event, "지원하지 않는 하위 명령입니다.")
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

        val channel = event.getOption(OPTION_CHANNEL_KO)?.asChannel
        if (channel == null || channel.type != ChannelType.TEXT) {
            discordReplyFactory.invalidInput(event, "텍스트 채널을 지정해 주세요.")
            return
        }

        when (val result = homeDashboardService.create(guild.idLong, guild.name, channel.idLong)) {
            is HomeDashboardService.Result.Success -> {
                val pinNote = when (result.pinResult) {
                    HomeDashboardService.PinResult.PINNED -> "핀 고정도 완료했습니다."
                    HomeDashboardService.PinResult.NO_PERMISSION -> "메시지는 생성됐지만 핀 권한이 없어 고정하지 못했습니다."
                    HomeDashboardService.PinResult.FAILED -> "메시지는 생성됐지만 핀 고정에 실패했습니다."
                    HomeDashboardService.PinResult.SKIPPED -> "메시지를 생성했습니다."
                }
                event.reply("홈 메시지를 생성했습니다. <#${result.channelId}> / 메시지 ID: `${result.messageId}`\n$pinNote")
                    .setEphemeral(true)
                    .queue()
            }

            HomeDashboardService.Result.ChannelNotFound -> {
                discordReplyFactory.invalidInput(event, "대상 채널을 찾을 수 없습니다.")
            }

            HomeDashboardService.Result.MessageNotFound -> {
                discordReplyFactory.invalidInput(event, "홈 메시지 생성에 실패했습니다.")
            }

            HomeDashboardService.Result.NotConfigured -> {
                discordReplyFactory.invalidInput(event, "홈 메시지 생성에 실패했습니다.")
            }
        }
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

        when (homeDashboardService.refresh(guild.idLong, guild.name)) {
            is HomeDashboardService.Result.Success -> {
                event.reply("홈 메시지를 갱신했습니다.")
                    .setEphemeral(true)
                    .queue()
            }

            HomeDashboardService.Result.NotConfigured -> {
                discordReplyFactory.invalidInput(event, "먼저 `/홈 생성`을 실행해 홈 메시지를 만들어 주세요.")
            }

            HomeDashboardService.Result.ChannelNotFound -> {
                discordReplyFactory.invalidInput(event, "저장된 홈 채널을 찾을 수 없습니다. `/홈 생성`으로 다시 생성해 주세요.")
            }

            HomeDashboardService.Result.MessageNotFound -> {
                discordReplyFactory.invalidInput(event, "저장된 홈 메시지를 찾을 수 없습니다. `/홈 생성`으로 다시 생성해 주세요.")
            }
        }
    }

    private fun isSubcommand(event: SlashCommandInteractionEvent, ko: String, en: String): Boolean {
        return event.subcommandName == ko || event.subcommandName == en
    }

    companion object {
        private const val COMMAND_NAME_KO = "홈"
        private const val COMMAND_NAME_EN = "home"
        private const val SUBCOMMAND_CREATE_KO = "생성"
        private const val SUBCOMMAND_CREATE_EN = "create"
        private const val SUBCOMMAND_REFRESH_KO = "갱신"
        private const val SUBCOMMAND_REFRESH_EN = "refresh"
        private const val OPTION_CHANNEL_KO = "채널"
    }
}
