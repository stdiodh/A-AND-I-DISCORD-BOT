package com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui

import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import org.springframework.stereotype.Component

@Component
class HomeDashboardComponentBuilder {

    fun buildHomeV2Components(
        guildId: Long,
        agendaUrl: String?,
        channelTargets: ChannelTargets,
        moreMenuOptions: MoreMenuOptions,
    ): List<ActionRow> {
        val components = mutableListOf<ActionRow>()
        components.add(
            ActionRow.of(
                buildMeetingMoveButton(guildId, channelTargets.meetingChannelId),
                buildAssignmentMoveButton(guildId, channelTargets.assignmentChannelId),
                buildMogakcoMoveButton(guildId, channelTargets.mogakcoChannelId),
            ),
        )
        components.add(ActionRow.of(buildMoreMenu(moreMenuOptions)))

        if (agendaUrl != null) {
            components.add(ActionRow.of(Button.link(agendaUrl, "오늘 안건 링크")))
        }
        return components
    }

    private fun buildMeetingMoveButton(guildId: Long, channelId: Long?): Button {
        if (channelId != null) {
            return Button.link(channelJumpUrl(guildId, channelId), "회의 이동")
        }
        return Button.secondary(DashboardActionIds.HOME_MEETING_MOVE_UNSET, "회의 이동").asDisabled()
    }

    private fun buildAssignmentMoveButton(guildId: Long, channelId: Long?): Button {
        if (channelId != null) {
            return Button.link(channelJumpUrl(guildId, channelId), "과제 이동")
        }
        return Button.secondary(DashboardActionIds.HOME_ASSIGNMENT_MOVE_UNSET, "과제 이동").asDisabled()
    }

    private fun buildMogakcoMoveButton(guildId: Long, channelId: Long?): Button {
        if (channelId != null) {
            return Button.link(channelJumpUrl(guildId, channelId), "모각코 이동")
        }
        return Button.secondary(DashboardActionIds.HOME_MOGAKCO_MOVE_UNSET, "모각코 이동").asDisabled()
    }

    private fun buildMoreMenu(options: MoreMenuOptions): StringSelectMenu {
        return StringSelectMenu.create(DashboardActionIds.HOME_MORE_SELECT)
            .setPlaceholder("더보기")
            .addOption("안건 설정", options.agendaValue)
            .addOption("내 기록(개인)", options.mogakcoMeValue)
            .addOption("설정/도움말", options.settingsHelpValue)
            .build()
    }

    private fun channelJumpUrl(guildId: Long, channelId: Long): String {
        return "https://discord.com/channels/$guildId/$channelId"
    }

    data class ChannelTargets(
        val meetingChannelId: Long?,
        val assignmentChannelId: Long?,
        val mogakcoChannelId: Long?,
    )

    data class MoreMenuOptions(
        val agendaValue: String,
        val mogakcoMeValue: String,
        val settingsHelpValue: String,
    )
}
