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
        if (event.name != "mogakco") {
            return
        }

        if (event.subcommandGroup == "channel") {
            routeChannelCommand(event)
            return
        }

        if (event.subcommandName == "leaderboard") {
            handleLeaderboard(event)
            return
        }
        if (event.subcommandName == "me") {
            handleMe(event)
            return
        }

        replyUnsupported(event)
    }

    private fun routeChannelCommand(event: SlashCommandInteractionEvent) {
        if (event.subcommandName == "add") {
            handleChannelAdd(event)
            return
        }
        if (event.subcommandName == "remove") {
            handleChannelRemove(event)
            return
        }
        if (event.subcommandName == "list") {
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

        val channel = event.getOption("channel")?.asChannel
        if (channel == null) {
            replyInvalidInputError(event, "channel 옵션이 필요합니다.")
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

        val channel = event.getOption("channel")?.asChannel
        if (channel == null) {
            replyInvalidInputError(event, "channel 옵션이 필요합니다.")
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

        val period = parsePeriod(event.getOption("period")?.asString)
        if (period == null) {
            replyInvalidInputError(event, "period는 week 또는 month만 가능합니다.")
            return
        }

        val top = event.getOption("top")?.asInt ?: 10
        val leaderboard = mogakcoService.getLeaderboard(guild.idLong, period, top)
        if (leaderboard.entries.isEmpty()) {
            replyResourceNotFoundError(event, "기록이 없습니다.", false)
            return
        }

        val rows = leaderboard.entries.mapIndexed { index, entry ->
            "${index + 1}. <@${entry.userId}> - ${durationFormatter.toHourMinute(entry.totalSeconds)}"
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

        val period = parsePeriod(event.getOption("period")?.asString)
        if (period == null) {
            replyInvalidInputError(event, "period는 week 또는 month만 가능합니다.")
            return
        }

        val stats = mogakcoService.getMyStats(
            guildId = guild.idLong,
            userId = member.idLong,
            period = period,
        )

        val message = buildString {
            appendLine("${periodLabel(period)} 내 모각코 통계")
            appendLine("- 누적시간: ${durationFormatter.toHourMinute(stats.totalSeconds)}")
            appendLine("- 참여일수: ${stats.activeDays}/${stats.totalDays}일 (기준 ${stats.activeMinutesThreshold}분)")
            append("- 참여율: ${formatPercent(stats.participationRate)}")
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
}
