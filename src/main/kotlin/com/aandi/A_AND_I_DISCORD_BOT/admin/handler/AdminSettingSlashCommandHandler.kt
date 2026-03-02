package com.aandi.A_AND_I_DISCORD_BOT.admin.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.auth.AdminPermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorCode
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorResponse
import net.dv8tion.jda.api.entities.channel.ChannelType
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
        if (isSubcommand(event, SUBCOMMAND_SET_MEETING_OPENER_ROLE_KO, SUBCOMMAND_SET_MEETING_OPENER_ROLE_EN)) {
            handleSetMeetingOpenerRole(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_CLEAR_MEETING_OPENER_ROLE_KO, SUBCOMMAND_CLEAR_MEETING_OPENER_ROLE_EN)) {
            handleClearMeetingOpenerRole(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_VIEW_MEETING_OPENER_ROLE_KO, SUBCOMMAND_VIEW_MEETING_OPENER_ROLE_EN)) {
            handleGetMeetingOpenerRole(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_SET_MEETING_BOARD_CHANNEL_KO, SUBCOMMAND_SET_MEETING_BOARD_CHANNEL_EN)) {
            handleSetMeetingBoardChannel(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_CLEAR_MEETING_BOARD_CHANNEL_KO, SUBCOMMAND_CLEAR_MEETING_BOARD_CHANNEL_EN)) {
            handleClearMeetingBoardChannel(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_SET_MOGAKCO_BOARD_CHANNEL_KO, SUBCOMMAND_SET_MOGAKCO_BOARD_CHANNEL_EN)) {
            handleSetMogakcoBoardChannel(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_CLEAR_MOGAKCO_BOARD_CHANNEL_KO, SUBCOMMAND_CLEAR_MOGAKCO_BOARD_CHANNEL_EN)) {
            handleClearMogakcoBoardChannel(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_SET_ASSIGNMENT_BOARD_CHANNEL_KO, SUBCOMMAND_SET_ASSIGNMENT_BOARD_CHANNEL_EN)) {
            handleSetAssignmentBoardChannel(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_CLEAR_ASSIGNMENT_BOARD_CHANNEL_KO, SUBCOMMAND_CLEAR_ASSIGNMENT_BOARD_CHANNEL_EN)) {
            handleClearAssignmentBoardChannel(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_VIEW_BOARD_CHANNELS_KO, SUBCOMMAND_VIEW_BOARD_CHANNELS_EN)) {
            handleViewBoardChannels(event)
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

        val role = event.getOption(OPTION_ROLE_KO)?.asRole
            ?: event.getOption(OPTION_ROLE_LEGACY_TARGET_KO)?.asRole
            ?: event.getOption(OPTION_ROLE_EN)?.asRole
        if (role == null) {
            replyInvalidInputError(event, "`역할` 옵션이 필요합니다.")
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
        event.reply("운영진 역할 설정을 해제했습니다. 필요하면 `/설정 운영진역할`에서 `역할`을 선택해 다시 지정해 주세요.")
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
            event.reply("운영진 역할이 아직 설정되지 않았습니다. `/설정 운영진역할`에서 `역할`을 선택해 먼저 설정해 주세요.")
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

    private fun handleSetMeetingOpenerRole(event: SlashCommandInteractionEvent) {
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

        val role = event.getOption(OPTION_ROLE_KO)?.asRole
            ?: event.getOption(OPTION_ROLE_LEGACY_TARGET_KO)?.asRole
            ?: event.getOption(OPTION_ROLE_EN)?.asRole
        if (role == null) {
            replyInvalidInputError(event, "`역할` 옵션이 필요합니다.")
            return
        }

        guildConfigService.setMeetingOpenerRole(guild.idLong, role.idLong)
        event.reply("회의 시작 권한 역할을 <@&${role.idLong}> 로 설정했습니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleClearMeetingOpenerRole(event: SlashCommandInteractionEvent) {
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

        guildConfigService.clearMeetingOpenerRole(guild.idLong)
        event.reply("회의 시작 권한 역할 설정을 해제했습니다. 필요하면 `/설정 회의열기역할`에서 다시 지정해 주세요.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleGetMeetingOpenerRole(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.")
            return
        }

        val roleId = guildConfigService.getMeetingOpenerRole(guild.idLong)
        if (roleId == null) {
            event.reply("회의 시작 권한 역할이 아직 설정되지 않았습니다. `/설정 회의열기역할`에서 `역할`을 선택해 설정해 주세요.")
                .setEphemeral(true)
                .queue()
            return
        }

        val role = guild.getRoleById(roleId)
        if (role == null) {
            event.reply("회의 시작 권한 역할 ID는 `${roleId}` 로 저장되어 있지만 현재 길드에서 역할을 찾을 수 없습니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        event.reply("현재 회의 시작 권한 역할은 <@&${role.idLong}> 입니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleSetMeetingBoardChannel(event: SlashCommandInteractionEvent) {
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

        val channelId = resolveTextChannelId(event) ?: run {
            replyInvalidInputError(event, "`채널` 옵션으로 텍스트 채널을 선택해 주세요.")
            return
        }
        guildConfigService.setMeetingBoardChannel(guild.idLong, channelId)
        event.reply("회의 공지 채널을 <#${channelId}> 로 설정했습니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleClearMeetingBoardChannel(event: SlashCommandInteractionEvent) {
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

        guildConfigService.clearMeetingBoardChannel(guild.idLong)
        event.reply("회의 공지 채널 설정을 해제했습니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleSetMogakcoBoardChannel(event: SlashCommandInteractionEvent) {
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

        val channelId = resolveTextChannelId(event) ?: run {
            replyInvalidInputError(event, "`채널` 옵션으로 텍스트 채널을 선택해 주세요.")
            return
        }
        guildConfigService.setMogakcoBoardChannel(guild.idLong, channelId)
        event.reply("모각코 공지 채널을 <#${channelId}> 로 설정했습니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleClearMogakcoBoardChannel(event: SlashCommandInteractionEvent) {
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

        guildConfigService.clearMogakcoBoardChannel(guild.idLong)
        event.reply("모각코 공지 채널 설정을 해제했습니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleSetAssignmentBoardChannel(event: SlashCommandInteractionEvent) {
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

        val channelId = resolveTextChannelId(event) ?: run {
            replyInvalidInputError(event, "`채널` 옵션으로 텍스트 채널을 선택해 주세요.")
            return
        }
        guildConfigService.setAssignmentBoardChannel(guild.idLong, channelId)
        event.reply("과제 공지 채널을 <#${channelId}> 로 설정했습니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleClearAssignmentBoardChannel(event: SlashCommandInteractionEvent) {
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

        guildConfigService.clearAssignmentBoardChannel(guild.idLong)
        event.reply("과제 공지 채널 설정을 해제했습니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleViewBoardChannels(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        val boards = guildConfigService.getBoardChannels(guild.idLong)
        val meeting = boards.meetingChannelId?.let { "<#${it}>" } ?: "미설정"
        val mogakco = boards.mogakcoChannelId?.let { "<#${it}>" } ?: "미설정"
        val assignment = boards.assignmentChannelId?.let { "<#${it}>" } ?: "미설정"
        event.reply(
            "공지 채널 설정\n" +
                "• 회의: $meeting\n" +
                "• 모각코: $mogakco\n" +
                "• 과제: $assignment",
        )
            .setEphemeral(true)
            .queue()
    }

    private fun resolveTextChannelId(event: SlashCommandInteractionEvent): Long? {
        val channel = event.getOption(OPTION_CHANNEL_KO)?.asChannel ?: event.getOption(OPTION_CHANNEL_EN)?.asChannel ?: return null
        if (channel.type != ChannelType.TEXT && channel.type != ChannelType.NEWS) {
            return null
        }
        return channel.idLong
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
        private const val SUBCOMMAND_SET_MEETING_OPENER_ROLE_KO = "회의열기역할"
        private const val SUBCOMMAND_SET_MEETING_OPENER_ROLE_EN = "meetingopenerrole"
        private const val SUBCOMMAND_CLEAR_MEETING_OPENER_ROLE_KO = "회의열기해제"
        private const val SUBCOMMAND_CLEAR_MEETING_OPENER_ROLE_EN = "meetingopenerclear"
        private const val SUBCOMMAND_VIEW_MEETING_OPENER_ROLE_KO = "회의열기조회"
        private const val SUBCOMMAND_VIEW_MEETING_OPENER_ROLE_EN = "meetingopenershow"
        private const val SUBCOMMAND_SET_MEETING_BOARD_CHANNEL_KO = "회의채널"
        private const val SUBCOMMAND_SET_MEETING_BOARD_CHANNEL_EN = "meetingchannel"
        private const val SUBCOMMAND_CLEAR_MEETING_BOARD_CHANNEL_KO = "회의채널해제"
        private const val SUBCOMMAND_CLEAR_MEETING_BOARD_CHANNEL_EN = "meetingchannelclear"
        private const val SUBCOMMAND_SET_MOGAKCO_BOARD_CHANNEL_KO = "모각코채널"
        private const val SUBCOMMAND_SET_MOGAKCO_BOARD_CHANNEL_EN = "mogakcochannel"
        private const val SUBCOMMAND_CLEAR_MOGAKCO_BOARD_CHANNEL_KO = "모각코채널해제"
        private const val SUBCOMMAND_CLEAR_MOGAKCO_BOARD_CHANNEL_EN = "mogakcochannelclear"
        private const val SUBCOMMAND_SET_ASSIGNMENT_BOARD_CHANNEL_KO = "과제공지채널"
        private const val SUBCOMMAND_SET_ASSIGNMENT_BOARD_CHANNEL_EN = "assignmentchannel"
        private const val SUBCOMMAND_CLEAR_ASSIGNMENT_BOARD_CHANNEL_KO = "과제공지해제"
        private const val SUBCOMMAND_CLEAR_ASSIGNMENT_BOARD_CHANNEL_EN = "assignmentchannelclear"
        private const val SUBCOMMAND_VIEW_BOARD_CHANNELS_KO = "채널조회"
        private const val SUBCOMMAND_VIEW_BOARD_CHANNELS_EN = "channelshow"
        private const val OPTION_ROLE_KO = "역할"
        private const val OPTION_ROLE_LEGACY_TARGET_KO = "대상역할"
        private const val OPTION_ROLE_EN = "role"
        private const val OPTION_CHANNEL_KO = "채널"
        private const val OPTION_CHANNEL_EN = "channel"
    }
}
