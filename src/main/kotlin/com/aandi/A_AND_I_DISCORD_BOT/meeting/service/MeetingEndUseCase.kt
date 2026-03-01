package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.AgendaLinkRepository
import com.aandi.A_AND_I_DISCORD_BOT.common.config.FeatureFlagsProperties
import com.aandi.A_AND_I_DISCORD_BOT.common.log.StructuredLog
import com.aandi.A_AND_I_DISCORD_BOT.meeting.domain.MeetingSessionStateMachine
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.structured.service.MeetingStructuredItemService
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.MeetingSummaryExtractor
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.service.MeetingSummaryArtifactService
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import org.slf4j.LoggerFactory
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
    private val meetingStructuredItemService: MeetingStructuredItemService,
    private val meetingSummaryExtractor: MeetingSummaryExtractor,
    private val meetingSummaryArtifactService: MeetingSummaryArtifactService,
    private val meetingThreadGateway: MeetingThreadGateway,
    private val featureFlags: FeatureFlagsProperties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun endMeeting(
        guildId: Long,
        requestedBy: Long,
        fallbackThreadId: Long?,
        requestedThreadId: Long?,
        progress: ((MeetingService.SummaryProgress) -> Unit)? = null,
    ): MeetingService.EndResult {
        val session = resolveSessionForEnd(guildId, fallbackThreadId, requestedThreadId)
            ?: return MeetingService.EndResult.SessionNotFound
        val transition = meetingSessionStateMachine.end(session.status)
        if (transition is MeetingSessionStateMachine.Transition.Rejected) {
            return MeetingService.EndResult.AlreadyEnded
        }

        val thread = meetingThreadGateway.findThreadChannel(session.threadId)
            ?: return closeMissingThreadSession(session, requestedBy)

        val meetingEndBase = Instant.now(clock)
        val generated = generateSummaryOrThrow(
            session = session,
            thread = thread,
            meetingEndBase = meetingEndBase,
            progress = progress,
            mode = SUMMARY_MODE_END,
        )

        val nowUtc = Instant.now(clock)
        session.status = MeetingSessionStatus.ENDED
        session.endedBy = requestedBy
        session.endedAt = meetingEndBase
        session.summaryMessageId = generated.summaryMessageId
        session.updatedAt = nowUtc
        meetingSessionRepository.save(session)
        val sessionId = session.id ?: return MeetingService.EndResult.SessionNotFound
        val linkedAgenda = resolveLinkedAgenda(session)
        meetingThreadGateway.postEndedMessage(thread)
        val archived = meetingThreadGateway.archiveThread(thread)

        return MeetingService.EndResult.Success(
            sessionId = sessionId,
            threadId = thread.idLong,
            summaryMessageId = generated.summaryMessageId,
            sourceMessageCount = generated.sourceMessageCount,
            participantCount = generated.participantCount,
            summaryArtifactId = generated.summaryArtifactId,
            agendaTitle = linkedAgenda?.title,
            agendaUrl = linkedAgenda?.url,
            decisions = generated.summary.decisions,
            actionItems = generated.summary.actionItems,
            todos = generated.summary.todos,
            archived = archived,
        )
    }

    fun regenerateSummary(
        guildId: Long,
        requestedBy: Long,
        meetingSessionId: Long,
        progress: ((MeetingService.SummaryProgress) -> Unit)? = null,
    ): MeetingService.SummaryMutationResult {
        val session = meetingSessionRepository.findById(meetingSessionId).orElse(null)
            ?: return MeetingService.SummaryMutationResult.SessionNotFound
        if (session.guildId != guildId) {
            return MeetingService.SummaryMutationResult.SessionNotFound
        }
        val thread = meetingThreadGateway.findThreadChannel(session.threadId)
            ?: return MeetingService.SummaryMutationResult.ThreadNotFound(session.threadId)
        val meetingEndBase = session.endedAt ?: Instant.now(clock)
        val generated = generateSummaryOrThrow(
            session = session,
            thread = thread,
            meetingEndBase = meetingEndBase,
            progress = progress,
            mode = SUMMARY_MODE_REGENERATE,
        )

        session.summaryMessageId = generated.summaryMessageId
        session.updatedAt = Instant.now(clock)
        meetingSessionRepository.save(session)
        return MeetingService.SummaryMutationResult.Success(
            sessionId = meetingSessionId,
            threadId = session.threadId,
            summaryMessageId = generated.summaryMessageId,
            sourceMessageCount = generated.sourceMessageCount,
            participantCount = generated.participantCount,
            summaryArtifactId = generated.summaryArtifactId,
            decisions = generated.summary.decisions,
            actionItems = generated.summary.actionItems,
            todos = generated.summary.todos,
        )
    }

    fun addManualDecision(
        guildId: Long,
        requestedBy: Long,
        meetingSessionId: Long,
        decision: String,
    ): MeetingService.SummaryMutationResult {
        val session = meetingSessionRepository.findById(meetingSessionId).orElse(null)
            ?: return MeetingService.SummaryMutationResult.SessionNotFound
        if (session.guildId != guildId) {
            return MeetingService.SummaryMutationResult.SessionNotFound
        }
        val thread = meetingThreadGateway.findThreadChannel(session.threadId)
            ?: return MeetingService.SummaryMutationResult.ThreadNotFound(session.threadId)
        val normalized = decision.trim()
        if (normalized.isBlank()) {
            return MeetingService.SummaryMutationResult.ArtifactNotFound
        }
        val sessionId = session.id ?: return MeetingService.SummaryMutationResult.SessionNotFound
        meetingStructuredItemService.addDecision(
            meetingSessionId = sessionId,
            guildId = session.guildId,
            threadId = session.threadId,
            content = normalized,
            createdBy = requestedBy,
        )
        val artifact = meetingSummaryArtifactService.appendDecision(meetingSessionId, normalized)
            ?: return MeetingService.SummaryMutationResult.ArtifactNotFound
        return applyManualMutation(session, thread, artifact, requestedBy)
    }

    fun addManualAction(
        guildId: Long,
        requestedBy: Long,
        meetingSessionId: Long,
        action: String,
    ): MeetingService.SummaryMutationResult {
        val session = meetingSessionRepository.findById(meetingSessionId).orElse(null)
            ?: return MeetingService.SummaryMutationResult.SessionNotFound
        if (session.guildId != guildId) {
            return MeetingService.SummaryMutationResult.SessionNotFound
        }
        val thread = meetingThreadGateway.findThreadChannel(session.threadId)
            ?: return MeetingService.SummaryMutationResult.ThreadNotFound(session.threadId)
        val normalized = action.trim()
        if (normalized.isBlank()) {
            return MeetingService.SummaryMutationResult.ArtifactNotFound
        }
        val sessionId = session.id ?: return MeetingService.SummaryMutationResult.SessionNotFound
        meetingStructuredItemService.addAction(
            meetingSessionId = sessionId,
            guildId = session.guildId,
            threadId = session.threadId,
            content = normalized,
            assigneeUserId = null,
            dueDateLocal = null,
            createdBy = requestedBy,
        )
        val artifact = meetingSummaryArtifactService.appendAction(meetingSessionId, normalized)
            ?: return MeetingService.SummaryMutationResult.ArtifactNotFound
        return applyManualMutation(session, thread, artifact, requestedBy)
    }

    private fun applyManualMutation(
        session: MeetingSessionEntity,
        thread: ThreadChannel,
        artifact: com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.entity.MeetingSummaryArtifactEntity,
        requestedBy: Long,
    ): MeetingService.SummaryMutationResult {
        val sessionId = session.id ?: return MeetingService.SummaryMutationResult.SessionNotFound
        val summary = meetingSummaryArtifactService.toSummary(artifact)
        val summaryMessage = meetingThreadGateway.upsertSummaryV2(
            thread = thread,
            sessionId = sessionId,
            existingSummaryMessageId = session.summaryMessageId ?: artifact.summaryMessageId,
            summary = summary,
            sourceMessageCount = artifact.messageCount,
            participantCount = artifact.participantCount,
            sourceWindowStart = artifact.sourceWindowStart,
            sourceWindowEnd = artifact.sourceWindowEnd,
        )

        val nowUtc = Instant.now(clock)
        session.summaryMessageId = summaryMessage.idLong
        session.updatedAt = nowUtc
        meetingSessionRepository.save(session)

        artifact.summaryMessageId = summaryMessage.idLong
        artifact.updatedAt = nowUtc
        meetingSummaryArtifactService.save(artifact)

        log.info(
            StructuredLog.event(
                name = "meeting.summary.manual.updated",
                "guildId" to session.guildId,
                "threadId" to session.threadId,
                "sessionId" to sessionId,
                "requestedBy" to requestedBy,
                "summaryArtifactId" to artifact.id,
                "summaryMessageId" to summaryMessage.idLong,
            ),
        )

        return MeetingService.SummaryMutationResult.Success(
            sessionId = sessionId,
            threadId = session.threadId,
            summaryMessageId = summaryMessage.idLong,
            sourceMessageCount = artifact.messageCount,
            participantCount = artifact.participantCount,
            summaryArtifactId = artifact.id,
            decisions = summary.decisions,
            actionItems = summary.actionItems,
            todos = summary.todos,
        )
    }

    private fun generateSummary(
        session: MeetingSessionEntity,
        thread: ThreadChannel,
        meetingEndBase: Instant,
        progress: ((MeetingService.SummaryProgress) -> Unit)?,
    ): GeneratedSummary {
        val summaryStartedAtMillis = System.currentTimeMillis()
        if (!featureFlags.meetingSummaryV2) {
            val legacyMessages = meetingThreadGateway.collectMessages(thread, MAX_SUMMARY_MESSAGES)
            val legacySummary = meetingSummaryExtractor.extract(
                legacyMessages.map {
                    MeetingSummaryExtractor.MeetingMessage(
                        authorId = it.author.idLong,
                        content = resolveSummaryContent(it),
                        createdAt = it.timeCreated.toInstant(),
                    )
                },
            )
            val summaryMessage = meetingThreadGateway.postSummary(thread, legacySummary, legacyMessages.size)
            val durationMs = System.currentTimeMillis() - summaryStartedAtMillis
            log.info(
                StructuredLog.event(
                    name = "meeting.summary.end",
                    "guildId" to session.guildId,
                    "threadId" to thread.idLong,
                    "sessionId" to session.id,
                    "mode" to "legacy",
                    "messageCount" to legacyMessages.size,
                    "participantCount" to legacyMessages.map { it.author.idLong }.toSet().size,
                    "summaryMessageId" to summaryMessage.idLong,
                    "summaryArtifactId" to null,
                    "durationMs" to durationMs,
                ),
            )
            return GeneratedSummary(
                summary = legacySummary,
                summaryMessageId = summaryMessage.idLong,
                sourceMessageCount = legacyMessages.size,
                participantCount = legacyMessages.map { it.author.idLong }.toSet().size,
                summaryArtifactId = null,
            )
        }

        progress?.invoke(MeetingService.SummaryProgress.Collecting)
        if (SUMMARY_GRACE_SECONDS > 0) {
            Thread.sleep(SUMMARY_GRACE_SECONDS * 1000L)
        }
        val sessionId = session.id ?: throw IllegalStateException("meeting session id is null")
        val sourceWindowStart = session.startedAt
        val sourceWindowEnd = meetingEndBase.plusSeconds(SUMMARY_GRACE_SECONDS.toLong())

        log.info(
            StructuredLog.event(
                name = "meeting.summary.start",
                "guildId" to session.guildId,
                "threadId" to thread.idLong,
                "sessionId" to sessionId,
                "meetingSummaryV2" to featureFlags.meetingSummaryV2,
                "windowStart" to sourceWindowStart,
                "windowEnd" to sourceWindowEnd,
                "bufferSeconds" to SUMMARY_GRACE_SECONDS,
            ),
        )

        val collected = meetingThreadGateway.collectMessagesInWindow(
            thread = thread,
            windowStart = sourceWindowStart,
            windowEnd = sourceWindowEnd,
            maxCount = MAX_SUMMARY_MESSAGES,
        )
        progress?.invoke(MeetingService.SummaryProgress.Collected(collected.messages.size))

        val summaryInput = collected.messages.map {
            MeetingSummaryExtractor.MeetingMessage(
                authorId = it.author.idLong,
                content = resolveSummaryContent(it),
                createdAt = it.timeCreated.toInstant(),
            )
        }
        val extracted = meetingSummaryExtractor.extract(summaryInput)
        val structuredItems = meetingStructuredItemService.listSummaryItems(sessionId)
        val decisionMerge = mergeStructuredAndExtracted(
            structured = structuredItems.decisions,
            extracted = extracted.decisions,
        )
        val actionMerge = mergeStructuredAndExtracted(
            structured = structuredItems.actions,
            extracted = extracted.actionItems,
        )
        val mergedSummary = MeetingSummaryExtractor.MeetingSummary(
            decisions = decisionMerge.merged,
            actionItems = actionMerge.merged,
            todos = extracted.todos,
            highlights = extracted.highlights,
        )

        val summaryMessage = meetingThreadGateway.upsertSummaryV2(
            thread = thread,
            sessionId = sessionId,
            existingSummaryMessageId = session.summaryMessageId,
            summary = mergedSummary,
            sourceMessageCount = collected.messages.size,
            participantCount = collected.participantCount,
            sourceWindowStart = sourceWindowStart,
            sourceWindowEnd = sourceWindowEnd,
            structuredDecisions = decisionMerge.structured,
            extractedDecisions = decisionMerge.extractedOnly,
            structuredActions = actionMerge.structured,
            extractedActions = actionMerge.extractedOnly,
        )
        val artifact = meetingSummaryArtifactService.saveGeneratedArtifact(
            meetingSessionId = sessionId,
            guildId = session.guildId,
            threadId = session.threadId,
            summaryMessageId = summaryMessage.idLong,
            messageCount = collected.messages.size,
            participantCount = collected.participantCount,
            summary = mergedSummary,
            windowStart = sourceWindowStart,
            windowEnd = sourceWindowEnd,
            sourceBufferSeconds = SUMMARY_GRACE_SECONDS,
            version = SUMMARY_VERSION,
        )

        log.info(
            StructuredLog.event(
                name = "meeting.summary.end",
                "guildId" to session.guildId,
                "threadId" to thread.idLong,
                "sessionId" to sessionId,
                "messageCount" to collected.messages.size,
                "participantCount" to collected.participantCount,
                "structuredDecisionCount" to decisionMerge.structured.size,
                "structuredActionCount" to actionMerge.structured.size,
                "summaryMessageId" to summaryMessage.idLong,
                "summaryArtifactId" to artifact.id,
                "durationMs" to (System.currentTimeMillis() - summaryStartedAtMillis),
            ),
        )

        return GeneratedSummary(
            summary = mergedSummary,
            summaryMessageId = summaryMessage.idLong,
            sourceMessageCount = collected.messages.size,
            participantCount = collected.participantCount,
            summaryArtifactId = artifact.id,
        )
    }

    private fun mergeStructuredAndExtracted(
        structured: List<String>,
        extracted: List<String>,
    ): MergedSummaryItems {
        val structuredUnique = dedupeByNormalized(structured)
        val structuredNorm = structuredUnique
            .map { normalizeSummaryItem(it) }
            .filter { it.isNotBlank() }
            .toSet()

        val extractedUnique = dedupeByNormalized(extracted)
        val extractedOnly = extractedUnique.filter { normalizeSummaryItem(it) !in structuredNorm }
        return MergedSummaryItems(
            structured = structuredUnique,
            extractedOnly = extractedOnly,
            merged = structuredUnique + extractedOnly,
        )
    }

    private fun dedupeByNormalized(items: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        items.forEach { raw ->
            val text = raw.trim()
            if (text.isBlank()) {
                return@forEach
            }
            val normalized = normalizeSummaryItem(text)
            if (normalized.isBlank() || !seen.add(normalized)) {
                return@forEach
            }
            result.add(text)
        }
        return result
    }

    private fun normalizeSummaryItem(text: String): String {
        return text.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun closeMissingThreadSession(
        session: MeetingSessionEntity,
        requestedBy: Long,
    ): MeetingService.EndResult {
        log.warn(
            StructuredLog.event(
                name = "meeting.summary.thread_missing",
                "guildId" to session.guildId,
                "threadId" to session.threadId,
                "sessionId" to session.id,
            ),
        )
        val nowUtc = Instant.now(clock)
        session.status = MeetingSessionStatus.ENDED
        session.endedBy = requestedBy
        session.endedAt = nowUtc
        session.updatedAt = nowUtc
        meetingSessionRepository.save(session)
        val sessionId = session.id ?: return MeetingService.EndResult.SessionNotFound
        return MeetingService.EndResult.ClosedMissingThread(
            sessionId = sessionId,
            threadId = session.threadId,
        )
    }

    private fun logSummaryFailure(
        session: MeetingSessionEntity,
        threadId: Long?,
        summaryStartedAtMillis: Long,
        exception: Throwable,
        mode: String,
    ) {
        log.error(
            StructuredLog.event(
                name = "meeting.summary.failed",
                "guildId" to session.guildId,
                "threadId" to threadId,
                "sessionId" to session.id,
                "mode" to mode,
                "durationMs" to (System.currentTimeMillis() - summaryStartedAtMillis),
                "errorType" to (exception::class.simpleName ?: "UnknownException"),
            ),
            exception,
        )
    }

    private fun generateSummaryOrThrow(
        session: MeetingSessionEntity,
        thread: ThreadChannel,
        meetingEndBase: Instant,
        progress: ((MeetingService.SummaryProgress) -> Unit)?,
        mode: String,
    ): GeneratedSummary {
        val summaryStartedAtMillis = System.currentTimeMillis()
        return runCatching {
            generateSummary(
                session = session,
                thread = thread,
                meetingEndBase = meetingEndBase,
                progress = progress,
            )
        }.onFailure { exception ->
            logSummaryFailure(
                session = session,
                threadId = thread.idLong,
                summaryStartedAtMillis = summaryStartedAtMillis,
                exception = exception,
                mode = mode,
            )
        }.getOrThrow()
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

    private data class GeneratedSummary(
        val summary: MeetingSummaryExtractor.MeetingSummary,
        val summaryMessageId: Long,
        val sourceMessageCount: Int,
        val participantCount: Int,
        val summaryArtifactId: Long?,
    )

    private data class MergedSummaryItems(
        val structured: List<String>,
        val extractedOnly: List<String>,
        val merged: List<String>,
    )

    companion object {
        private const val MAX_SUMMARY_MESSAGES = 500
        private const val SUMMARY_GRACE_SECONDS = 3
        private const val SUMMARY_VERSION = "v2"
        private const val SUMMARY_MODE_END = "end"
        private const val SUMMARY_MODE_REGENERATE = "regenerate"
    }
}
