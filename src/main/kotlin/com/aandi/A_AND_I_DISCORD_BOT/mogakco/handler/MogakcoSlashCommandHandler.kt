package com.aandi.A_AND_I_DISCORD_BOT.mogakco.handler

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.HomeChannelGuard
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionGate
import com.aandi.A_AND_I_DISCORD_BOT.common.discord.DiscordReplyFactory
import com.aandi.A_AND_I_DISCORD_BOT.common.format.DurationFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodType
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.MogakcoService
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class MogakcoSlashCommandHandler(
    private val mogakcoService: MogakcoService,
    private val durationFormatter: DurationFormatter,
    private val permissionGate: PermissionGate,
    private val discordReplyFactory: DiscordReplyFactory,
    private val guildConfigService: GuildConfigService,
    private val homeChannelGuard: HomeChannelGuard,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND_NAME_KO && event.name != COMMAND_NAME_EN) {
            return
        }

        if (isSubcommandGroup(event, SUBCOMMAND_GROUP_CHANNEL_KO, SUBCOMMAND_GROUP_CHANNEL_EN)) {
            routeChannelCommand(event)
            return
        }

        if (isSubcommand(event, SUBCOMMAND_LEADERBOARD_KO, SUBCOMMAND_LEADERBOARD_EN)) {
            handleLeaderboard(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_ME_KO, SUBCOMMAND_ME_EN)) {
            handleMe(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_TODAY_KO, SUBCOMMAND_TODAY_EN)) {
            handleToday(event)
            return
        }

        replyUnsupported(event)
    }

    private fun routeChannelCommand(event: SlashCommandInteractionEvent) {
        if (isSubcommand(event, SUBCOMMAND_CHANNEL_ADD_KO, SUBCOMMAND_CHANNEL_ADD_EN)) {
            handleChannelAdd(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_CHANNEL_REMOVE_KO, SUBCOMMAND_CHANNEL_REMOVE_EN)) {
            handleChannelRemove(event)
            return
        }
        if (isSubcommand(event, SUBCOMMAND_CHANNEL_LIST_KO, SUBCOMMAND_CHANNEL_LIST_EN)) {
            handleChannelList(event)
            return
        }

        replyUnsupported(event)
    }

    private fun handleChannelAdd(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyGuildOnlyError(event)
            return
        }

        val channel = resolveChannelOption(event)
        if (channel == null) {
            replyInvalidInputError(event, "채널 옵션이 필요합니다.")
            return
        }

        val result = mogakcoService.addChannel(
            guildId = guild.idLong,
            requesterRoleIds = member.roles.map { it.idLong }.toSet(),
            hasManageServerPermission = permissionGate.canAdminAction(guild.idLong, member),
            channelId = channel.idLong,
        )

        when (result) {
            MogakcoService.ChannelUpdateResult.Added -> {
                event.reply("모각코 채널을 등록했습니다: <#${channel.idLong}>")
                    .setEphemeral(true)
                    .queue()
            }

            MogakcoService.ChannelUpdateResult.AlreadyExists -> {
                replyInvalidInputError(event, "이미 등록된 모각코 채널입니다.")
            }

            MogakcoService.ChannelUpdateResult.Forbidden -> {
                replyAccessDeniedError(event)
            }

            MogakcoService.ChannelUpdateResult.NotFound -> {
                replyInvalidInputError(event, "등록할 수 없는 채널입니다.")
            }

            MogakcoService.ChannelUpdateResult.Removed -> {
                replyInvalidInputError(event, "요청 처리에 실패했습니다. 다시 시도해 주세요.")
            }
        }
    }

    private fun handleChannelRemove(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyGuildOnlyError(event)
            return
        }

        val channel = resolveChannelOption(event)
        if (channel == null) {
            replyInvalidInputError(event, "채널 옵션이 필요합니다.")
            return
        }

        val result = mogakcoService.removeChannel(
            guildId = guild.idLong,
            requesterRoleIds = member.roles.map { it.idLong }.toSet(),
            hasManageServerPermission = permissionGate.canAdminAction(guild.idLong, member),
            channelId = channel.idLong,
        )

        when (result) {
            MogakcoService.ChannelUpdateResult.Removed -> {
                event.reply("모각코 채널 등록을 해제했습니다: <#${channel.idLong}>")
                    .setEphemeral(true)
                    .queue()
            }

            MogakcoService.ChannelUpdateResult.NotFound -> {
                replyInvalidInputError(event, "등록된 모각코 채널이 아닙니다.")
            }

            MogakcoService.ChannelUpdateResult.Forbidden -> {
                replyAccessDeniedError(event)
            }

            MogakcoService.ChannelUpdateResult.Added -> {
                replyInvalidInputError(event, "요청 처리에 실패했습니다. 다시 시도해 주세요.")
            }

            MogakcoService.ChannelUpdateResult.AlreadyExists -> {
                replyInvalidInputError(event, "요청 처리에 실패했습니다. 다시 시도해 주세요.")
            }
        }
    }

    private fun handleChannelList(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyGuildOnlyError(event)
            return
        }

        val result = mogakcoService.listChannels(
            guildId = guild.idLong,
            requesterRoleIds = member.roles.map { it.idLong }.toSet(),
            hasManageServerPermission = permissionGate.canAdminAction(guild.idLong, member),
        )

        when (result) {
            is MogakcoService.ChannelListResult.Success -> {
                if (result.channelIds.isEmpty()) {
                    event.reply("등록된 모각코 채널이 없습니다.")
                        .setEphemeral(true)
                        .queue()
                    return
                }

                val lines = result.channelIds.map { "- <#${it}>" }
                event.reply("등록된 모각코 채널 목록\n${lines.joinToString(separator = "\n")}")
                    .setEphemeral(true)
                    .queue()
            }

            MogakcoService.ChannelListResult.Forbidden -> {
                replyAccessDeniedError(event)
            }
        }
    }

    private fun handleLeaderboard(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            replyGuildOnlyError(event)
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }

        val period = parsePeriod(event.getOption(OPTION_PERIOD_KO)?.asString ?: event.getOption(OPTION_PERIOD_EN)?.asString)
        if (period == null) {
            replyInvalidInputError(event, "기간은 day/week/month만 가능합니다.")
            return
        }

        val top = event.getOption(OPTION_TOP_KO)?.asInt ?: event.getOption(OPTION_TOP_EN)?.asInt ?: 10
        val leaderboard = mogakcoService.getLeaderboard(guild.idLong, period, top)
        if (leaderboard.entries.isEmpty()) {
            replyResourceNotFoundError(event, "기록이 없습니다.", false)
            return
        }

        val rows = leaderboard.entries.mapIndexed { index, entry ->
            val medal = medalForIndex(index)
            val maxSeconds = leaderboard.entries.first().totalSeconds.coerceAtLeast(1L)
            val bar = progressBar(entry.totalSeconds.toDouble() / maxSeconds.toDouble(), 8)
            "$medal <@${entry.userId}> - ${durationFormatter.toHourMinute(entry.totalSeconds)} $bar"
        }
        val payload = "${periodLabel(period)} 모각코 랭킹\n${rows.joinToString(separator = "\\n")}"

        val boardChannelId = guildConfigService.getBoardChannels(guild.idLong).mogakcoChannelId
        if (boardChannelId == null || event.channel.idLong == boardChannelId) {
            event.reply(payload).queue()
            return
        }

        val boardChannel = guild.getTextChannelById(boardChannelId)
        if (boardChannel == null) {
            replyInvalidInputError(event, "모각코 공지 채널을 찾지 못했습니다. `/설정 마법사 모각코채널:#모각코`로 다시 설정해 주세요.")
            return
        }

        boardChannel.sendMessage(payload).queue(
            { message ->
                event.reply("모각코 랭킹을 <#${boardChannelId}> 채널에 게시했습니다.\n바로가기: ${message.jumpUrl}")
                    .setEphemeral(true)
                    .queue()
            },
            {
                replyInvalidInputError(event, "모각코 공지 채널로 전송하지 못했습니다. 권한을 확인해 주세요.")
            },
        )
    }

    private fun handleMe(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyGuildOnlyError(event)
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }

        val period = parsePeriod(event.getOption(OPTION_PERIOD_KO)?.asString ?: event.getOption(OPTION_PERIOD_EN)?.asString)
        if (period == null) {
            replyInvalidInputError(event, "기간은 day/week/month만 가능합니다.")
            return
        }

        val stats = mogakcoService.getMyStats(
            guildId = guild.idLong,
            userId = member.idLong,
            period = period,
        )

        val message = buildStatsMessage(period, stats)

        event.reply(message)
            .setEphemeral(true)
            .queue()
    }

    private fun handleToday(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyGuildOnlyError(event)
            return
        }
        if (isBlockedByHomeChannelGuard(event, guild.idLong)) {
            return
        }

        val stats = mogakcoService.getTodayStats(
            guildId = guild.idLong,
            userId = member.idLong,
        )

        event.reply(buildStatsMessage(PeriodType.DAY, stats))
            .setEphemeral(true)
            .queue()
    }

    private fun buildStatsMessage(period: PeriodType, stats: MogakcoService.MyStatsView): String {
        val total = durationFormatter.toHourMinute(stats.totalSeconds)
        val attendanceTargetSeconds = stats.activeMinutesThreshold.toLong() * 60L
        val attendanceRate = if (attendanceTargetSeconds <= 0) 0.0 else stats.totalSeconds.toDouble() / attendanceTargetSeconds.toDouble()
        val oneHourRate = stats.totalSeconds.toDouble() / ONE_HOUR_SECONDS.toDouble()

        if (period == PeriodType.DAY) {
            return buildString {
                appendLine("오늘 내 모각코 통계 📈")
                appendLine("⏱ 오늘 누적시간: $total")
                appendLine(
                    "✅ 출석체크(${stats.activeMinutesThreshold}분): ${remainingLine(stats.totalSeconds, attendanceTargetSeconds)} " +
                        progressBar(attendanceRate, 10),
                )
                append("🎯 1시간 목표: ${remainingLine(stats.totalSeconds, ONE_HOUR_SECONDS)} ${progressBar(oneHourRate, 10)}")
            }
        }

        return buildString {
            appendLine("${periodLabel(period)} 내 모각코 통계 📈")
            appendLine("⏱ 누적시간: $total")
            appendLine("📅 참여일수: ${stats.activeDays}/${stats.totalDays}일 (기준 ${stats.activeMinutesThreshold}분)")
            append("📊 참여율: ${formatPercent(stats.participationRate)} ${progressBar(stats.participationRate, 10)}")
        }
    }

    private fun remainingLine(currentSeconds: Long, targetSeconds: Long): String {
        if (targetSeconds <= 0L) {
            return "완료 ✅"
        }
        val remainingSeconds = targetSeconds - currentSeconds
        if (remainingSeconds <= 0L) {
            return "완료 ✅"
        }
        return "남은시간 ${durationFormatter.toHourMinute(remainingSeconds)}"
    }

    private fun periodLabel(periodType: PeriodType): String = when (periodType) {
        PeriodType.DAY -> "오늘"
        PeriodType.WEEK -> "이번 주"
        PeriodType.MONTH -> "이번 달"
    }

    private fun parsePeriod(raw: String?): PeriodType? {
        val normalized = raw?.lowercase()
        if (normalized == "day" || normalized == "today" || normalized == "일간" || normalized == "오늘") {
            return PeriodType.DAY
        }
        if (normalized == "week" || normalized == "주간") {
            return PeriodType.WEEK
        }
        if (normalized == "month" || normalized == "월간") {
            return PeriodType.MONTH
        }
        return null
    }

    private fun formatPercent(rate: Double): String = String.format(Locale.US, "%.1f%%", rate * 100.0)

    private fun progressBar(rate: Double, size: Int): String {
        val clamped = rate.coerceIn(0.0, 1.0)
        val filled = (clamped * size).toInt()
        val empty = size - filled
        return FILLED_BAR.repeat(filled) + EMPTY_BAR.repeat(empty)
    }

    private fun medalForIndex(index: Int): String = rankMedalMap[index] ?: "🏅"

    private fun isSubcommand(
        event: SlashCommandInteractionEvent,
        ko: String,
        en: String,
    ): Boolean = event.subcommandName == ko || event.subcommandName == en

    private fun isSubcommandGroup(
        event: SlashCommandInteractionEvent,
        ko: String,
        en: String,
    ): Boolean = event.subcommandGroup == ko || event.subcommandGroup == en

    private fun resolveChannelOption(event: SlashCommandInteractionEvent) =
        event.getOption(OPTION_CHANNEL_KO)?.asChannel
            ?: event.getOption(OPTION_CHANNEL_LEGACY_KO)?.asChannel
            ?: event.getOption(OPTION_CHANNEL_EN)?.asChannel

    private fun replyUnsupported(event: SlashCommandInteractionEvent) {
        replyInvalidInputError(event, "지원하지 않는 하위 명령입니다.")
    }

    private fun replyGuildOnlyError(event: SlashCommandInteractionEvent) {
        discordReplyFactory.invalidInput(event, "길드에서만 사용할 수 있습니다.")
    }

    private fun isBlockedByHomeChannelGuard(event: SlashCommandInteractionEvent, guildId: Long): Boolean {
        val mogakcoChannelId = guildConfigService.getBoardChannels(guildId).mogakcoChannelId
        val guardResult = homeChannelGuard.validate(
            guildId = guildId,
            currentChannelId = event.channel.idLong,
            featureChannelId = mogakcoChannelId,
            featureName = "모각코",
            setupCommand = "/설정 마법사 모각코채널:#모각코",
            usageCommand = "/모각코 오늘",
        )
        if (guardResult is HomeChannelGuard.GuardResult.Allowed) {
            return false
        }
        val blocked = guardResult as HomeChannelGuard.GuardResult.Blocked
        replyInvalidInputError(event, blocked.message)
        return true
    }

    private fun replyInvalidInputError(event: SlashCommandInteractionEvent, message: String) {
        discordReplyFactory.invalidInput(event, message)
    }

    private fun replyAccessDeniedError(event: SlashCommandInteractionEvent) {
        discordReplyFactory.accessDenied(event, "이 명령은 운영진만 사용할 수 있습니다.")
    }

    private fun replyResourceNotFoundError(event: SlashCommandInteractionEvent, message: String, ephemeral: Boolean) {
        discordReplyFactory.resourceNotFound(event, message, ephemeral)
    }

    companion object {
        private const val FILLED_BAR = "🟩"
        private const val EMPTY_BAR = "⬜"
        private val rankMedalMap = mapOf(
            0 to "🥇",
            1 to "🥈",
            2 to "🥉",
        )
        private const val COMMAND_NAME_KO = "모각코"
        private const val COMMAND_NAME_EN = "mogakco"
        private const val SUBCOMMAND_GROUP_CHANNEL_KO = "채널"
        private const val SUBCOMMAND_GROUP_CHANNEL_EN = "channel"
        private const val SUBCOMMAND_CHANNEL_ADD_KO = "등록"
        private const val SUBCOMMAND_CHANNEL_ADD_EN = "add"
        private const val SUBCOMMAND_CHANNEL_REMOVE_KO = "해제"
        private const val SUBCOMMAND_CHANNEL_REMOVE_EN = "remove"
        private const val SUBCOMMAND_CHANNEL_LIST_KO = "목록"
        private const val SUBCOMMAND_CHANNEL_LIST_EN = "list"
        private const val SUBCOMMAND_LEADERBOARD_KO = "랭킹"
        private const val SUBCOMMAND_LEADERBOARD_EN = "leaderboard"
        private const val SUBCOMMAND_ME_KO = "내정보"
        private const val SUBCOMMAND_ME_EN = "me"
        private const val SUBCOMMAND_TODAY_KO = "오늘"
        private const val SUBCOMMAND_TODAY_EN = "today"
        private const val OPTION_CHANNEL_KO = "채널"
        private const val OPTION_CHANNEL_LEGACY_KO = "음성채널"
        private const val OPTION_CHANNEL_EN = "channel"
        private const val OPTION_PERIOD_KO = "기간"
        private const val OPTION_PERIOD_EN = "period"
        private const val OPTION_TOP_KO = "인원"
        private const val OPTION_TOP_EN = "top"
        private const val ONE_HOUR_SECONDS = 3600L
    }
}
