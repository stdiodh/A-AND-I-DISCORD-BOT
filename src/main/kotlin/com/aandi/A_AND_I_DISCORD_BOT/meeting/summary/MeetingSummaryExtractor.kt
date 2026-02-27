package com.aandi.A_AND_I_DISCORD_BOT.meeting.summary

import org.springframework.stereotype.Component
import java.time.Instant

@Component
class MeetingSummaryExtractor {

    fun extract(messages: List<MeetingMessage>): MeetingSummary {
        val decisions = mutableListOf<String>()
        val actionItems = mutableListOf<String>()
        val candidateHighlights = mutableListOf<String>()

        messages.forEach { message ->
            parseLines(message.content).forEach { line ->
                extractDecision(line)?.let { decisions.add(it) }
                extractActionItem(line)?.let { actionItems.add(it) }
                if (isHighlightCandidate(line)) {
                    candidateHighlights.add(line)
                }
            }
        }

        val highlights = candidateHighlights
            .distinct()
            .sortedByDescending { it.length }
            .take(MAX_HIGHLIGHTS)

        return MeetingSummary(
            decisions = decisions.distinct().take(MAX_DECISIONS),
            actionItems = actionItems.distinct().take(MAX_ACTION_ITEMS),
            highlights = highlights,
        )
    }

    private fun parseLines(content: String): List<String> = content
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    private fun extractDecision(line: String): String? {
        val normalized = normalizePrefix(line)
        DECISION_PATTERNS.forEach { pattern ->
            val match = pattern.find(normalized)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    private fun extractActionItem(line: String): String? {
        val normalized = normalizePrefix(line)
        ACTION_PATTERNS.forEach { pattern ->
            val match = pattern.find(normalized)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    private fun isHighlightCandidate(line: String): Boolean {
        if (line.startsWith("/")) {
            return false
        }
        if (line.length < 12) {
            return false
        }
        if (line.startsWith("http://") || line.startsWith("https://")) {
            return false
        }
        return true
    }

    private fun normalizePrefix(line: String): String {
        return line.replace(PREFIX_PATTERN, "").trim()
    }

    data class MeetingMessage(
        val authorId: Long,
        val content: String,
        val createdAt: Instant,
    )

    data class MeetingSummary(
        val decisions: List<String>,
        val actionItems: List<String>,
        val highlights: List<String>,
    )

    companion object {
        private const val MAX_DECISIONS = 5
        private const val MAX_ACTION_ITEMS = 8
        private const val MAX_HIGHLIGHTS = 5

        private val PREFIX_PATTERN = Regex("""^(?:>\s*)?(?:[-*•]\s*)?""")

        private val DECISION_PATTERNS = listOf(
            Regex("^(?:결정|결론|합의|decision)\\s*[:：-]\\s*(.+)$", RegexOption.IGNORE_CASE),
        )

        private val ACTION_PATTERNS = listOf(
            Regex("^(?:액션|할일|todo|action\\s*item|task)\\s*[:：-]\\s*(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:-\\s*)?\\[ \\]\\s*(.+)$"),
        )
    }
}
