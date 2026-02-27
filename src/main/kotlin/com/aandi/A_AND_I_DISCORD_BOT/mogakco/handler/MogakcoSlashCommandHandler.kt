package com.aandi.A_AND_I_DISCORD_BOT.mogakco.handler

import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorCode
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorResponse
import com.aandi.A_AND_I_DISCORD_BOT.common.format.DurationFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodType
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.MogakcoService
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class MogakcoSlashCommandHandler(
    private val mogakcoService: MogakcoService,
    private val durationFormatter: DurationFormatter,
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

        val channel = event.getOption(OPTION_CHANNEL_KO)?.asChannel ?: event.getOption(OPTION_CHANNEL_EN)?.asChannel
        if (channel == null) {
            replyInvalidInputError(event, "음성채널 옵션이 필요합니다.")
            return
        }

        val result = mogakcoService.addChannel(
            guildId = guild.idLong,
            requesterRoleIds = member.roles.map { it.idLong }.toSet(),
            hasManageServerPermission = hasManageServerPermission(member),
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

        val channel = event.getOption(OPTION_CHANNEL_KO)?.asChannel ?: event.getOption(OPTION_CHANNEL_EN)?.asChannel
        if (channel == null) {
            replyInvalidInputError(event, "음성채널 옵션이 필요합니다.")
            return
        }

        val result = mogakcoService.removeChannel(
            guildId = guild.idLong,
            requesterRoleIds = member.roles.map { it.idLong }.toSet(),
            hasManageServerPermission = hasManageServerPermission(member),
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
            hasManageServerPermission = hasManageServerPermission(member),
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

        val period = parsePeriod(event.getOption(OPTION_PERIOD_KO)?.asString ?: event.getOption(OPTION_PERIOD_EN)?.asString)
        if (period == null) {
            replyInvalidInputError(event, "기간은 week 또는 month만 가능합니다.")
            return
        }

        val top = event.getOption(OPTION_TOP_KO)?.asInt ?: event.getOption(OPTION_TOP_EN)?.asInt ?: 10
        val leaderboard = mogakcoService.getLeaderboard(guild.idLong, period, top)
        if (leaderboard.entries.isEmpty()) {
            replyResourceNotFoundError(event, "기록이 없습니다.", false)
            return
        }

        val rows = leaderboard.entries.mapIndexed { index, entry ->
            val medal = when (index) {
                0 -> "🥇"
                1 -> "🥈"
                2 -> "🥉"
                else -> "🏅"
            }
            val maxSeconds = leaderboard.entries.first().totalSeconds.coerceAtLeast(1L)
            val bar = progressBar(entry.totalSeconds.toDouble() / maxSeconds.toDouble(), 8)
            "$medal <@${entry.userId}> - ${durationFormatter.toHourMinute(entry.totalSeconds)} $bar"
        }

        event.reply("${periodLabel(period)} 모각코 랭킹\n${rows.joinToString(separator = "\\n")}")
            .queue()
    }

    private fun handleMe(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            replyGuildOnlyError(event)
            return
        }

        val period = parsePeriod(event.getOption(OPTION_PERIOD_KO)?.asString ?: event.getOption(OPTION_PERIOD_EN)?.asString)
        if (period == null) {
            replyInvalidInputError(event, "기간은 week 또는 month만 가능합니다.")
            return
        }

        val stats = mogakcoService.getMyStats(
            guildId = guild.idLong,
            userId = member.idLong,
            period = period,
        )

        val message = buildString {
            appendLine("${periodLabel(period)} 내 모각코 통계 📈")
            appendLine("⏱ 누적시간: ${durationFormatter.toHourMinute(stats.totalSeconds)}")
            appendLine("📅 참여일수: ${stats.activeDays}/${stats.totalDays}일 (기준 ${stats.activeMinutesThreshold}분)")
            append("📊 참여율: ${formatPercent(stats.participationRate)} ${progressBar(stats.participationRate, 10)}")
        }

        event.reply(message)
            .setEphemeral(true)
            .queue()
    }

    private fun periodLabel(periodType: PeriodType): String = when (periodType) {
        PeriodType.WEEK -> "이번 주"
        PeriodType.MONTH -> "이번 달"
    }

    private fun parsePeriod(raw: String?): PeriodType? {
        val normalized = raw?.lowercase()
        if (normalized == "week") {
            return PeriodType.WEEK
        }
        if (normalized == "month") {
            return PeriodType.MONTH
        }
        return null
    }

    private fun formatPercent(rate: Double): String = String.format(Locale.US, "%.1f%%", rate * 100.0)

    private fun progressBar(rate: Double, size: Int): String {
        val clamped = rate.coerceIn(0.0, 1.0)
        val filled = (clamped * size).toInt()
        val empty = size - filled
        return "▓".repeat(filled) + "░".repeat(empty)
    }

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

    private fun hasManageServerPermission(member: net.dv8tion.jda.api.entities.Member): Boolean {
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true
        }
        return member.hasPermission(Permission.MANAGE_SERVER)
    }

    private fun replyUnsupported(event: SlashCommandInteractionEvent) {
        replyInvalidInputError(event, "지원하지 않는 하위 명령입니다.")
    }

    private fun replyGuildOnlyError(event: SlashCommandInteractionEvent) {
        replyError(
            event = event,
            code = DiscordErrorCode.COMMON_INVALID_INPUT,
            message = "길드에서만 사용할 수 있습니다.",
            retryable = false,
            ephemeral = true,
        )
    }

    private fun replyInvalidInputError(event: SlashCommandInteractionEvent, message: String) {
        replyError(
            event = event,
            code = DiscordErrorCode.COMMON_INVALID_INPUT,
            message = message,
            retryable = false,
            ephemeral = true,
        )
    }

    private fun replyAccessDeniedError(event: SlashCommandInteractionEvent) {
        replyError(
            event = event,
            code = DiscordErrorCode.ACCESS_DENIED,
            message = "이 명령은 운영진만 사용할 수 있습니다.",
            retryable = false,
            ephemeral = true,
        )
    }

    private fun replyResourceNotFoundError(event: SlashCommandInteractionEvent, message: String, ephemeral: Boolean) {
        replyError(
            event = event,
            code = DiscordErrorCode.RESOURCE_NOT_FOUND,
            message = message,
            retryable = false,
            ephemeral = ephemeral,
        )
    }

    private fun replyError(
        event: SlashCommandInteractionEvent,
        code: DiscordErrorCode,
        message: String,
        retryable: Boolean,
        ephemeral: Boolean,
    ) {
        val payload = DiscordErrorFormatter.format(
            DiscordErrorResponse(
                code = code,
                message = message,
                retryable = retryable,
            ),
        )
        event.reply(payload)
            .setEphemeral(ephemeral)
            .queue()
    }

    companion object {
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
        private const val OPTION_CHANNEL_KO = "음성채널"
        private const val OPTION_CHANNEL_EN = "channel"
        private const val OPTION_PERIOD_KO = "기간"
        private const val OPTION_PERIOD_EN = "period"
        private const val OPTION_TOP_KO = "인원"
        private const val OPTION_TOP_EN = "top"
    }
}
