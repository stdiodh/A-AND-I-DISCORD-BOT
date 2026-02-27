package com.aandi.A_AND_I_DISCORD_BOT.dashboard.service

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.agenda.service.AgendaService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
import com.aandi.A_AND_I_DISCORD_BOT.common.format.DurationFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodType
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardRenderer
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.MogakcoService
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class HomeDashboardService(
    private val guildConfigService: GuildConfigService,
    private val agendaService: AgendaService,
    private val assignmentTaskService: AssignmentTaskService,
    private val mogakcoService: MogakcoService,
    private val durationFormatter: DurationFormatter,
    private val renderer: DashboardRenderer,
    private val jda: JDA,
) {

    @Transactional
    fun create(guildId: Long, guildName: String?, channelId: Long): Result {
        val channel = jda.getTextChannelById(channelId) ?: return Result.ChannelNotFound
        val bundle = loadBundle(guildId, guildName)
        val message = channel.sendMessageEmbeds(bundle.embed)
            .setComponents(bundle.components)
            .complete()

        val pinResult = pinIfPossible(channelId, message.idLong)
        guildConfigService.setDashboard(guildId, channel.idLong, message.idLong)
        return Result.Success(channel.idLong, message.idLong, pinResult)
    }

    @Transactional
    fun refresh(guildId: Long, guildName: String?): Result {
        val dashboard = guildConfigService.getDashboard(guildId)
        val channelId = dashboard.channelId ?: return Result.NotConfigured
        val messageId = dashboard.messageId ?: return Result.NotConfigured
        val channel = jda.getTextChannelById(channelId) ?: return Result.ChannelNotFound
        val message = runCatching { channel.retrieveMessageById(messageId).complete() }.getOrNull()
            ?: return Result.MessageNotFound

        val bundle = loadBundle(guildId, guildName)
        message.editMessageEmbeds(bundle.embed)
            .setComponents(bundle.components)
            .complete()

        return Result.Success(channelId, messageId, PinResult.SKIPPED)
    }

    private fun loadBundle(guildId: Long, guildName: String?): DashboardBundle {
        val agenda = agendaService.getTodayAgenda(guildId)
        val pendingCount = resolvePendingCount(guildId)
        val top3 = mogakcoService.getLeaderboard(guildId, PeriodType.WEEK, 3).entries
            .map {
                DashboardRenderer.MogakcoSummary(
                    userId = it.userId,
                    formattedDuration = durationFormatter.toHourMinute(it.totalSeconds),
                )
            }

        val view = renderer.render(
            DashboardRenderer.DashboardInput(
                guildName = guildName,
                todayAgenda = agenda?.let { DashboardRenderer.AgendaSummary(title = it.title, url = it.url) },
                pendingCount = pendingCount,
                weeklyTop3 = top3,
            ),
        )

        val embed = EmbedBuilder()
            .setTitle(view.title)
            .setDescription(view.overview)
            .addField("회의(안건 링크)", view.meetingSection, false)
            .addField("과제", view.assignmentSection, false)
            .addField("모각코", view.mogakcoSection, false)
            .setColor(Color(0x1F8B4C))
            .build()

        val components = mutableListOf<ActionRow>()
        components.add(
            ActionRow.of(
                Button.primary(DashboardActionIds.MEETING_START, "회의"),
                Button.secondary(DashboardActionIds.AGENDA_SET, "안건 설정"),
                Button.success(DashboardActionIds.ASSIGNMENT_CREATE, "과제 등록"),
            ),
        )
        components.add(
            ActionRow.of(
                Button.primary(DashboardActionIds.ASSIGNMENT_LIST, "과제 목록"),
                Button.primary(DashboardActionIds.MOGAKCO_RANK, "모각코 랭킹"),
                Button.primary(DashboardActionIds.MOGAKCO_ME, "내 기록"),
            ),
        )
        if (agenda != null) {
            components.add(ActionRow.of(Button.link(agenda.url, "오늘 안건 링크")))
        }

        return DashboardBundle(embed = embed, components = components)
    }

    private fun resolvePendingCount(guildId: Long): Int? {
        return when (val result = assignmentTaskService.list(guildId, "대기")) {
            is AssignmentTaskService.ListResult.Success -> result.tasks.size
            AssignmentTaskService.ListResult.InvalidStatus -> null
        }
    }

    private fun pinIfPossible(channelId: Long, messageId: Long): PinResult {
        val channel = jda.getTextChannelById(channelId) ?: return PinResult.FAILED
        val selfMember = channel.guild.selfMember
        if (!selfMember.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            return PinResult.NO_PERMISSION
        }

        val pinned = runCatching {
            channel.retrieveMessageById(messageId)
                .complete()
                .pin()
                .reason("A&I 홈 대시보드")
                .complete()
        }.isSuccess

        if (!pinned) {
            return PinResult.FAILED
        }
        return PinResult.PINNED
    }

    private data class DashboardBundle(
        val embed: net.dv8tion.jda.api.entities.MessageEmbed,
        val components: List<ActionRow>,
    )

    sealed interface Result {
        data class Success(
            val channelId: Long,
            val messageId: Long,
            val pinResult: PinResult,
        ) : Result

        data object NotConfigured : Result
        data object ChannelNotFound : Result
        data object MessageNotFound : Result
    }

    enum class PinResult {
        PINNED,
        NO_PERMISSION,
        FAILED,
        SKIPPED,
    }
}
