package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingService(
    private val meetingStartUseCase: MeetingStartUseCase,
    private val meetingEndUseCase: MeetingEndUseCase,
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
        fallbackThreadId: Long?,
        requestedThreadId: Long?,
    ): EndResult {
        return meetingEndUseCase.endMeeting(
            guildId = guildId,
            requestedBy = requestedBy,
            fallbackThreadId = fallbackThreadId,
            requestedThreadId = requestedThreadId,
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
            val agendaTitle: String?,
            val agendaUrl: String?,
            val decisions: List<String>,
            val actionItems: List<String>,
            val archived: Boolean,
        ) : EndResult

        data object SessionNotFound : EndResult
        data object AlreadyEnded : EndResult
        data class ThreadNotFound(val threadId: Long) : EndResult
    }
}
