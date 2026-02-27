package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.auth.AdminPermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.agenda.service.AgendaService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentStatus
import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
import com.aandi.A_AND_I_DISCORD_BOT.common.format.DurationFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodType
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeCustomIdParser
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionPrefixHandler
import com.aandi.A_AND_I_DISCORD_BOT.meeting.service.MeetingService
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.MogakcoService
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.modals.Modal
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.Locale

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class HomeInteractionHandler(
    private val adminPermissionChecker: AdminPermissionChecker,
    private val agendaService: AgendaService,
    private val assignmentTaskService: AssignmentTaskService,
    private val meetingService: MeetingService,
    private val mogakcoService: MogakcoService,
    private val durationFormatter: DurationFormatter,
    private val clock: Clock,
) : InteractionPrefixHandler {

    override fun supports(prefix: String): Boolean {
        return prefix in SUPPORTED_PREFIXES
    }

    override fun onButton(event: ButtonInteractionEvent): Boolean {
        if (event.componentId == DashboardActionIds.MEETING_START) {
            startMeetingFromDashboard(event)
            return true
        }
        if (event.componentId == DashboardActionIds.AGENDA_SET) {
            showAgendaModal(event)
            return true
        }
        if (event.componentId == DashboardActionIds.ASSIGNMENT_CREATE) {
            showTaskModal(event)
            return true
        }
        if (event.componentId == DashboardActionIds.ASSIGNMENT_LIST) {
            showTaskList(event)
            return true
        }
        if (event.componentId == DashboardActionIds.MOGAKCO_RANK) {
            showPeriodSelect(event, DashboardActionIds.MOGAKCO_RANK_SELECT)
            return true
        }
        if (event.componentId == DashboardActionIds.MOGAKCO_ME) {
            showPeriodSelect(event, DashboardActionIds.MOGAKCO_ME_SELECT)
            return true
        }

        val parsed = HomeCustomIdParser.parse(event.componentId) ?: return false
        if (parsed.domain == "meeting" && parsed.action == "start") {
            startMeetingFromDashboard(event)
            return true
        }
        if (parsed.domain == "agenda" && parsed.action == "set") {
            showAgendaModal(event)
            return true
        }
        if (parsed.domain == "task" && parsed.action == "create") {
            showTaskModal(event)
            return true
        }
        if (parsed.domain == "task" && parsed.action == "list") {
            showTaskList(event)
            return true
        }
        if (parsed.domain == "mogakco" && parsed.action == "leaderboard") {
            showPeriodSelect(event, DashboardActionIds.MOGAKCO_RANK_SELECT)
            return true
        }
        if (parsed.domain == "mogakco" && parsed.action == "me") {
            showPeriodSelect(event, DashboardActionIds.MOGAKCO_ME_SELECT)
            return true
        }
        return false
    }

    override fun onStringSelect(event: StringSelectInteractionEvent): Boolean {
        if (event.componentId == DashboardActionIds.MOGAKCO_RANK_SELECT) {
            val period = parsePeriod(event.values.firstOrNull())
            if (period == null) {
                event.reply("기간 선택값이 올바르지 않습니다.").setEphemeral(true).queue()
                return true
            }
            showMogakcoLeaderboard(event, period)
            return true
        }
        if (event.componentId == DashboardActionIds.MOGAKCO_ME_SELECT) {
            val period = parsePeriod(event.values.firstOrNull())
            if (period == null) {
                event.reply("기간 선택값이 올바르지 않습니다.").setEphemeral(true).queue()
                return true
            }
            showMogakcoMe(event, period)
            return true
        }

        val parsed = HomeCustomIdParser.parse(event.componentId) ?: return false
        if (parsed.domain != "mogakco" || parsed.action != "select") {
            return false
        }

        val mode = parsed.tailAt(0) ?: return false
        val period = parsePeriod(event.values.firstOrNull())
        if (period == null) {
            event.reply("기간 선택값이 올바르지 않습니다.")
                .setEphemeral(true)
                .queue()
            return true
        }

        if (mode == "leaderboard") {
            showMogakcoLeaderboard(event, period)
            return true
        }
        if (mode == "me") {
            showMogakcoMe(event, period)
            return true
        }
        return false
    }

    override fun onModal(event: ModalInteractionEvent): Boolean {
        if (event.modalId == DashboardActionIds.AGENDA_MODAL) {
            submitAgendaSet(event)
            return true
        }
        if (event.modalId == DashboardActionIds.ASSIGNMENT_MODAL) {
            submitTaskCreate(event)
            return true
        }

        val parsed = HomeCustomIdParser.parse(event.modalId) ?: return false
        if (parsed.domain == "agenda" && parsed.action == "modal") {
            submitAgendaSet(event)
            return true
        }
        if (parsed.domain == "task" && parsed.action == "modal") {
            submitTaskCreate(event)
            return true
        }
        return false
    }

    private fun startMeetingFromDashboard(event: ButtonInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!canAdminAction(guild.idLong, member)) {
            event.reply("회의 시작 권한이 없습니다.").setEphemeral(true).queue()
            return
        }

        val fallbackChannelId = event.channel.idLong
        when (
            val result = meetingService.startMeeting(
                guildId = guild.idLong,
                requestedBy = member.idLong,
                targetChannelId = null,
                fallbackChannelId = fallbackChannelId,
                rawTitle = null,
            )
        ) {
            is MeetingService.StartResult.Success -> {
                event.reply("회의 스레드를 생성했습니다: <#${result.threadId}>")
                    .setEphemeral(true)
                    .queue()
            }

            is MeetingService.StartResult.AlreadyActive -> {
                event.reply("이미 진행 중인 회의가 있습니다: <#${result.threadId}>")
                    .setEphemeral(true)
                    .queue()
            }

            MeetingService.StartResult.ChannelNotConfigured -> {
                event.reply("회의 채널이 설정되지 않았습니다. `/홈 생성` 후 다시 시도해 주세요.")
                    .setEphemeral(true)
                    .queue()
            }

            MeetingService.StartResult.ChannelNotFound -> {
                event.reply("회의 채널을 찾지 못했습니다.").setEphemeral(true).queue()
            }

            MeetingService.StartResult.ThreadCreateFailed -> {
                event.reply("회의 스레드 생성에 실패했습니다.").setEphemeral(true).queue()
            }
        }
    }

    private fun showAgendaModal(event: ButtonInteractionEvent) {
        val link = TextInput.create("링크", TextInputStyle.SHORT)
            .setRequired(true)
            .setPlaceholder("https://docs.google.com/...")
            .setMaxLength(500)
            .build()
        val title = TextInput.create("제목", TextInputStyle.SHORT)
            .setRequired(false)
            .setPlaceholder("오늘 안건")
            .setMaxLength(255)
            .build()
        val modal = Modal.create(DashboardActionIds.AGENDA_MODAL, "안건 설정")
            .addComponents(
                Label.of("안건 링크", link),
                Label.of("안건 제목(선택)", title),
            )
            .build()
        event.replyModal(modal).queue()
    }

    private fun showTaskModal(event: ButtonInteractionEvent) {
        val title = TextInput.create("제목", TextInputStyle.SHORT)
            .setRequired(true)
            .setMaxLength(200)
            .build()
        val link = TextInput.create("링크", TextInputStyle.SHORT)
            .setRequired(true)
            .setMaxLength(500)
            .build()
        val remindAt = TextInput.create("알림시각", TextInputStyle.SHORT)
            .setRequired(true)
            .setMaxLength(19)
            .build()
        val channelId = TextInput.create("채널", TextInputStyle.SHORT)
            .setRequired(false)
            .setMaxLength(40)
            .build()
        val modal = Modal.create(DashboardActionIds.ASSIGNMENT_MODAL, "과제 등록")
            .addComponents(
                Label.of("과제 제목", title),
                Label.of("검증 링크(http/https)", link),
                Label.of("알림시각 (예: 2026-03-01 21:30)", remindAt),
                Label.of("채널(선택, #멘션 또는 ID)", channelId),
            )
            .build()
        event.replyModal(modal).queue()
    }

    private fun showTaskList(event: ButtonInteractionEvent) {
        val guild = event.guild ?: run {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        val result = assignmentTaskService.list(guild.idLong, null)
        if (result is AssignmentTaskService.ListResult.InvalidStatus) {
            event.reply("과제 상태값이 올바르지 않습니다.").setEphemeral(true).queue()
            return
        }

        val tasks = (result as AssignmentTaskService.ListResult.Success).tasks
        if (tasks.isEmpty()) {
            event.reply("등록된 과제가 없습니다.").setEphemeral(true).queue()
            return
        }

        val lines = tasks.take(10).map {
            "• ${statusEmoji(it.status)} [${it.id}] ${it.title} | ${KstTime.formatInstantToKst(it.remindAt)} | <@${it.createdBy}>"
        }
        event.reply("과제 목록(최대 10건)\n${lines.joinToString("\n")}")
            .setEphemeral(true)
            .queue()
    }

    private fun showPeriodSelect(event: ButtonInteractionEvent, customId: String) {
        val menu = StringSelectMenu.create(customId)
            .setPlaceholder("기간을 선택하세요")
            .addOption("주간", "week")
            .addOption("월간", "month")
            .build()
        event.reply("조회할 기간을 선택하세요.")
            .addComponents(ActionRow.of(menu))
            .setEphemeral(true)
            .queue()
    }

    private fun showMogakcoLeaderboard(event: StringSelectInteractionEvent, period: PeriodType) {
        val guild = event.guild ?: run {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        val leaderboard = mogakcoService.getLeaderboard(guild.idLong, period, 10)
        if (leaderboard.entries.isEmpty()) {
            event.reply("📭 기록이 없습니다.").setEphemeral(true).queue()
            return
        }

        val maxSeconds = leaderboard.entries.maxOf { it.totalSeconds }.coerceAtLeast(1L)
        val rows = leaderboard.entries.mapIndexed { index, entry ->
            val medal = when (index) {
                0 -> "🥇"
                1 -> "🥈"
                2 -> "🥉"
                else -> "🏅"
            }
            val bar = progressBar(entry.totalSeconds.toDouble() / maxSeconds.toDouble(), 8)
            "$medal <@${entry.userId}> ${durationFormatter.toHourMinute(entry.totalSeconds)} $bar"
        }
        event.reply("${periodLabel(period)} 모각코 랭킹\n${rows.joinToString("\n")}")
            .setEphemeral(true)
            .queue()
    }

    private fun showMogakcoMe(event: StringSelectInteractionEvent, period: PeriodType) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }

        val stats = mogakcoService.getMyStats(guild.idLong, member.idLong, period)
        val message = buildString {
            appendLine("${periodLabel(period)} 내 기록 📈")
            appendLine("⏱ 누적시간: ${durationFormatter.toHourMinute(stats.totalSeconds)}")
            appendLine("📅 참여일: ${stats.activeDays}/${stats.totalDays}일 (기준 ${stats.activeMinutesThreshold}분)")
            append("📊 참여율: ${formatPercent(stats.participationRate)} ${progressBar(stats.participationRate, 10)}")
        }
        event.reply(message)
            .setEphemeral(true)
            .queue()
    }

    private fun submitAgendaSet(event: ModalInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!canAdminAction(guild.idLong, member)) {
            event.reply("안건 설정 권한이 없습니다.").setEphemeral(true).queue()
            return
        }

        val url = event.getValue("링크")?.asString
        if (url.isNullOrBlank()) {
            event.reply("링크는 필수입니다.").setEphemeral(true).queue()
            return
        }

        val result = agendaService.setTodayAgenda(
            guildId = guild.idLong,
            requesterUserId = member.idLong,
            requesterRoleIds = member.roles.map { it.idLong }.toSet(),
            hasManageServerPermission = adminPermissionChecker.canSetAdminRole(guild.idLong, member),
            rawUrl = url,
            rawTitle = event.getValue("제목")?.asString,
        )
        when (result) {
            is AgendaService.SetAgendaResult.Success -> {
                event.reply("오늘 안건 링크를 저장했습니다: ${result.title}")
                    .setEphemeral(true)
                    .queue()
            }

            AgendaService.SetAgendaResult.Forbidden -> {
                event.reply("안건 설정 권한이 없습니다.").setEphemeral(true).queue()
            }

            AgendaService.SetAgendaResult.InvalidUrl -> {
                event.reply("URL 형식이 올바르지 않습니다.").setEphemeral(true).queue()
            }

            AgendaService.SetAgendaResult.InvalidTitle -> {
                event.reply("제목 길이가 너무 깁니다.").setEphemeral(true).queue()
            }
        }
    }

    private fun submitTaskCreate(event: ModalInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.").setEphemeral(true).queue()
            return
        }
        if (!canAdminAction(guild.idLong, member)) {
            event.reply("과제 등록 권한이 없습니다.").setEphemeral(true).queue()
            return
        }

        val title = event.getValue("제목")?.asString.orEmpty()
        val link = event.getValue("링크")?.asString.orEmpty()
        val remindRaw = event.getValue("알림시각")?.asString.orEmpty()
        val remindAtUtc = runCatching { KstTime.parseKstToInstant(remindRaw) }.getOrElse {
            event.reply("알림시각 형식이 올바르지 않습니다. 예: 2026-03-01 21:30")
                .setEphemeral(true)
                .queue()
            return
        }

        val channelRaw = event.getValue("채널")?.asString?.trim().orEmpty()
        val channelId = parseChannelId(channelRaw) ?: event.channel.idLong
        val result = assignmentTaskService.create(
            guildId = guild.idLong,
            channelId = channelId,
            title = title,
            verifyUrl = link,
            remindAtUtc = remindAtUtc,
            createdBy = member.idLong,
            nowUtc = Instant.now(clock),
        )
        when (result) {
            is AssignmentTaskService.CreateResult.Success -> {
                val task = result.task
                event.reply("과제를 등록했습니다. ID: ${task.id}, 알림시각(KST): ${KstTime.formatInstantToKst(task.remindAt)}")
                    .setEphemeral(true)
                    .queue()
            }

            AssignmentTaskService.CreateResult.InvalidUrl -> {
                event.reply("링크는 http/https만 허용됩니다.").setEphemeral(true).queue()
            }

            AssignmentTaskService.CreateResult.InvalidTitle -> {
                event.reply("제목은 1~200자여야 합니다.").setEphemeral(true).queue()
            }

            AssignmentTaskService.CreateResult.RemindAtMustBeFuture -> {
                event.reply("알림시각은 현재보다 미래여야 합니다.").setEphemeral(true).queue()
            }
        }
    }

    private fun canAdminAction(guildId: Long, member: Member): Boolean {
        if (adminPermissionChecker.isAdmin(guildId, member)) {
            return true
        }
        return adminPermissionChecker.canSetAdminRole(guildId, member)
    }

    private fun parsePeriod(raw: String?): PeriodType? {
        if (raw == "week") {
            return PeriodType.WEEK
        }
        if (raw == "month") {
            return PeriodType.MONTH
        }
        return null
    }

    private fun periodLabel(period: PeriodType): String = when (period) {
        PeriodType.WEEK -> "이번 주"
        PeriodType.MONTH -> "이번 달"
    }

    private fun formatPercent(rate: Double): String = String.format(Locale.US, "%.1f%%", rate * 100.0)

    private fun statusEmoji(status: AssignmentStatus): String = when (status) {
        AssignmentStatus.PENDING -> "🕒"
        AssignmentStatus.DONE -> "✅"
        AssignmentStatus.CANCELED -> "❌"
    }

    private fun parseChannelId(raw: String): Long? {
        if (raw.isBlank()) {
            return null
        }
        if (raw.startsWith("<#") && raw.endsWith(">")) {
            return raw.removePrefix("<#").removeSuffix(">").toLongOrNull()
        }
        return raw.toLongOrNull()
    }

    private fun progressBar(value: Double, size: Int): String {
        val clamped = value.coerceIn(0.0, 1.0)
        val filled = (clamped * size).toInt()
        val empty = size - filled
        return "▓".repeat(filled) + "░".repeat(empty)
    }

    companion object {
        private val SUPPORTED_PREFIXES = setOf("dash", "meeting", "assign", "mogakco", "home")
    }
}
