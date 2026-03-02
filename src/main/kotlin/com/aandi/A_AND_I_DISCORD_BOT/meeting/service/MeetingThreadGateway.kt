package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.service.AgendaService
import com.aandi.A_AND_I_DISCORD_BOT.common.log.StructuredLog
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeCustomIdParser
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.MeetingSummaryExtractor
import com.aandi.A_AND_I_DISCORD_BOT.meeting.ui.MeetingSummaryActionIds
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.Instant

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingThreadGateway(
    @Lazy private val jda: JDA,
    private val agendaService: AgendaService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun findTextChannel(channelId: Long): TextChannel? {
        return jda.getTextChannelById(channelId)
    }

    fun findThreadChannel(threadId: Long): ThreadChannel? {
        jda.getThreadChannelById(threadId)?.let { return it }

        jda.guilds.forEach { guild ->
            val activeThread = runCatching { guild.retrieveActiveThreads().complete() }
                .getOrNull()
                ?.firstOrNull { it.idLong == threadId }
            if (activeThread != null) {
                return activeThread
            }
        }
        return null
    }

    fun createStartMessage(channel: TextChannel, requestedBy: Long, nowUtc: Instant): Message {
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

    fun createThread(startMessage: Message, threadName: String): ThreadChannel? {
        return runCatching { startMessage.createThreadChannel(threadName).complete() }.getOrNull()
    }

    fun postMeetingTemplate(guildId: Long, thread: ThreadChannel) {
        val template = buildString {
            appendLine("## 회의 템플릿")
            appendLine("- 결정: (예) 결정: 다음 주까지 MVP 배포")
            appendLine("- 액션: (예) 액션: 홍길동이 CI 파이프라인 정리")
            appendLine("- TODO: (예) TODO: 인증 예외 케이스 테스트 추가")
        }

        val agenda = agendaService.getTodayAgenda(guildId)
        if (agenda == null) {
            thread.sendMessage("$template\n\n오늘 안건 링크가 아직 등록되지 않았습니다.\n`/회의 안건등록 링크:<URL>` 또는 아래 버튼으로 먼저 설정해 주세요.")
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

    fun collectMessages(thread: ThreadChannel, maxCount: Int): List<Message> {
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

    fun collectMessagesInWindow(
        thread: ThreadChannel,
        windowStart: Instant,
        windowEnd: Instant,
        maxCount: Int,
    ): MessageCollectionResult {
        val accepted = mutableListOf<Message>()
        val history = thread.history
        while (accepted.size < maxCount) {
            val remaining = maxCount - accepted.size
            val batchSize = remaining.coerceAtMost(100)
            val batch = runCatching { history.retrievePast(batchSize).complete() }.getOrElse { emptyList() }
            if (batch.isEmpty()) {
                break
            }

            batch.forEach { message ->
                val createdAt = message.timeCreated.toInstant()
                if (createdAt.isBefore(windowStart)) {
                    return@forEach
                }
                if (createdAt.isAfter(windowEnd)) {
                    return@forEach
                }
                if (message.author.isBot) {
                    return@forEach
                }
                accepted.add(message)
            }

            val oldestCreatedAt = batch.last().timeCreated.toInstant()
            if (oldestCreatedAt.isBefore(windowStart) || batch.size < batchSize) {
                break
            }
        }

        val deduped = accepted
            .distinctBy { it.idLong }
            .sortedBy { it.timeCreated.toInstant() }
        val participantCount = deduped.map { it.author.idLong }.toSet().size
        return MessageCollectionResult(
            messages = deduped,
            participantCount = participantCount,
            windowStart = windowStart,
            windowEnd = windowEnd,
        )
    }

    fun postSummary(
        thread: ThreadChannel,
        summary: MeetingSummaryExtractor.MeetingSummary,
        sourceMessageCount: Int,
        participantCount: Int,
    ): Message {
        val embed = buildSummaryEmbed(
            summary = summary,
            sourceMessageCount = sourceMessageCount,
            participantCount = participantCount,
            sourceWindowStart = null,
            sourceWindowEnd = null,
            meetingSummaryV2 = false,
        )

        return thread.sendMessageEmbeds(embed)
            .setComponents(ActionRow.of(Button.success(HomeCustomIdParser.of("task", "create"), "액션아이템 → 과제 등록")))
            .complete()
    }

    fun upsertSummaryV2(
        thread: ThreadChannel,
        sessionId: Long,
        existingSummaryMessageId: Long?,
        summary: MeetingSummaryExtractor.MeetingSummary,
        sourceMessageCount: Int,
        participantCount: Int,
        sourceWindowStart: Instant,
        sourceWindowEnd: Instant,
        structuredDecisions: List<String> = emptyList(),
        extractedDecisions: List<String> = summary.decisions,
        structuredActions: List<String> = emptyList(),
        extractedActions: List<String> = summary.actionItems,
    ): Message {
        val embed = buildSummaryEmbed(
            summary = summary,
            sourceMessageCount = sourceMessageCount,
            participantCount = participantCount,
            sourceWindowStart = sourceWindowStart,
            sourceWindowEnd = sourceWindowEnd,
            meetingSummaryV2 = true,
            structuredDecisions = structuredDecisions,
            extractedDecisions = extractedDecisions,
            structuredActions = structuredActions,
            extractedActions = extractedActions,
        )

        val components = listOf(
            ActionRow.of(
                Button.primary(MeetingSummaryActionIds.regenerate(sessionId), "요약 재생성"),
                Button.success(MeetingSummaryActionIds.addDecision(sessionId), "결정 추가"),
                Button.success(MeetingSummaryActionIds.addAction(sessionId), "액션 추가"),
            ),
            ActionRow.of(
                Button.secondary(
                    MeetingSummaryActionIds.source(sessionId),
                    "원문 보기/메시지 수 ${sourceMessageCount}개",
                ),
            ),
        )

        val existing = existingSummaryMessageId?.let { messageId ->
            runCatching { thread.retrieveMessageById(messageId).complete() }.getOrNull()
        }

        if (existing != null) {
            existing.editMessageEmbeds(embed)
                .setComponents(components)
                .complete()
            return existing
        }

        return thread.sendMessageEmbeds(embed)
            .setComponents(components)
            .complete()
    }

    fun postEndedMessage(thread: ThreadChannel) {
        val embed = EmbedBuilder()
            .setColor(Color(0x1ABC9C))
            .setTitle("회의 종료")
            .setDescription("회의를 종료하고 스레드를 아카이브합니다.")
            .build()
        runCatching { thread.sendMessageEmbeds(embed).complete() }
    }

    fun postStructuredCaptureNotice(thread: ThreadChannel, message: String) {
        thread.sendMessage(message).queue(
            {},
            { exception ->
                log.warn(
                    StructuredLog.event(
                        name = "meeting.capture.notice.failed",
                        "threadId" to thread.idLong,
                    ),
                    exception,
                )
            },
        )
    }

    fun archiveThread(thread: ThreadChannel): Boolean {
        if (thread.isArchived) {
            return true
        }
        val result = runCatching { thread.manager.setArchived(true).complete() }
        return result.isSuccess
    }

    private fun buildSummaryEmbed(
        summary: MeetingSummaryExtractor.MeetingSummary,
        sourceMessageCount: Int,
        participantCount: Int,
        sourceWindowStart: Instant?,
        sourceWindowEnd: Instant?,
        meetingSummaryV2: Boolean,
        structuredDecisions: List<String> = emptyList(),
        extractedDecisions: List<String> = summary.decisions,
        structuredActions: List<String> = emptyList(),
        extractedActions: List<String> = summary.actionItems,
    ): net.dv8tion.jda.api.entities.MessageEmbed {
        val decisionsText = toBullet(summary.decisions, "추출된 결정 항목이 없습니다.")
        val actionItemsText = toBullet(summary.actionItems, "추출된 액션아이템이 없습니다.")
        val todosText = toBullet(summary.todos, "추출된 TODO 항목이 없습니다.")
        val highlightsText = toBullet(summary.highlights, "핵심 문장을 추출하지 못했습니다.")
        val countLine = "결정 ${summary.decisions.size} / 액션 ${summary.actionItems.size} / TODO ${summary.todos.size} / 참여자 $participantCount"
        val description = if (meetingSummaryV2) {
            "스레드 텍스트 기반 자동 요약 v2입니다. (분석 메시지 수: $sourceMessageCount)"
        } else {
            "스레드 텍스트 기반 자동 요약입니다. (분석 메시지 수: $sourceMessageCount)"
        }
        val embedBuilder = EmbedBuilder()
            .setColor(Color(0x2D9CDB))
            .setTitle(if (meetingSummaryV2) "회의 요약 (v2)" else "회의 요약 (MVP0)")
            .setDescription(description)
            .addField("요약 통계", countLine, false)
            .setFooter("결정/액션은 패턴 기반 추출 결과입니다.")

        if (meetingSummaryV2) {
            embedBuilder
                .addField("결정(구조화)", toBullet(structuredDecisions, "등록된 결정이 없습니다."), false)
                .addField("결정(추출)", toBullet(extractedDecisions, "추출된 결정 항목이 없습니다."), false)
                .addField("액션(구조화)", toBullet(structuredActions, "등록된 액션이 없습니다."), false)
                .addField("액션(추출)", toBullet(extractedActions, "추출된 액션아이템이 없습니다."), false)
                .addField("TODO", todosText, false)
                .addField("핵심문장", highlightsText, false)
        } else {
            embedBuilder
                .addField("결정", decisionsText, false)
                .addField("액션아이템", actionItemsText, false)
                .addField("TODO", todosText, false)
                .addField("핵심문장", highlightsText, false)
        }

        if (meetingSummaryV2 && summary.decisions.isEmpty() && summary.actionItems.isEmpty()) {
            embedBuilder.addField(
                "안내",
                "결정/액션을 아직 인식하지 못했어요. 아래에서 추가하거나 재생성할 수 있어요.",
                false,
            )
        }

        if (meetingSummaryV2 && sourceWindowStart != null && sourceWindowEnd != null) {
            embedBuilder.addField(
                "원문 수집 범위(KST)",
                "${KstTime.formatInstantToKst(sourceWindowStart)} ~ ${KstTime.formatInstantToKst(sourceWindowEnd)}",
                false,
            )
        }

        return embedBuilder.build()
    }

    private fun toBullet(items: List<String>, fallback: String): String {
        if (items.isEmpty()) {
            return fallback
        }
        return items.joinToString("\n") { "• $it" }
    }

    data class MessageCollectionResult(
        val messages: List<Message>,
        val participantCount: Int,
        val windowStart: Instant,
        val windowEnd: Instant,
    )
}
