package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.meeting.domain.MeetingSessionStateMachine
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.MeetingSummaryExtractor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingEndUseCase(
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
                content = it.contentDisplay,
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
        meetingThreadGateway.postEndedMessage(thread)
        meetingThreadGateway.archiveThread(thread)

        return MeetingService.EndResult.Success(
            threadId = thread.idLong,
            summaryMessageId = summaryMessage.idLong,
            decisions = summary.decisions,
            actionItems = summary.actionItems,
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

    companion object {
        private const val MAX_SUMMARY_MESSAGES = 200
    }
}
