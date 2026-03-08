package com.aandi.A_AND_I_DISCORD_BOT.admin.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.auth.AdminPermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorCode
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorResponse
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.MogakcoService
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component

@Component
class AdminSettingSlashCommandHandler(
    private val guildConfigService: GuildConfigService,
    private val adminPermissionChecker: AdminPermissionChecker,
    private val mogakcoService: MogakcoService,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND_NAME_KO && event.name != COMMAND_NAME_EN) {
            return
        }
        if (isSubcommand(event, SUBCOMMAND_SETUP_WIZARD_KO, SUBCOMMAND_SETUP_WIZARD_EN)) {
            handleSetupWizard(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_STATUS_KO, SUBCOMMAND_STATUS_EN)) {
            handleStatus(event)
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
        if (isSubcommand(event, SUBCOMMAND_SET_ASSIGNMENT_NOTIFY_ROLE_KO, SUBCOMMAND_SET_ASSIGNMENT_NOTIFY_ROLE_EN)) {
            handleSetAssignmentNotifyRole(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_CLEAR_ASSIGNMENT_NOTIFY_ROLE_KO, SUBCOMMAND_CLEAR_ASSIGNMENT_NOTIFY_ROLE_EN)) {
            handleClearAssignmentNotifyRole(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_VIEW_BOARD_CHANNELS_KO, SUBCOMMAND_VIEW_BOARD_CHANNELS_EN)) {
            handleStatus(event)
            return
        }
        replyInvalidInputError(event, "지원하지 않는 하위 명령입니다.")
    }

    private fun handleSetupWizard(event: SlashCommandInteractionEvent) {
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

        val meetingChannelId = resolveTextChannelId(event, OPTION_WIZARD_MEETING_CHANNEL_KO, OPTION_WIZARD_MEETING_CHANNEL_EN)
        val mogakcoChannelId = resolveTextChannelId(event, OPTION_WIZARD_MOGAKCO_CHANNEL_KO, OPTION_WIZARD_MOGAKCO_CHANNEL_EN)
        val homeChannelId = resolveTextChannelId(event, OPTION_WIZARD_HOME_CHANNEL_KO, OPTION_WIZARD_HOME_CHANNEL_EN)
        val adminRoleId = resolveRoleId(event, OPTION_WIZARD_ADMIN_ROLE_KO, OPTION_WIZARD_ADMIN_ROLE_EN)
        val mogakcoVoiceAddChannelId =
            resolveVoiceChannelId(event, OPTION_WIZARD_MOGAKCO_VOICE_ADD_KO, OPTION_WIZARD_MOGAKCO_VOICE_ADD_EN)
        val mogakcoVoiceRemoveChannelId =
            resolveVoiceChannelId(event, OPTION_WIZARD_MOGAKCO_VOICE_REMOVE_KO, OPTION_WIZARD_MOGAKCO_VOICE_REMOVE_EN)
        val assignmentChannelId =
            resolveTextChannelId(event, OPTION_WIZARD_ASSIGNMENT_CHANNEL_KO, OPTION_WIZARD_ASSIGNMENT_CHANNEL_EN)
        val assignmentNotifyRoleId =
            resolveRoleId(event, OPTION_WIZARD_ASSIGNMENT_NOTIFY_ROLE_KO, OPTION_WIZARD_ASSIGNMENT_NOTIFY_ROLE_EN)
        val meetingOpenerRoleId =
            resolveRoleId(event, OPTION_WIZARD_MEETING_OPENER_ROLE_KO, OPTION_WIZARD_MEETING_OPENER_ROLE_EN)

        if (
            meetingChannelId == null &&
            mogakcoChannelId == null &&
            homeChannelId == null &&
            adminRoleId == null &&
            mogakcoVoiceAddChannelId == null &&
            mogakcoVoiceRemoveChannelId == null &&
            assignmentChannelId == null &&
            assignmentNotifyRoleId == null &&
            meetingOpenerRoleId == null
        ) {
            event.reply(
                "설정할 항목을 하나 이상 선택해 주세요.\n" +
                    "예시: `/설정 마법사 운영진역할:@운영진 홈채널:#홈 회의채널:#회의 모각코채널:#모각코 과제공지채널:#과제 과제알림역할:@알림역할`\n" +
                    "현재 상태 확인: `/설정 상태`",
            )
                .setEphemeral(true)
                .queue()
            return
        }

        val requesterRoleIds = member.roles.map { it.idLong }.toSet()
        val hasManagePermission = hasManageServerPermission(member)

        if (adminRoleId != null) {
            guildConfigService.setAdminRole(guild.idLong, adminRoleId)
        }
        if (homeChannelId != null) {
            guildConfigService.setDashboardChannel(guild.idLong, homeChannelId)
        }
        if (meetingChannelId != null) {
            guildConfigService.setMeetingBoardChannel(guild.idLong, meetingChannelId)
        }
        if (mogakcoChannelId != null) {
            guildConfigService.setMogakcoBoardChannel(guild.idLong, mogakcoChannelId)
        }
        var mogakcoVoiceAddResult: MogakcoService.ChannelUpdateResult? = null
        var mogakcoVoiceRemoveResult: MogakcoService.ChannelUpdateResult? = null
        if (mogakcoVoiceAddChannelId != null) {
            mogakcoVoiceAddResult = mogakcoService.addChannel(
                guildId = guild.idLong,
                requesterRoleIds = requesterRoleIds,
                hasManageServerPermission = hasManagePermission,
                channelId = mogakcoVoiceAddChannelId,
            )
        }
        if (mogakcoVoiceRemoveChannelId != null) {
            mogakcoVoiceRemoveResult = mogakcoService.removeChannel(
                guildId = guild.idLong,
                requesterRoleIds = requesterRoleIds,
                hasManageServerPermission = hasManagePermission,
                channelId = mogakcoVoiceRemoveChannelId,
            )
        }
        if (assignmentChannelId != null) {
            guildConfigService.setAssignmentBoardChannel(guild.idLong, assignmentChannelId)
        }
        if (assignmentNotifyRoleId != null) {
            guildConfigService.setDefaultNotifyRole(guild.idLong, assignmentNotifyRoleId)
        }
        if (meetingOpenerRoleId != null) {
            guildConfigService.setMeetingOpenerRole(guild.idLong, meetingOpenerRoleId)
        }

        val boards = guildConfigService.getBoardChannels(guild.idLong)
        val taskDefaults = guildConfigService.getTaskDefaults(guild.idLong)
        val openerRole = guildConfigService.getMeetingOpenerRole(guild.idLong)
        val adminRole = guildConfigService.getAdminRole(guild.idLong)
        val dashboard = guildConfigService.getDashboard(guild.idLong)
        val mogakcoVoiceChannels = when (
            val listResult = mogakcoService.listChannels(
                guildId = guild.idLong,
                requesterRoleIds = requesterRoleIds,
                hasManageServerPermission = hasManagePermission,
            )
        ) {
            is MogakcoService.ChannelListResult.Success -> listResult.channelIds
            MogakcoService.ChannelListResult.Forbidden -> emptyList()
        }
        event.reply(
            buildString {
                appendLine("설정 마법사를 적용했습니다.")
                appendLine("1) 권한 설정")
                appendLine("• 운영진 역할: ${formatRoleMention(adminRole)}")
                appendLine("• 회의 시작 권한 역할: ${formatRoleMention(openerRole)}")
                appendLine("2) 채널 설정")
                appendLine("• 홈 채널: ${formatChannelMention(dashboard.channelId)}")
                appendLine("• 회의 채널: ${formatChannelMention(boards.meetingChannelId)}")
                appendLine("• 모각코 채널: ${formatChannelMention(boards.mogakcoChannelId)}")
                appendLine("• 모각코 집계 음성채널: ${formatChannelMentionList(mogakcoVoiceChannels)}")
                appendLine("• 과제 채널: ${formatChannelMention(boards.assignmentChannelId)}")
                appendLine("3) 과제 기본값")
                appendLine("• 과제 기본 알림 역할: ${formatRoleMention(taskDefaults.defaultNotifyRoleId)}")
                appendLine("4) 상태 확인")
                mogakcoVoiceAddResult?.let {
                    appendLine("• 모각코 음성 추가 결과: ${formatMogakcoChannelUpdateResult(it, mogakcoVoiceAddChannelId)}")
                }
                mogakcoVoiceRemoveResult?.let {
                    appendLine("• 모각코 음성 해제 결과: ${formatMogakcoChannelUpdateResult(it, mogakcoVoiceRemoveChannelId)}")
                }
                append("홈 상태 반영이 필요하면 `/홈 설치`를 실행해 주세요.")
            },
        )
            .setEphemeral(true)
            .queue()
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

    private fun handleSetAssignmentNotifyRole(event: SlashCommandInteractionEvent) {
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

        guildConfigService.setDefaultNotifyRole(guild.idLong, role.idLong)
        event.reply("과제 기본 알림 역할을 <@&${role.idLong}> 로 설정했습니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleClearAssignmentNotifyRole(event: SlashCommandInteractionEvent) {
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

        guildConfigService.clearDefaultNotifyRole(guild.idLong)
        event.reply("과제 기본 알림 역할 설정을 해제했습니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleStatus(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            replyInvalidInputError(event, "길드에서만 사용할 수 있습니다.")
            return
        }
        val boards = guildConfigService.getBoardChannels(guild.idLong)
        val taskDefaults = guildConfigService.getTaskDefaults(guild.idLong)
        val dashboard = guildConfigService.getDashboard(guild.idLong)
        val adminRoleId = guildConfigService.getAdminRole(guild.idLong)
        val openerRoleId = guildConfigService.getMeetingOpenerRole(guild.idLong)
        event.reply(
            buildString {
                appendLine("설정 상태")
                appendLine("• 운영진 역할: ${formatRoleMention(adminRoleId)}")
                appendLine("• 회의 시작 권한 역할: ${formatRoleMention(openerRoleId)}")
                appendLine("• 회의 채널: ${formatChannelMention(boards.meetingChannelId)}")
                appendLine("• 모각코 채널: ${formatChannelMention(boards.mogakcoChannelId)}")
                appendLine("• 과제 채널: ${formatChannelMention(boards.assignmentChannelId)}")
                appendLine("• 과제 기본 알림 역할: ${formatRoleMention(taskDefaults.defaultNotifyRoleId)}")
                appendLine("• 홈 채널: ${formatChannelMention(dashboard.channelId)}")
                appendLine("• 모각코 집계 음성채널 관리: `/설정 마법사 모각코음성추가|모각코음성해제`")
                append("빠른 변경: `/설정 마법사`")
            },
        )
            .setEphemeral(true)
            .queue()
    }

    private fun resolveTextChannelId(event: SlashCommandInteractionEvent): Long? {
        return resolveTextChannelId(event, OPTION_CHANNEL_KO, OPTION_CHANNEL_EN)
    }

    private fun resolveTextChannelId(
        event: SlashCommandInteractionEvent,
        optionKo: String,
        optionEn: String,
    ): Long? {
        val channel = event.getOption(optionKo)?.asChannel ?: event.getOption(optionEn)?.asChannel ?: return null
        if (channel.type != ChannelType.TEXT && channel.type != ChannelType.NEWS) {
            return null
        }
        return channel.idLong
    }

    private fun resolveVoiceChannelId(
        event: SlashCommandInteractionEvent,
        optionKo: String,
        optionEn: String,
    ): Long? {
        val channel = event.getOption(optionKo)?.asChannel ?: event.getOption(optionEn)?.asChannel ?: return null
        if (channel.type != ChannelType.VOICE && channel.type != ChannelType.STAGE) {
            return null
        }
        return channel.idLong
    }

    private fun resolveRoleId(
        event: SlashCommandInteractionEvent,
        optionKo: String,
        optionEn: String,
    ): Long? {
        return event.getOption(optionKo)?.asRole?.idLong ?: event.getOption(optionEn)?.asRole?.idLong
    }

    private fun formatRoleMention(roleId: Long?): String {
        return roleId?.let { "<@&$it>" } ?: "미설정"
    }

    private fun formatChannelMention(channelId: Long?): String {
        return channelId?.let { "<#$it>" } ?: "미설정"
    }

    private fun formatChannelMentionList(channelIds: List<Long>): String {
        if (channelIds.isEmpty()) {
            return "미설정"
        }
        val visible = channelIds.take(5).joinToString(", ") { "<#$it>" }
        if (channelIds.size <= 5) {
            return visible
        }
        return "$visible 외 ${channelIds.size - 5}개"
    }

    private fun formatMogakcoChannelUpdateResult(result: MogakcoService.ChannelUpdateResult, channelId: Long?): String {
        val mention = channelId?.let { "<#$it>" } ?: "(채널 없음)"
        return when (result) {
            MogakcoService.ChannelUpdateResult.Added -> "$mention 추가됨"
            MogakcoService.ChannelUpdateResult.Removed -> "$mention 해제됨"
            MogakcoService.ChannelUpdateResult.AlreadyExists -> "$mention 이미 등록됨"
            MogakcoService.ChannelUpdateResult.NotFound -> "$mention 등록 내역 없음"
            MogakcoService.ChannelUpdateResult.Forbidden -> "권한 부족"
        }
    }

    private fun hasManageServerPermission(member: net.dv8tion.jda.api.entities.Member): Boolean {
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true
        }
        return member.hasPermission(Permission.MANAGE_SERVER)
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
        private const val SUBCOMMAND_SETUP_WIZARD_KO = "마법사"
        private const val SUBCOMMAND_SETUP_WIZARD_EN = "wizard"
        private const val SUBCOMMAND_STATUS_KO = "상태"
        private const val SUBCOMMAND_STATUS_EN = "status"
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
        private const val SUBCOMMAND_SET_ASSIGNMENT_NOTIFY_ROLE_KO = "과제알림역할"
        private const val SUBCOMMAND_SET_ASSIGNMENT_NOTIFY_ROLE_EN = "assignmentnotifyrole"
        private const val SUBCOMMAND_CLEAR_ASSIGNMENT_NOTIFY_ROLE_KO = "과제알림역할해제"
        private const val SUBCOMMAND_CLEAR_ASSIGNMENT_NOTIFY_ROLE_EN = "assignmentnotifyroleclear"
        private const val SUBCOMMAND_VIEW_BOARD_CHANNELS_KO = "채널조회"
        private const val SUBCOMMAND_VIEW_BOARD_CHANNELS_EN = "channelshow"
        private const val OPTION_ROLE_KO = "역할"
        private const val OPTION_ROLE_LEGACY_TARGET_KO = "대상역할"
        private const val OPTION_ROLE_EN = "role"
        private const val OPTION_CHANNEL_KO = "채널"
        private const val OPTION_CHANNEL_EN = "channel"
        private const val OPTION_WIZARD_MEETING_CHANNEL_KO = "회의채널"
        private const val OPTION_WIZARD_MEETING_CHANNEL_EN = "meetingchannel"
        private const val OPTION_WIZARD_MOGAKCO_CHANNEL_KO = "모각코채널"
        private const val OPTION_WIZARD_MOGAKCO_CHANNEL_EN = "mogakcochannel"
        private const val OPTION_WIZARD_HOME_CHANNEL_KO = "홈채널"
        private const val OPTION_WIZARD_HOME_CHANNEL_EN = "homechannel"
        private const val OPTION_WIZARD_ADMIN_ROLE_KO = "운영진역할"
        private const val OPTION_WIZARD_ADMIN_ROLE_EN = "adminrole"
        private const val OPTION_WIZARD_MOGAKCO_VOICE_ADD_KO = "모각코음성추가"
        private const val OPTION_WIZARD_MOGAKCO_VOICE_ADD_EN = "mogakcovoiceadd"
        private const val OPTION_WIZARD_MOGAKCO_VOICE_REMOVE_KO = "모각코음성해제"
        private const val OPTION_WIZARD_MOGAKCO_VOICE_REMOVE_EN = "mogakcovoiceremove"
        private const val OPTION_WIZARD_ASSIGNMENT_CHANNEL_KO = "과제공지채널"
        private const val OPTION_WIZARD_ASSIGNMENT_CHANNEL_EN = "assignmentchannel"
        private const val OPTION_WIZARD_ASSIGNMENT_NOTIFY_ROLE_KO = "과제알림역할"
        private const val OPTION_WIZARD_ASSIGNMENT_NOTIFY_ROLE_EN = "assignmentnotifyrole"
        private const val OPTION_WIZARD_MEETING_OPENER_ROLE_KO = "회의열기역할"
        private const val OPTION_WIZARD_MEETING_OPENER_ROLE_EN = "meetingopenerrole"
    }
}
