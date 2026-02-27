package com.aandi.A_AND_I_DISCORD_BOT.admin.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.auth.AdminPermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorCode
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorResponse
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component

@Component
class AdminSettingSlashCommandHandler(
    private val guildConfigService: GuildConfigService,
    private val adminPermissionChecker: AdminPermissionChecker,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND_NAME_KO && event.name != COMMAND_NAME_EN) {
            return
        }
        if (isSubcommand(event, SUBCOMMAND_SET_ROLE_KO, SUBCOMMAND_SET_ROLE_EN)) {
            handleSetAdminRole(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_CLEAR_ROLE_KO, SUBCOMMAND_CLEAR_ROLE_EN)) {
            handleClearAdminRole(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_VIEW_ROLE_KO, SUBCOMMAND_VIEW_ROLE_EN)) {
            handleGetAdminRole(event)
            return
        }
        replyInvalidInputError(event, "지원하지 않는 하위 명령입니다.")
    }

    private fun handleSetAdminRole(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (!adminPermissionChecker.canManageAdminRole(guild.idLong, member)) {
            replyAccessDeniedError(event)
            return
        }

        val role = event.getOption(OPTION_ROLE_KO)?.asRole ?: event.getOption(OPTION_ROLE_EN)?.asRole
        if (role == null) {
            replyInvalidInputError(event, "역할 옵션이 필요합니다.")
            return
        }

        guildConfigService.setAdminRole(guild.idLong, role.idLong)
        event.reply("운영진 역할을 <@&${role.idLong}> 로 설정했습니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleClearAdminRole(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        if (!adminPermissionChecker.canManageAdminRole(guild.idLong, member)) {
            replyAccessDeniedError(event)
            return
        }

        guildConfigService.clearAdminRole(guild.idLong)
        event.reply("운영진 역할 설정을 해제했습니다. 이후 `/설정 운영진역할 역할:@역할`로 다시 지정해 주세요.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleGetAdminRole(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.")
            return
        }

        val adminRoleId = guildConfigService.getAdminRole(guild.idLong)
        if (adminRoleId == null) {
            event.reply("운영진 역할이 아직 설정되지 않았습니다. `/설정 운영진역할 역할:@운영진` 으로 먼저 설정해 주세요.")
                .setEphemeral(true)
                .queue()
            return
        }

        val role = guild.getRoleById(adminRoleId)
        if (role == null) {
            event.reply("운영진 역할 ID는 `${adminRoleId}` 로 저장되어 있지만 현재 길드에서 역할을 찾을 수 없습니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        event.reply("현재 운영진 역할은 <@&${role.idLong}> 입니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun isSubcommand(
        event: SlashCommandInteractionEvent,
        ko: String,
        en: String,
    ): Boolean = event.subcommandName == ko || event.subcommandName == en

    private fun replyInvalidInputError(event: SlashCommandInteractionEvent, message: String) {
        replyError(
            event = event,
            code = DiscordErrorCode.COMMON_INVALID_INPUT,
            message = message,
        )
    }

    private fun replyAccessDeniedError(event: SlashCommandInteractionEvent) {
        replyError(
            event = event,
            code = DiscordErrorCode.ACCESS_DENIED,
            message = "운영진 역할 설정/해제 권한이 없습니다.",
        )
    }

    private fun replyError(
        event: SlashCommandInteractionEvent,
        code: DiscordErrorCode,
        message: String,
    ) {
        val payload = DiscordErrorFormatter.format(
            DiscordErrorResponse(
                code = code,
                message = message,
                retryable = false,
            ),
        )
        event.reply(payload)
            .setEphemeral(true)
            .queue()
    }

    companion object {
        private const val COMMAND_NAME_KO = "설정"
        private const val COMMAND_NAME_EN = "settings"
        private const val SUBCOMMAND_SET_ROLE_KO = "운영진역할"
        private const val SUBCOMMAND_SET_ROLE_EN = "adminrole"
        private const val SUBCOMMAND_CLEAR_ROLE_KO = "운영진해제"
        private const val SUBCOMMAND_CLEAR_ROLE_EN = "adminclear"
        private const val SUBCOMMAND_VIEW_ROLE_KO = "운영진조회"
        private const val SUBCOMMAND_VIEW_ROLE_EN = "adminshow"
        private const val OPTION_ROLE_KO = "역할"
        private const val OPTION_ROLE_EN = "role"
    }
}
