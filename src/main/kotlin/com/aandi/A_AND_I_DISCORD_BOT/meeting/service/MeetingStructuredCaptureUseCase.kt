package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.common.log.StructuredLog
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.structured.service.MeetingStructuredItemService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingStructuredCaptureUseCase(
    private val meetingSessionRepository: MeetingSessionRepository,
    private val meetingStructuredItemService: MeetingStructuredItemService,
    private val meetingThreadGateway: MeetingThreadGateway,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun captureDecision(
        guildId: Long,
        requestedBy: Long,
        fallbackThreadId: Long?,
        content: String,
    ): MeetingService.StructuredCaptureResult {
        val resolution = resolveActiveSession(guildId, fallbackThreadId)
        if (resolution.threadExistsButNotActive) {
            return MeetingService.StructuredCaptureResult.MeetingNotActive
        }
        val session = resolution.session ?: return MeetingService.StructuredCaptureResult.SessionNotFound
        val sessionId = session.id ?: return MeetingService.StructuredCaptureResult.SessionNotFound
        val thread = meetingThreadGateway.findThreadChannel(session.threadId)
            ?: return MeetingService.StructuredCaptureResult.ThreadNotFound(session.threadId)

        val saved = meetingStructuredItemService.addDecision(
            meetingSessionId = sessionId,
            guildId = session.guildId,
            threadId = session.threadId,
            content = content,
            createdBy = requestedBy,
        )
        val itemId = saved.id ?: return MeetingService.StructuredCaptureResult.SessionNotFound

        val notice = "🧾 결정 기록: ${saved.content.trim()}"
        meetingThreadGateway.postStructuredCaptureNotice(thread, notice)

        log.info(
            StructuredLog.event(
                name = "meeting.capture.decision",
                "guildId" to guildId,
                "threadId" to session.threadId,
                "sessionId" to session.id,
                "itemId" to saved.id,
                "requestedBy" to requestedBy,
            ),
        )

        return MeetingService.StructuredCaptureResult.Success(
            sessionId = sessionId,
            threadId = session.threadId,
            itemId = itemId,
            type = MeetingService.StructuredCaptureType.DECISION,
            summaryLine = saved.content.trim(),
        )
    }

    fun captureAction(
        guildId: Long,
        requestedBy: Long,
        fallbackThreadId: Long?,
        content: String,
        assigneeUserId: Long?,
        dueDateLocal: LocalDate?,
    ): MeetingService.StructuredCaptureResult {
        val resolution = resolveActiveSession(guildId, fallbackThreadId)
        if (resolution.threadExistsButNotActive) {
            return MeetingService.StructuredCaptureResult.MeetingNotActive
        }
        val session = resolution.session ?: return MeetingService.StructuredCaptureResult.SessionNotFound
        val sessionId = session.id ?: return MeetingService.StructuredCaptureResult.SessionNotFound
        val thread = meetingThreadGateway.findThreadChannel(session.threadId)
            ?: return MeetingService.StructuredCaptureResult.ThreadNotFound(session.threadId)

        val saved = meetingStructuredItemService.addAction(
            meetingSessionId = sessionId,
            guildId = session.guildId,
            threadId = session.threadId,
            content = content,
            assigneeUserId = assigneeUserId,
            dueDateLocal = dueDateLocal,
            createdBy = requestedBy,
        )
        val itemId = saved.id ?: return MeetingService.StructuredCaptureResult.SessionNotFound

        val summaryLine = meetingStructuredItemService.formatActionLine(
            content = saved.content,
            assigneeUserId = saved.assigneeUserId,
            dueDateLocal = saved.dueDateLocal,
        )
        meetingThreadGateway.postStructuredCaptureNotice(thread, "📝 액션 기록: $summaryLine")

        log.info(
            StructuredLog.event(
                name = "meeting.capture.action",
                "guildId" to guildId,
                "threadId" to session.threadId,
                "sessionId" to session.id,
                "itemId" to saved.id,
                "requestedBy" to requestedBy,
                "assigneeUserId" to assigneeUserId,
                "dueDate" to dueDateLocal,
            ),
        )

        return MeetingService.StructuredCaptureResult.Success(
            sessionId = sessionId,
            threadId = session.threadId,
            itemId = itemId,
            type = MeetingService.StructuredCaptureType.ACTION,
            summaryLine = summaryLine,
        )
    }

    private fun resolveActiveSession(guildId: Long, fallbackThreadId: Long?): SessionResolution {
        if (fallbackThreadId != null) {
            val threadSession = meetingSessionRepository.findByGuildIdAndThreadId(guildId, fallbackThreadId)
            if (threadSession != null) {
                if (threadSession.status == MeetingSessionStatus.ACTIVE) {
                    return SessionResolution(session = threadSession, threadExistsButNotActive = false)
                }
                return SessionResolution(session = null, threadExistsButNotActive = true)
            }
        }

        return SessionResolution(
            session = meetingSessionRepository.findFirstByGuildIdAndStatusOrderByStartedAtDesc(
                guildId,
                MeetingSessionStatus.ACTIVE,
            ),
            threadExistsButNotActive = false,
        )
    }

    private data class SessionResolution(
        val session: MeetingSessionEntity?,
        val threadExistsButNotActive: Boolean,
    )
}
