package com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe

class DashboardRendererTest : FunSpec({

    test("빈 데이터에서도 안전하게 대시보드 섹션을 생성한다") {
        val renderer = DashboardRenderer()

        val view = renderer.render(
            DashboardRenderer.DashboardInput(
                guildName = null,
                isMeetingActive = false,
                lastMeetingThreadId = null,
                todayAgenda = null,
                pendingCount = null,
                dueSoonTop3 = emptyList(),
                weeklyTop3 = emptyList(),
            ),
        )

        view.title shouldBe "A&I 운영 홈"
        view.overview shouldContain "서버: **A&I**"
        view.meetingSection shouldContain "진행 중인 회의 없음"
        view.assignmentSection shouldContain "마감 임박 과제가 없습니다"
        view.mogakcoSection shouldContain "모각코 요약이 없습니다"
    }

    test("데이터가 있으면 각 섹션에 요약을 반영한다") {
        val renderer = DashboardRenderer()

        val view = renderer.render(
            DashboardRenderer.DashboardInput(
                guildName = "A&I Dev Guild",
                isMeetingActive = true,
                lastMeetingThreadId = 1234L,
                todayAgenda = DashboardRenderer.AgendaSummary(
                    title = "오늘 안건",
                    url = "https://docs.example.com/agenda",
                ),
                pendingCount = 3,
                dueSoonTop3 = listOf(
                    DashboardRenderer.DueTaskSummary(
                        id = 77L,
                        title = "배포 체크",
                        ddayLabel = "D-1",
                        dueAtKst = "2026-03-02 10:00",
                    ),
                ),
                weeklyTop3 = listOf(
                    DashboardRenderer.MogakcoSummary(1001L, "03:20"),
                    DashboardRenderer.MogakcoSummary(1002L, "02:10"),
                ),
            ),
        )

        view.overview shouldContain "A&I Dev Guild"
        view.meetingSection shouldContain "진행 중"
        view.meetingSection shouldContain "<#1234>"
        view.assignmentSection shouldContain "D-1"
        view.mogakcoSection shouldContain "<@1001>"
    }
})
