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
            overview = "서버: **$safeName**\n버튼으로 회의/안건/과제/모각코를 바로 실행하세요.",
            meetingSection = meeting,
            assignmentSection = assignment,
            mogakcoSection = mogakco,
        )
    }

    private fun renderMeetingSection(input: DashboardInput): String {
        val agenda = input.todayAgenda
        if (agenda == null) {
            return "오늘 안건 링크가 없습니다. `안건 설정` 버튼으로 등록하세요."
        }

        val title = agenda.title?.ifBlank { "오늘 안건" } ?: "오늘 안건"
        return "$title\n링크 버튼으로 바로 이동할 수 있습니다."
    }

    private fun renderAssignmentSection(input: DashboardInput): String {
        val pending = input.pendingCount
        if (pending == null || pending <= 0) {
            return "대기 과제가 없습니다. `과제 등록/과제 목록` 버튼을 사용하세요."
        }

        return "대기 과제 **${pending}건**"
    }

    private fun renderMogakcoSection(input: DashboardInput): String {
        if (input.weeklyTop3.isEmpty()) {
            return "이번 주 모각코 기록이 없습니다. `모각코 랭킹` 버튼으로 집계를 확인하세요."
        }

        return input.weeklyTop3
            .take(3)
            .mapIndexed { index, row ->
                "${index + 1}. <@${row.userId}> ${row.formattedDuration}"
            }
            .joinToString("\n")
    }

    data class DashboardInput(
        val guildName: String?,
        val todayAgenda: AgendaSummary?,
        val pendingCount: Int?,
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

    data class DashboardView(
        val title: String,
        val overview: String,
        val meetingSection: String,
        val assignmentSection: String,
        val mogakcoSection: String,
    )
}
