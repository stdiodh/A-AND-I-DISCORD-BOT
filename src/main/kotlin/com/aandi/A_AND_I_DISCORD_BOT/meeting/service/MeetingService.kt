package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingService(
    private val meetingStartUseCase: MeetingStartUseCase,
    private val meetingEndUseCase: MeetingEndUseCase,
    private val meetingStructuredCaptureUseCase: MeetingStructuredCaptureUseCase,
    private val meetingHistoryUseCase: MeetingHistoryUseCase,
) {

    @Transactional
    fun startMeeting(
        guildId: Long,
        requestedBy: Long,
        targetChannelId: Long?,
        fallbackChannelId: Long?,
        rawTitle: String?,
    ): StartResult {
        return meetingStartUseCase.startMeeting(
            guildId = guildId,
            requestedBy = requestedBy,
            targetChannelId = targetChannelId,
            fallbackChannelId = fallbackChannelId,
            rawTitle = rawTitle,
        )
    }

    @Transactional
    fun endMeeting(
        guildId: Long,
        requestedBy: Long,
        meetingSessionId: Long,
        progress: ((SummaryProgress) -> Unit)? = null,
    ): EndResult {
        return meetingEndUseCase.endMeeting(
            guildId = guildId,
            requestedBy = requestedBy,
            meetingSessionId = meetingSessionId,
            progress = progress,
        )
    }

    @Transactional
    fun regenerateSummary(
        guildId: Long,
        requestedBy: Long,
        meetingSessionId: Long,
        progress: ((SummaryProgress) -> Unit)? = null,
    ): SummaryMutationResult {
        return meetingEndUseCase.regenerateSummary(
            guildId = guildId,
            requestedBy = requestedBy,
            meetingSessionId = meetingSessionId,
            progress = progress,
        )
    }

    @Transactional
    fun addManualDecision(
        guildId: Long,
        requestedBy: Long,
        meetingSessionId: Long,
        decision: String,
    ): SummaryMutationResult {
        return meetingEndUseCase.addManualDecision(
            guildId = guildId,
            requestedBy = requestedBy,
            meetingSessionId = meetingSessionId,
            decision = decision,
        )
    }

    @Transactional
    fun addManualAction(
        guildId: Long,
        requestedBy: Long,
        meetingSessionId: Long,
        action: String,
    ): SummaryMutationResult {
        return meetingEndUseCase.addManualAction(
            guildId = guildId,
            requestedBy = requestedBy,
            meetingSessionId = meetingSessionId,
            action = action,
        )
    }

    @Transactional
    fun addManualTodo(
        guildId: Long,
        requestedBy: Long,
        meetingSessionId: Long,
        todo: String,
    ): SummaryMutationResult {
        return meetingEndUseCase.addManualTodo(
            guildId = guildId,
            requestedBy = requestedBy,
            meetingSessionId = meetingSessionId,
            todo = todo,
        )
    }

    @Transactional
    fun captureDecision(
        guildId: Long,
        requestedBy: Long,
        fallbackThreadId: Long?,
        requestedMeetingSessionId: Long?,
        content: String,
    ): StructuredCaptureResult {
        return meetingStructuredCaptureUseCase.captureDecision(
            guildId = guildId,
            requestedBy = requestedBy,
            fallbackThreadId = fallbackThreadId,
            requestedMeetingSessionId = requestedMeetingSessionId,
            content = content,
        )
    }

    @Transactional
    fun captureAction(
        guildId: Long,
        requestedBy: Long,
        fallbackThreadId: Long?,
        requestedMeetingSessionId: Long?,
        content: String,
        assigneeUserId: Long?,
        dueDateLocal: java.time.LocalDate?,
    ): StructuredCaptureResult {
        return meetingStructuredCaptureUseCase.captureAction(
            guildId = guildId,
            requestedBy = requestedBy,
            fallbackThreadId = fallbackThreadId,
            requestedMeetingSessionId = requestedMeetingSessionId,
            content = content,
            assigneeUserId = assigneeUserId,
            dueDateLocal = dueDateLocal,
        )
    }

    @Transactional
    fun captureTodo(
        guildId: Long,
        requestedBy: Long,
        fallbackThreadId: Long?,
        requestedMeetingSessionId: Long?,
        content: String,
    ): StructuredCaptureResult {
        return meetingStructuredCaptureUseCase.captureTodo(
            guildId = guildId,
            requestedBy = requestedBy,
            fallbackThreadId = fallbackThreadId,
            requestedMeetingSessionId = requestedMeetingSessionId,
            content = content,
        )
    }

    @Transactional(readOnly = true)
    fun listStructuredItems(
        guildId: Long,
        fallbackThreadId: Long?,
        requestedMeetingSessionId: Long?,
    ): StructuredListResult {
        return meetingStructuredCaptureUseCase.listItems(
            guildId = guildId,
            fallbackThreadId = fallbackThreadId,
            requestedMeetingSessionId = requestedMeetingSessionId,
        )
    }

    @Transactional
    fun cancelStructuredItem(
        guildId: Long,
        requestedBy: Long,
        fallbackThreadId: Long?,
        requestedMeetingSessionId: Long?,
        itemId: Long,
    ): StructuredCancelResult {
        return meetingStructuredCaptureUseCase.cancelItem(
            guildId = guildId,
            requestedBy = requestedBy,
            fallbackThreadId = fallbackThreadId,
            requestedMeetingSessionId = requestedMeetingSessionId,
            itemId = itemId,
        )
    }

    @Transactional(readOnly = true)
    fun listActiveMeetings(guildId: Long): ActiveMeetingsResult {
        return meetingStartUseCase.listActiveMeetings(guildId)
    }

    @Transactional(readOnly = true)
    fun listMeetingHistory(
        guildId: Long,
        days: Int,
        statusFilter: HistoryStatusFilter,
    ): HistoryResult {
        return meetingHistoryUseCase.listHistory(
            guildId = guildId,
            days = days,
            statusFilter = statusFilter,
        )
    }

    @Transactional(readOnly = true)
    fun getMeetingDetail(
        guildId: Long,
        meetingSessionId: Long,
    ): MeetingDetailResult {
        return meetingHistoryUseCase.detail(
            guildId = guildId,
            meetingSessionId = meetingSessionId,
        )
    }

    sealed interface StartResult {
        data class Success(
            val sessionId: Long,
            val threadId: Long,
            val threadName: String,
        ) : StartResult

        data class AlreadyActive(val threadId: Long) : StartResult
        data object ChannelNotConfigured : StartResult
        data object ChannelNotFound : StartResult
        data object ThreadCreateFailed : StartResult
    }

    sealed interface EndResult {
        data class Success(
            val sessionId: Long,
            val threadId: Long,
            val summaryMessageId: Long,
            val sourceMessageCount: Int,
            val participantCount: Int,
            val summaryArtifactId: Long?,
            val agendaTitle: String?,
            val agendaUrl: String?,
            val decisions: List<String>,
            val actionItems: List<String>,
            val todos: List<String>,
            val archived: Boolean,
        ) : EndResult

        data class ClosedMissingThread(
            val sessionId: Long,
            val threadId: Long,
        ) : EndResult
        data object SessionNotFound : EndResult
        data object AlreadyEnded : EndResult
        data class ThreadNotFound(val threadId: Long) : EndResult
    }

    sealed interface SummaryMutationResult {
        data class Success(
            val sessionId: Long,
            val threadId: Long,
            val summaryMessageId: Long,
            val sourceMessageCount: Int,
            val participantCount: Int,
            val summaryArtifactId: Long?,
            val decisions: List<String>,
            val actionItems: List<String>,
            val todos: List<String>,
        ) : SummaryMutationResult

        data object SessionNotFound : SummaryMutationResult
        data object ArtifactNotFound : SummaryMutationResult
        data class ThreadNotFound(val threadId: Long) : SummaryMutationResult
    }

    sealed interface SummaryProgress {
        data object Collecting : SummaryProgress
        data class Collected(val messageCount: Int) : SummaryProgress
    }

    sealed interface StructuredCaptureResult {
        data class Success(
            val sessionId: Long,
            val threadId: Long,
            val itemId: Long,
            val type: StructuredCaptureType,
            val summaryLine: String,
        ) : StructuredCaptureResult

        data object SessionNotFound : StructuredCaptureResult
        data object MeetingIdRequired : StructuredCaptureResult
        data object MeetingNotActive : StructuredCaptureResult
        data class ThreadNotFound(val threadId: Long) : StructuredCaptureResult
    }

    sealed interface StructuredListResult {
        data class Success(
            val sessionId: Long,
            val threadId: Long,
            val items: List<StructuredItemView>,
        ) : StructuredListResult

        data object SessionNotFound : StructuredListResult
        data object MeetingIdRequired : StructuredListResult
        data object MeetingNotActive : StructuredListResult
        data class ThreadNotFound(val threadId: Long) : StructuredListResult
    }

    sealed interface StructuredCancelResult {
        data class Success(
            val sessionId: Long,
            val threadId: Long,
            val item: StructuredItemView,
        ) : StructuredCancelResult

        data object SessionNotFound : StructuredCancelResult
        data object MeetingIdRequired : StructuredCancelResult
        data object MeetingNotActive : StructuredCancelResult
        data class ThreadNotFound(val threadId: Long) : StructuredCancelResult
        data object ItemNotFound : StructuredCancelResult
    }

    enum class StructuredCaptureType {
        DECISION,
        ACTION,
        TODO,
    }

    data class StructuredItemView(
        val id: Long,
        val type: StructuredCaptureType,
        val summary: String,
    )

    data class ActiveMeetingView(
        val sessionId: Long,
        val boardChannelId: Long?,
        val threadId: Long,
        val startedBy: Long,
        val startedAt: java.time.Instant,
    )

    sealed interface ActiveMeetingsResult {
        data class Success(val meetings: List<ActiveMeetingView>) : ActiveMeetingsResult
    }

    enum class HistoryStatusFilter {
        ALL,
        ACTIVE,
        ENDED,
    }

    data class HistoryMeetingView(
        val sessionId: Long,
        val status: com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus,
        val boardChannelId: Long?,
        val threadId: Long,
        val startedBy: Long,
        val startedAt: java.time.Instant,
        val endedAt: java.time.Instant?,
        val summaryMessageId: Long?,
        val decisionCount: Int,
        val actionCount: Int,
        val todoCount: Int,
    )

    sealed interface HistoryResult {
        data class Success(val meetings: List<HistoryMeetingView>) : HistoryResult
        data object InvalidDays : HistoryResult
    }

    data class MeetingDetailView(
        val sessionId: Long,
        val status: com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus,
        val boardChannelId: Long?,
        val threadId: Long,
        val startedBy: Long,
        val startedAt: java.time.Instant,
        val endedBy: Long?,
        val endedAt: java.time.Instant?,
        val summaryMessageId: Long?,
        val agendaTitle: String?,
        val agendaUrl: String?,
        val decisionCount: Int,
        val actionCount: Int,
        val todoCount: Int,
        val decisions: List<String>,
        val actions: List<String>,
        val todos: List<String>,
    )

    sealed interface MeetingDetailResult {
        data class Success(val detail: MeetingDetailView) : MeetingDetailResult
        data object NotFound : MeetingDetailResult
    }
}
