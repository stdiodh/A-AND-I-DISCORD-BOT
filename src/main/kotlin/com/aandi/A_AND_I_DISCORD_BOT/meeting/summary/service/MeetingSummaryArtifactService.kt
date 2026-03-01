package com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.service

import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.MeetingSummaryExtractor
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.entity.MeetingSummaryArtifactEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.repository.MeetingSummaryArtifactRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingSummaryArtifactService(
    private val meetingSummaryArtifactRepository: MeetingSummaryArtifactRepository,
) {

    fun saveGeneratedArtifact(
        meetingSessionId: Long,
        guildId: Long,
        threadId: Long,
        summaryMessageId: Long?,
        messageCount: Int,
        participantCount: Int,
        summary: MeetingSummaryExtractor.MeetingSummary,
        windowStart: Instant,
        windowEnd: Instant,
        sourceBufferSeconds: Int,
        version: String,
    ): MeetingSummaryArtifactEntity {
        val nowUtc = Instant.now()
        val entity = MeetingSummaryArtifactEntity(
            meetingSessionId = meetingSessionId,
            guildId = guildId,
            threadId = threadId,
            summaryMessageId = summaryMessageId,
            messageCount = messageCount,
            participantCount = participantCount,
            decisionCount = summary.decisions.size,
            actionCount = summary.actionItems.size,
            todoCount = summary.todos.size,
            generatedAt = nowUtc,
            version = version,
            sourceWindowStart = windowStart,
            sourceWindowEnd = windowEnd,
            sourceBufferSeconds = sourceBufferSeconds,
            decisionsText = serializeLines(summary.decisions),
            actionsText = serializeLines(summary.actionItems),
            todosText = serializeLines(summary.todos),
            createdAt = nowUtc,
            updatedAt = nowUtc,
        )
        return meetingSummaryArtifactRepository.save(entity)
    }

    fun findLatestByMeetingSessionId(meetingSessionId: Long): MeetingSummaryArtifactEntity? {
        return meetingSummaryArtifactRepository.findFirstByMeetingSessionIdOrderByGeneratedAtDesc(meetingSessionId)
    }

    fun findLatestByGuildAndThread(guildId: Long, threadId: Long): MeetingSummaryArtifactEntity? {
        return meetingSummaryArtifactRepository.findFirstByGuildIdAndThreadIdOrderByGeneratedAtDesc(guildId, threadId)
    }

    fun appendDecision(meetingSessionId: Long, decision: String): MeetingSummaryArtifactEntity? {
        val artifact = findLatestByMeetingSessionId(meetingSessionId) ?: return null
        val updatedDecisions = parseLines(artifact.decisionsText) + decision.trim()
        artifact.decisionsText = serializeLines(updatedDecisions)
        artifact.decisionCount = updatedDecisions.size
        artifact.updatedAt = Instant.now()
        return meetingSummaryArtifactRepository.save(artifact)
    }

    fun appendAction(meetingSessionId: Long, action: String): MeetingSummaryArtifactEntity? {
        val artifact = findLatestByMeetingSessionId(meetingSessionId) ?: return null
        val updatedActions = parseLines(artifact.actionsText) + action.trim()
        artifact.actionsText = serializeLines(updatedActions)
        artifact.actionCount = updatedActions.size
        artifact.updatedAt = Instant.now()
        return meetingSummaryArtifactRepository.save(artifact)
    }

    fun toSummary(artifact: MeetingSummaryArtifactEntity): MeetingSummaryExtractor.MeetingSummary {
        return MeetingSummaryExtractor.MeetingSummary(
            decisions = parseLines(artifact.decisionsText),
            actionItems = parseLines(artifact.actionsText),
            todos = parseLines(artifact.todosText),
            highlights = emptyList(),
        )
    }

    fun save(artifact: MeetingSummaryArtifactEntity): MeetingSummaryArtifactEntity {
        return meetingSummaryArtifactRepository.save(artifact)
    }

    private fun parseLines(raw: String?): List<String> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return raw.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun serializeLines(lines: List<String>): String? {
        val normalized = lines
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalized.isEmpty()) {
            return null
        }
        return normalized.joinToString("\n")
    }
}
