package com.aandi.A_AND_I_DISCORD_BOT.discord

import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.MogakcoService
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class MogakcoSlashCommandListener(
    private val mogakcoService: MogakcoService,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != "mogakco") {
            return
        }

        if (event.subcommandGroup == "channel") {
            when (event.subcommandName) {
                "add" -> handleChannelAdd(event)
                "remove" -> handleChannelRemove(event)
                else -> replyUnsupported(event)
            }
            return
        }

        when (event.subcommandName) {
            "leaderboard" -> handleLeaderboard(event)
            "me" -> handleMe(event)
            else -> replyUnsupported(event)
        }
    }

    private fun handleChannelAdd(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val channel = event.getOption("channel")?.asChannel
        if (channel == null) {
            event.reply("channel 옵션이 필요합니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val roleIds = member.roles.map { it.idLong }.toSet()
        when (mogakcoService.addChannel(guild.idLong, roleIds, channel.idLong)) {
            MogakcoService.ChannelUpdateResult.Added -> {
                event.reply("모각코 채널을 등록했습니다: <#${channel.idLong}>")
                    .setEphemeral(true)
                    .queue()
            }

            MogakcoService.ChannelUpdateResult.AlreadyExists -> {
                event.reply("이미 등록된 모각코 채널입니다.")
                    .setEphemeral(true)
                    .queue()
            }

            MogakcoService.ChannelUpdateResult.MissingGuildConfig -> {
                event.reply("guild_config가 없어 처리할 수 없습니다.")
                    .setEphemeral(true)
                    .queue()
            }

            MogakcoService.ChannelUpdateResult.AdminRoleNotConfigured -> {
                event.reply("guild_config.admin_role_id가 설정되지 않았습니다.")
                    .setEphemeral(true)
                    .queue()
            }

            MogakcoService.ChannelUpdateResult.Forbidden -> {
                event.reply("운영진만 사용할 수 있습니다.")
                    .setEphemeral(true)
                    .queue()
            }

            else -> {
                event.reply("처리 중 오류가 발생했습니다.")
                    .setEphemeral(true)
                    .queue()
            }
        }
    }

    private fun handleChannelRemove(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val channel = event.getOption("channel")?.asChannel
        if (channel == null) {
            event.reply("channel 옵션이 필요합니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val roleIds = member.roles.map { it.idLong }.toSet()
        when (mogakcoService.removeChannel(guild.idLong, roleIds, channel.idLong)) {
            MogakcoService.ChannelUpdateResult.Removed -> {
                event.reply("모각코 채널 등록을 해제했습니다: <#${channel.idLong}>")
                    .setEphemeral(true)
                    .queue()
            }

            MogakcoService.ChannelUpdateResult.NotFound -> {
                event.reply("등록된 모각코 채널이 아닙니다.")
                    .setEphemeral(true)
                    .queue()
            }

            MogakcoService.ChannelUpdateResult.MissingGuildConfig -> {
                event.reply("guild_config가 없어 처리할 수 없습니다.")
                    .setEphemeral(true)
                    .queue()
            }

            MogakcoService.ChannelUpdateResult.AdminRoleNotConfigured -> {
                event.reply("guild_config.admin_role_id가 설정되지 않았습니다.")
                    .setEphemeral(true)
                    .queue()
            }

            MogakcoService.ChannelUpdateResult.Forbidden -> {
                event.reply("운영진만 사용할 수 있습니다.")
                    .setEphemeral(true)
                    .queue()
            }

            else -> {
                event.reply("처리 중 오류가 발생했습니다.")
                    .setEphemeral(true)
                    .queue()
            }
        }
    }

    private fun handleLeaderboard(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (guild == null) {
            event.reply("길드에서만 사용할 수 있습니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val period = parsePeriod(event.getOption("period")?.asString)
        if (period == null) {
            event.reply("period는 week 또는 month만 가능합니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val top = event.getOption("top")?.asInt ?: 10
        val leaderboard = mogakcoService.getLeaderboard(guild.idLong, period, top)
        if (leaderboard.entries.isEmpty()) {
            event.reply("아직 모각코 기록이 없습니다.")
                .queue()
            return
        }

        val title = when (leaderboard.periodType) {
            MogakcoService.PeriodType.WEEK -> "이번 주 모각코 랭킹"
            MogakcoService.PeriodType.MONTH -> "이번 달 모각코 랭킹"
        }
        val rows = leaderboard.entries.mapIndexed { index, entry ->
            "${index + 1}. <@${entry.userId}> - ${formatDuration(entry.totalSeconds)}"
        }
        event.reply("$title\n${rows.joinToString(separator = "\n")}")
            .queue()
    }

    private fun handleMe(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("길드에서만 사용할 수 있습니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val period = parsePeriod(event.getOption("period")?.asString)
        if (period == null) {
            event.reply("period는 week 또는 month만 가능합니다.")
                .setEphemeral(true)
                .queue()
            return
        }

        val stats = mogakcoService.getMyStats(
            guildId = guild.idLong,
            userId = member.idLong,
            period = period,
        )
        val periodLabel = when (stats.periodType) {
            MogakcoService.PeriodType.WEEK -> "이번 주"
            MogakcoService.PeriodType.MONTH -> "이번 달"
        }
        val message = buildString {
            appendLine("$periodLabel 내 모각코 통계")
            appendLine("- 누적시간: ${formatDuration(stats.totalSeconds)}")
            appendLine("- 참여일수: ${stats.activeDays}/${stats.totalDays}일 (기준 ${stats.activeMinutesThreshold}분)")
            append("- 참여율: ${formatPercent(stats.participationRate)}")
        }
        event.reply(message)
            .setEphemeral(true)
            .queue()
    }

    private fun parsePeriod(raw: String?): MogakcoService.PeriodType? = when (raw?.lowercase()) {
        "week" -> MogakcoService.PeriodType.WEEK
        "month" -> MogakcoService.PeriodType.MONTH
        else -> null
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return "${hours}시간 ${minutes}분"
    }

    private fun formatPercent(rate: Double): String =
        String.format(Locale.US, "%.1f%%", rate * 100.0)

    private fun replyUnsupported(event: SlashCommandInteractionEvent) {
        event.reply("지원하지 않는 하위 명령입니다.")
            .setEphemeral(true)
            .queue()
    }
}
