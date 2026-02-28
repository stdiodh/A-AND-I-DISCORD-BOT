package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.service.AgendaService
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeCustomIdParser
import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.MeetingSummaryExtractor
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
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

    fun postSummary(
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

    fun postEndedMessage(thread: ThreadChannel) {
        val embed = EmbedBuilder()
            .setColor(Color(0x1ABC9C))
            .setTitle("회의 종료")
            .setDescription("회의를 종료하고 스레드를 아카이브합니다.")
            .build()
        runCatching { thread.sendMessageEmbeds(embed).complete() }
    }

    fun archiveThread(thread: ThreadChannel): Boolean {
        if (thread.isArchived) {
            return true
        }
        val result = runCatching { thread.manager.setArchived(true).complete() }
        return result.isSuccess
    }

    private fun toBullet(items: List<String>, fallback: String): String {
        if (items.isEmpty()) {
            return fallback
        }
        return items.joinToString("\n") { "• $it" }
    }
}
