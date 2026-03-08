package com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui

import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import org.springframework.stereotype.Component

@Component
class HomeDashboardComponentBuilder {

    fun buildHomeV2Components(
        guildId: Long,
        activeMeetingThreadId: Long?,
        agendaUrl: String?,
        channelTargets: ChannelTargets,
        moreMenuOptions: MoreMenuOptions,
    ): List<ActionRow> {
        val components = mutableListOf<ActionRow>()
        val hasMissingChannels = channelTargets.meetingChannelId == null ||
            channelTargets.assignmentChannelId == null
        val homeState = resolveHomeState(hasMissingChannels, activeMeetingThreadId)
        components.add(
            when (homeState) {
                HomeState.NORMAL -> ActionRow.of(
                    buildMeetingPrimaryButton(guildId, channelTargets.meetingChannelId, activeMeetingThreadId),
                    buildAssignmentQuickButton(),
                    buildMyRecordButton(),
                )

                HomeState.MEETING_ACTIVE -> ActionRow.of(
                    buildMeetingPrimaryButton(guildId, channelTargets.meetingChannelId, activeMeetingThreadId),
                    buildAssignmentQuickButton(),
                    buildMyRecordButton(),
                )

                HomeState.SETUP_INCOMPLETE -> ActionRow.of(
                    Button.primary(DashboardActionIds.HOME_SETUP_START, "설정 시작"),
                    buildMyRecordButton(),
                    buildHelpButton(),
                )
            },
        )

        components.add(ActionRow.of(buildMoreMenu(moreMenuOptions)))

        if (agendaUrl != null) {
            components.add(ActionRow.of(Button.link(agendaUrl, "오늘 안건 링크")))
        }
        return components
    }

    private fun buildMeetingPrimaryButton(guildId: Long, channelId: Long?, activeMeetingThreadId: Long?): Button {
        if (channelId == null) {
            return Button.primary(DashboardActionIds.HOME_SETUP_START_MEETING, "회의 설정")
        }
        if (activeMeetingThreadId != null) {
            return Button.link(channelJumpUrl(guildId, activeMeetingThreadId), "진행 중 회의 열기")
        }
        return Button.primary(DashboardActionIds.MEETING_START, "회의 시작")
    }

    private fun buildAssignmentQuickButton(): Button {
        return Button.success(DashboardActionIds.ASSIGNMENT_CREATE, "빠른 과제")
    }

    private fun buildMyRecordButton(): Button {
        return Button.secondary(DashboardActionIds.MOGAKCO_ME, "내 기록")
    }

    private fun buildHelpButton(): Button {
        return Button.secondary(DashboardActionIds.HOME_QUICK_HELP, "도움말")
    }

    private fun buildMoreMenu(options: MoreMenuOptions): StringSelectMenu {
        return StringSelectMenu.create(DashboardActionIds.HOME_MORE_SELECT)
            .setPlaceholder("더보기")
            .addOption("안건", options.agendaValue)
            .addOption("과제목록", options.assignmentListValue)
            .addOption("모각코", options.mogakcoValue)
            .addOption("설정", options.settingsValue)
            .addOption("도움말", options.helpValue)
            .build()
    }

    private fun channelJumpUrl(guildId: Long, channelId: Long): String {
        return "https://discord.com/channels/$guildId/$channelId"
    }

    private fun resolveHomeState(hasMissingChannels: Boolean, activeMeetingThreadId: Long?): HomeState {
        if (hasMissingChannels) {
            return HomeState.SETUP_INCOMPLETE
        }
        if (activeMeetingThreadId != null) {
            return HomeState.MEETING_ACTIVE
        }
        return HomeState.NORMAL
    }

    data class ChannelTargets(
        val meetingChannelId: Long?,
        val assignmentChannelId: Long?,
        val mogakcoChannelId: Long?,
    )

    data class MoreMenuOptions(
        val agendaValue: String,
        val assignmentListValue: String,
        val mogakcoValue: String,
        val settingsValue: String,
        val helpValue: String,
    )

    private enum class HomeState {
        NORMAL,
        MEETING_ACTIVE,
        SETUP_INCOMPLETE,
    }
}
