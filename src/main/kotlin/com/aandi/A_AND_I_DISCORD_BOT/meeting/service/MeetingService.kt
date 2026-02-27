package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.agenda.service.AgendaService
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeCustomIdParser
import com.aandi.A_AND_I_DISCORD_BOT.meeting.domain.MeetingSessionStateMachine
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.MeetingSummaryExtractor
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingService(
    private val guildConfigService: GuildConfigService,
    private val agendaService: AgendaService,
    private val meetingSessionRepository: MeetingSessionRepository,
    private val meetingSessionStateMachine: MeetingSessionStateMachine,
    private val meetingSummaryExtractor: MeetingSummaryExtractor,
    private val jda: JDA,
    private val clock: Clock,
) {

    @Transactional
    fun startMeeting(
        guildId: Long,
        requestedBy: Long,
        targetChannelId: Long?,
        fallbackChannelId: Long?,
        rawTitle: String?,
    ): StartResult {
        val activeSession = meetingSessionRepository.findFirstByGuildIdAndStatusOrderByStartedAtDesc(
            guildId,
            MeetingSessionStatus.ACTIVE,
        )
        if (activeSession != null) {
            return StartResult.AlreadyActive(activeSession.threadId)
        }

        val dashboard = guildConfigService.getDashboard(guildId)
        val channelId = targetChannelId ?: dashboard.channelId ?: fallbackChannelId ?: return StartResult.ChannelNotConfigured
        val channel = jda.getTextChannelById(channelId) ?: return StartResult.ChannelNotFound
        val nowUtc = Instant.now(clock)
        val startMessage = createStartMessage(channel, requestedBy, nowUtc)
        val threadName = resolveThreadName(rawTitle)
        val thread = runCatching { startMessage.createThreadChannel(threadName).complete() }.getOrNull()
            ?: return StartResult.ThreadCreateFailed

        meetingSessionRepository.save(
            MeetingSessionEntity(
                guildId = guildId,
                threadId = thread.idLong,
                status = MeetingSessionStatus.ACTIVE,
                startedBy = requestedBy,
                startedAt = nowUtc,
                createdAt = nowUtc,
                updatedAt = nowUtc,
            ),
        )

        postMeetingTemplate(guildId, thread)
        return StartResult.Success(thread.idLong, thread.name)
    }

    @Transactional
    fun endMeeting(
        guildId: Long,
        requestedBy: Long,
        fallbackThreadId: Long?,
        requestedThreadId: Long?,
    ): EndResult {
        val session = resolveSessionForEnd(guildId, fallbackThreadId, requestedThreadId) ?: return EndResult.SessionNotFound
        val transition = meetingSessionStateMachine.end(session.status)
        if (transition is MeetingSessionStateMachine.Transition.Rejected) {
            return EndResult.AlreadyEnded
        }

        val thread = jda.getThreadChannelById(session.threadId) ?: return EndResult.ThreadNotFound(session.threadId)
        val messages = collectMessages(thread, MAX_SUMMARY_MESSAGES)
        val summaryInput = messages.map {
            MeetingSummaryExtractor.MeetingMessage(
                authorId = it.author.idLong,
                content = it.contentDisplay,
                createdAt = it.timeCreated.toInstant(),
            )
        }
        val summary = meetingSummaryExtractor.extract(summaryInput)
        val summaryMessage = postSummary(thread, summary, messages.size)

        session.status = MeetingSessionStatus.ENDED
        session.endedBy = requestedBy
        session.endedAt = Instant.now(clock)
        session.summaryMessageId = summaryMessage.idLong
        session.updatedAt = Instant.now(clock)
        meetingSessionRepository.save(session)
        postEndedMessage(thread)
        archiveThread(thread)

        return EndResult.Success(
            threadId = thread.idLong,
            summaryMessageId = summaryMessage.idLong,
            decisions = summary.decisions,
            actionItems = summary.actionItems,
        )
    }

    private fun postMeetingTemplate(guildId: Long, thread: ThreadChannel) {
        val template = buildString {
            appendLine("## 회의 템플릿")
            appendLine("- 결정: (예) 결정: 다음 주까지 MVP 배포")
            appendLine("- 액션: (예) 액션: 홍길동이 CI 파이프라인 정리")
            appendLine("- TODO: (예) TODO: 인증 예외 케이스 테스트 추가")
        }

        val agenda = agendaService.getTodayAgenda(guildId)
        if (agenda == null) {
            thread.sendMessage("$template\n\n오늘 안건 링크가 아직 등록되지 않았습니다.")
                .setComponents(ActionRow.of(Button.secondary("dash:agenda_set", "안건 설정")))
                .queue()
            return
        }

        thread.sendMessage("$template\n\n오늘 안건 링크를 확인하세요.")
            .setComponents(
                ActionRow.of(
                    Button.link(agenda.url, "오늘 안건 링크"),
                    Button.success(HomeCustomIdParser.of("task", "create"), "과제 등록"),
                ),
            )
            .queue()
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

    private fun collectMessages(thread: ThreadChannel, maxCount: Int): List<Message> {
        val messages = mutableListOf<Message>()
        val history = thread.history
        while (messages.size < maxCount) {
            val remaining = maxCount - messages.size
            val batchSize = remaining.coerceAtMost(100)
            val batch = runCatching { history.retrievePast(batchSize).complete() }.getOrElse { emptyList() }
            if (batch.isEmpty()) {
                break
            }
            messages.addAll(batch)
            if (batch.size < batchSize) {
                break
            }
        }

        return messages
            .asSequence()
            .filterNot { it.author.isBot }
            .sortedBy { it.timeCreated.toInstant() }
            .toList()
    }

    private fun postSummary(
        thread: ThreadChannel,
        summary: MeetingSummaryExtractor.MeetingSummary,
        sourceMessageCount: Int,
    ): Message {
        val decisionsText = toBullet(summary.decisions, "추출된 결정 항목이 없습니다.")
        val actionItemsText = toBullet(summary.actionItems, "추출된 액션아이템이 없습니다.")
        val highlightsText = toBullet(summary.highlights, "핵심 문장을 추출하지 못했습니다.")

        val embed = EmbedBuilder()
            .setColor(Color(0x2D9CDB))
            .setTitle("회의 요약 (MVP0)")
            .setDescription("스레드 텍스트 기반 자동 요약입니다. (분석 메시지 수: $sourceMessageCount)")
            .addField("결정", decisionsText, false)
            .addField("액션아이템", actionItemsText, false)
            .addField("핵심문장", highlightsText, false)
            .setFooter("결정/액션은 패턴 기반 추출 결과입니다.")
            .build()

        return thread.sendMessageEmbeds(embed)
            .setComponents(ActionRow.of(Button.success(HomeCustomIdParser.of("task", "create"), "액션아이템 → 과제 등록")))
            .complete()
    }

    private fun toBullet(items: List<String>, fallback: String): String {
        if (items.isEmpty()) {
            return fallback
        }
        return items.joinToString("\n") { "• $it" }
    }

    private fun resolveThreadName(rawTitle: String?): String {
        val normalized = rawTitle?.trim().orEmpty()
        if (normalized.isNotBlank()) {
            return normalized.take(MAX_THREAD_NAME_LENGTH)
        }
        val meetingDate = Instant.now(clock)
            .atZone(ZoneId.of("Asia/Seoul"))
            .toLocalDate()
        return "$meetingDate 회의"
    }

    private fun createStartMessage(channel: TextChannel, requestedBy: Long, nowUtc: Instant): Message {
        val startedAtKst = KstTime.formatInstantToKst(nowUtc)
        val embed = EmbedBuilder()
            .setColor(Color(0x5865F2))
            .setTitle("회의 시작")
            .setDescription("회의 스레드를 생성합니다.")
            .addField("시작자", "<@$requestedBy>", true)
            .addField("시작시각(KST)", startedAtKst, true)
            .build()
        return channel.sendMessageEmbeds(embed).complete()
    }

    private fun postEndedMessage(thread: ThreadChannel) {
        val embed = EmbedBuilder()
            .setColor(Color(0x1ABC9C))
            .setTitle("회의 종료")
            .setDescription("회의를 종료하고 스레드를 아카이브합니다.")
            .build()
        runCatching { thread.sendMessageEmbeds(embed).complete() }
    }

    private fun archiveThread(thread: ThreadChannel) {
        runCatching { thread.manager.setArchived(true).complete() }
    }

    sealed interface StartResult {
        data class Success(
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
            val threadId: Long,
            val summaryMessageId: Long,
            val decisions: List<String>,
            val actionItems: List<String>,
        ) : EndResult

        data object SessionNotFound : EndResult
        data object AlreadyEnded : EndResult
        data class ThreadNotFound(val threadId: Long) : EndResult
    }

    companion object {
        private const val MAX_THREAD_NAME_LENGTH = 100
        private const val MAX_SUMMARY_MESSAGES = 200
    }
}
