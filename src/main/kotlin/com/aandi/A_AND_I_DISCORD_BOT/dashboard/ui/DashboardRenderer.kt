package com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui

import org.springframework.stereotype.Component

@Component
class DashboardRenderer {

    fun render(input: DashboardInput): DashboardView {
        val safeName = input.guildName?.trim().orEmpty().ifBlank { "A&I" }
        val meeting = renderMeetingSection(input)
        val assignment = renderAssignmentSection(input)
        val mogakco = renderMogakcoSection(input)

        return DashboardView(
            title = "A&I 운영 홈",
            overview = "서버: **$safeName**\n버튼으로 기능별 채널로 이동한 뒤 명령어를 실행하세요.",
            meetingSection = meeting,
            assignmentSection = assignment,
            mogakcoSection = mogakco,
        )
    }

    private fun renderMeetingSection(input: DashboardInput): String {
        val statusLine = if (input.isMeetingActive) {
            "현재 상태: 진행 중"
        } else {
            "현재 상태: 진행 중인 회의 없음"
        }

        val lastMeetingLine = input.lastMeetingThreadId?.let { "마지막 회의: <#$it>" } ?: "마지막 회의: 기록 없음"
        val agenda = input.todayAgenda
        val agendaLine = if (agenda == null) {
            "오늘 안건 링크: 미등록 (`안건 설정`으로 등록)"
        } else {
            val title = agenda.title?.ifBlank { "오늘 안건" } ?: "오늘 안건"
            "오늘 안건: $title (하단 링크 버튼)"
        }

        return listOf(statusLine, lastMeetingLine, agendaLine).joinToString("\n")
    }

    private fun renderAssignmentSection(input: DashboardInput): String {
        if (input.dueSoonTop3.isNotEmpty()) {
            val dueLines = input.dueSoonTop3.joinToString("\n") { due ->
                "• ${due.ddayLabel} [${due.id}] ${due.title} (${due.dueAtKst})"
            }
            return buildString {
                appendLine("마감 임박 Top ${input.dueSoonTop3.size}")
                append(dueLines)
            }
        }

        val pending = input.pendingCount
        if (pending == null || pending <= 0) {
            return "마감 임박 과제가 없습니다. `과제 등록`으로 추가하세요."
        }

        return "마감 임박 과제 없음 (대기 과제 **${pending}건**)"
    }

    private fun renderMogakcoSection(input: DashboardInput): String {
        if (input.weeklyTop3.isEmpty()) {
            return "이번 주 모각코 요약이 없습니다. `더보기 > 모각코 전체 보기`로 확인하세요."
        }

        val topRows = input.weeklyTop3
            .take(3)
            .mapIndexed { index, row ->
                "${index + 1}. <@${row.userId}> ${row.formattedDuration}"
            }
            .joinToString("\n")
        return "이번 주 모각코 간단 요약 (Top ${input.weeklyTop3.size})\n$topRows"
    }

    data class DashboardInput(
        val guildName: String?,
        val isMeetingActive: Boolean,
        val lastMeetingThreadId: Long?,
        val todayAgenda: AgendaSummary?,
        val pendingCount: Int?,
        val dueSoonTop3: List<DueTaskSummary>,
        val weeklyTop3: List<MogakcoSummary>,
    )

    data class AgendaSummary(
        val title: String?,
        val url: String,
    )

    data class MogakcoSummary(
        val userId: Long,
        val formattedDuration: String,
    )

    data class DueTaskSummary(
        val id: Long,
        val title: String,
        val ddayLabel: String,
        val dueAtKst: String,
    )

    data class DashboardView(
        val title: String,
        val overview: String,
        val meetingSection: String,
        val assignmentSection: String,
        val mogakcoSection: String,
    )
}
