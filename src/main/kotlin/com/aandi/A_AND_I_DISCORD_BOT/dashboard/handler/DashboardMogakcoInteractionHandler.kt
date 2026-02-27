package com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler

import com.aandi.A_AND_I_DISCORD_BOT.common.format.DurationFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodType
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeCustomIdParser
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionPrefixHandler
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.MogakcoService
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.Locale

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class DashboardMogakcoInteractionHandler(
    private val mogakcoService: MogakcoService,
    private val durationFormatter: DurationFormatter,
) : InteractionPrefixHandler {

    override fun supports(prefix: String): Boolean {
        return prefix in supportedPrefixes
    }

    override fun onButton(event: ButtonInteractionEvent): Boolean {
        if (event.componentId == DashboardActionIds.MOGAKCO_RANK) {
            showPeriodSelect(event, DashboardActionIds.MOGAKCO_RANK_SELECT)
            return true
        }
        if (event.componentId == DashboardActionIds.MOGAKCO_ME) {
            showPeriodSelect(event, DashboardActionIds.MOGAKCO_ME_SELECT)
            return true
        }

        val parsed = HomeCustomIdParser.parse(event.componentId) ?: return false
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
            val medal = medalForIndex(index)
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

    private fun progressBar(value: Double, size: Int): String {
        val clamped = value.coerceIn(0.0, 1.0)
        val filled = (clamped * size).toInt()
        val empty = size - filled
        return "▓".repeat(filled) + "░".repeat(empty)
    }

    private fun medalForIndex(index: Int): String = rankMedalMap[index] ?: "🏅"

    companion object {
        private val supportedPrefixes = setOf("dash", "mogakco", "home")
        private val rankMedalMap = mapOf(
            0 to "🥇",
            1 to "🥈",
            2 to "🥉",
        )
    }
}
