package com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui

import org.springframework.stereotype.Component

@Component
class DashboardRenderer {

    fun render(input: DashboardInput): DashboardView {
        val safeName = input.guildName?.trim().orEmpty().ifBlank { "A&I" }
        val pendingCount = (input.pendingCount ?: 0).coerceAtLeast(0)
        val statusLine = listOf(
            if (input.todayAgenda != null) "안건 있음" else "안건 없음",
            if (input.isMeetingActive) "진행 중 회의 있음" else "진행 중 회의 없음",
            "미완료 과제 ${pendingCount}개",
            "오늘 모각코 ${input.todayParticipantCount}명",
        ).joinToString(" · ")

        val nearestDueLine = if (input.nextDueTask == null) {
            "- 오늘 임박 과제 없음"
        } else {
            "- ${input.nextDueTask.dueAtKst} 마감 과제 1건 (${input.nextDueTask.title})"
        }

        val meetingLine = if (input.isMeetingActive) {
            "- 진행 중 회의가 있습니다. [회의] 버튼으로 이동하세요."
        } else {
            "- 진행 중 회의 없음"
        }
        val agendaLine = if (input.todayAgenda == null) {
            "- 오늘 안건 링크 없음 (`/안건 설정` 권장)"
        } else {
            "- 오늘 안건 링크 등록 완료"
        }

        return DashboardView(
            title = "A&I 홈",
            overview = "서버: **$safeName**",
            statusLine = statusLine,
            todoSection = listOf(nearestDueLine, meetingLine, agendaLine).joinToString("\n"),
        )
    }

    data class DashboardInput(
        val guildName: String?,
        val isMeetingActive: Boolean,
        val todayAgenda: AgendaSummary?,
        val pendingCount: Int?,
        val nextDueTask: DueTaskSummary?,
        val todayParticipantCount: Int,
    )

    data class AgendaSummary(
        val title: String?,
        val url: String,
    )

    data class DueTaskSummary(
        val title: String,
        val dueAtKst: String,
    )

    data class DashboardView(
        val title: String,
        val overview: String,
        val statusLine: String,
        val todoSection: String,
    )
}
