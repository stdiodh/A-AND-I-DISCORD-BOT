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
                todayAgenda = null,
                pendingCount = null,
                nextDueTask = null,
                todayParticipantCount = 0,
            ),
        )

        view.title shouldBe "A&I 홈"
        view.overview shouldContain "서버: **A&I**"
        view.statusLine shouldContain "안건 없음"
        view.statusLine shouldContain "진행 중 회의 없음"
        view.statusLine shouldContain "미완료 과제 0개"
        view.todoSection shouldContain "진행 중 회의 없음"
    }

    test("데이터가 있으면 각 섹션에 요약을 반영한다") {
        val renderer = DashboardRenderer()

        val view = renderer.render(
            DashboardRenderer.DashboardInput(
                guildName = "A&I Dev Guild",
                isMeetingActive = true,
                todayAgenda = DashboardRenderer.AgendaSummary(
                    title = "오늘 안건",
                    url = "https://docs.example.com/agenda",
                ),
                pendingCount = 3,
                nextDueTask = DashboardRenderer.DueTaskSummary(
                    title = "배포 체크",
                    dueAtKst = "2026-03-02 10:00",
                ),
                todayParticipantCount = 9,
            ),
        )

        view.overview shouldContain "A&I Dev Guild"
        view.statusLine shouldContain "안건 있음"
        view.statusLine shouldContain "진행 중 회의 있음"
        view.statusLine shouldContain "미완료 과제 3개"
        view.statusLine shouldContain "오늘 모각코 9명"
        view.todoSection shouldContain "배포 체크"
    }
})
