package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.AgendaLinkRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.domain.MeetingSessionStateMachine
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.MeetingSummaryExtractor
import net.dv8tion.jda.api.entities.Message
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingEndUseCase(
    private val agendaLinkRepository: AgendaLinkRepository,
    private val meetingSessionRepository: MeetingSessionRepository,
    private val meetingSessionStateMachine: MeetingSessionStateMachine,
    private val meetingSummaryExtractor: MeetingSummaryExtractor,
    private val meetingThreadGateway: MeetingThreadGateway,
    private val clock: Clock,
) {

    fun endMeeting(
        guildId: Long,
        requestedBy: Long,
        fallbackThreadId: Long?,
        requestedThreadId: Long?,
    ): MeetingService.EndResult {
        val session = resolveSessionForEnd(guildId, fallbackThreadId, requestedThreadId)
            ?: return MeetingService.EndResult.SessionNotFound
        val transition = meetingSessionStateMachine.end(session.status)
        if (transition is MeetingSessionStateMachine.Transition.Rejected) {
            return MeetingService.EndResult.AlreadyEnded
        }

        val thread = meetingThreadGateway.findThreadChannel(session.threadId)
            ?: return MeetingService.EndResult.ThreadNotFound(session.threadId)
        val messages = meetingThreadGateway.collectMessages(thread, MAX_SUMMARY_MESSAGES)
        val summaryInput = messages.map {
            MeetingSummaryExtractor.MeetingMessage(
                authorId = it.author.idLong,
                content = resolveSummaryContent(it),
                createdAt = it.timeCreated.toInstant(),
            )
        }
        val summary = meetingSummaryExtractor.extract(summaryInput)
        val summaryMessage = meetingThreadGateway.postSummary(thread, summary, messages.size)

        val nowUtc = Instant.now(clock)
        session.status = MeetingSessionStatus.ENDED
        session.endedBy = requestedBy
        session.endedAt = nowUtc
        session.summaryMessageId = summaryMessage.idLong
        session.updatedAt = nowUtc
        meetingSessionRepository.save(session)
        val sessionId = session.id ?: return MeetingService.EndResult.SessionNotFound
        val linkedAgenda = resolveLinkedAgenda(session)
        meetingThreadGateway.postEndedMessage(thread)
        val archived = meetingThreadGateway.archiveThread(thread)

        return MeetingService.EndResult.Success(
            sessionId = sessionId,
            threadId = thread.idLong,
            summaryMessageId = summaryMessage.idLong,
            sourceMessageCount = messages.size,
            agendaTitle = linkedAgenda?.title,
            agendaUrl = linkedAgenda?.url,
            decisions = summary.decisions,
            actionItems = summary.actionItems,
            archived = archived,
        )
    }

    private fun resolveSessionForEnd(
        guildId: Long,
        fallbackThreadId: Long?,
        requestedThreadId: Long?,
    ): MeetingSessionEntity? {
        if (requestedThreadId != null) {
            return meetingSessionRepository.findByGuildIdAndThreadId(guildId, requestedThreadId)
        }
        if (fallbackThreadId != null) {
            return meetingSessionRepository.findByGuildIdAndThreadId(guildId, fallbackThreadId)
        }
        return meetingSessionRepository.findFirstByGuildIdAndStatusOrderByStartedAtDesc(
            guildId,
            MeetingSessionStatus.ACTIVE,
        )
    }

    private fun resolveSummaryContent(message: Message): String {
        val display = message.contentDisplay.trim()
        if (display.isNotBlank()) {
            return display
        }
        return message.contentRaw.trim()
    }

    private fun resolveLinkedAgenda(session: MeetingSessionEntity) =
        session.agendaLinkId?.let { agendaLinkRepository.findById(it).orElse(null) }

    companion object {
        private const val MAX_SUMMARY_MESSAGES = 200
    }
}
