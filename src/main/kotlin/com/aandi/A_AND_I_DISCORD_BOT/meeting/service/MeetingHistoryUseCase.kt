package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.AgendaLinkRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.service.MeetingSummaryArtifactService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingHistoryUseCase(
    private val meetingSessionRepository: MeetingSessionRepository,
    private val meetingSummaryArtifactService: MeetingSummaryArtifactService,
    private val agendaLinkRepository: AgendaLinkRepository,
    private val clock: Clock,
) {

    fun listHistory(
        guildId: Long,
        days: Int,
        statusFilter: MeetingService.HistoryStatusFilter,
    ): MeetingService.HistoryResult {
        if (days !in HISTORY_DAYS_MIN..HISTORY_DAYS_MAX) {
            return MeetingService.HistoryResult.InvalidDays
        }
        val sessions = fetchSessions(guildId, statusFilter)
        if (sessions.isEmpty()) {
            return MeetingService.HistoryResult.Success(emptyList())
        }
        val cutoff = Instant.now(clock).minus(days.toLong(), ChronoUnit.DAYS)
        val views = sessions
            .asSequence()
            .filter { it.startedAt >= cutoff }
            .mapNotNull { session ->
                val sessionId = session.id ?: return@mapNotNull null
                val latestArtifact = meetingSummaryArtifactService.findLatestByMeetingSessionId(sessionId)
                MeetingService.HistoryMeetingView(
                    sessionId = sessionId,
                    status = session.status,
                    boardChannelId = session.boardChannelId,
                    threadId = session.threadId,
                    startedBy = session.startedBy,
                    startedAt = session.startedAt,
                    endedAt = session.endedAt,
                    summaryMessageId = session.summaryMessageId,
                    decisionCount = latestArtifact?.decisionCount ?: 0,
                    actionCount = latestArtifact?.actionCount ?: 0,
                    todoCount = latestArtifact?.todoCount ?: 0,
                )
            }
            .toList()
        return MeetingService.HistoryResult.Success(views)
    }

    fun detail(
        guildId: Long,
        meetingSessionId: Long,
    ): MeetingService.MeetingDetailResult {
        val session = meetingSessionRepository.findByIdAndGuildId(meetingSessionId, guildId)
            ?: return MeetingService.MeetingDetailResult.NotFound
        val artifact = meetingSummaryArtifactService.findLatestByMeetingSessionId(meetingSessionId)
        val summary = artifact?.let { meetingSummaryArtifactService.toSummary(it) }
        val agenda = session.agendaLinkId?.let { agendaLinkRepository.findById(it).orElse(null) }
        return MeetingService.MeetingDetailResult.Success(
            detail = MeetingService.MeetingDetailView(
                sessionId = meetingSessionId,
                status = session.status,
                boardChannelId = session.boardChannelId,
                threadId = session.threadId,
                startedBy = session.startedBy,
                startedAt = session.startedAt,
                endedBy = session.endedBy,
                endedAt = session.endedAt,
                summaryMessageId = session.summaryMessageId,
                agendaTitle = agenda?.title,
                agendaUrl = agenda?.url,
                decisionCount = artifact?.decisionCount ?: 0,
                actionCount = artifact?.actionCount ?: 0,
                todoCount = artifact?.todoCount ?: 0,
                decisions = summary?.decisions ?: emptyList(),
                actions = summary?.actionItems ?: emptyList(),
                todos = summary?.todos ?: emptyList(),
            ),
        )
    }

    private fun fetchSessions(
        guildId: Long,
        statusFilter: MeetingService.HistoryStatusFilter,
    ) = when (statusFilter) {
        MeetingService.HistoryStatusFilter.ALL ->
            meetingSessionRepository.findTop50ByGuildIdOrderByStartedAtDesc(guildId)

        MeetingService.HistoryStatusFilter.ACTIVE ->
            meetingSessionRepository.findTop50ByGuildIdAndStatusOrderByStartedAtDesc(
                guildId = guildId,
                status = MeetingSessionStatus.ACTIVE,
            )

        MeetingService.HistoryStatusFilter.ENDED ->
            meetingSessionRepository.findTop50ByGuildIdAndStatusOrderByStartedAtDesc(
                guildId = guildId,
                status = MeetingSessionStatus.ENDED,
            )
    }

    companion object {
        private const val HISTORY_DAYS_MIN = 1
        private const val HISTORY_DAYS_MAX = 90
    }
}
