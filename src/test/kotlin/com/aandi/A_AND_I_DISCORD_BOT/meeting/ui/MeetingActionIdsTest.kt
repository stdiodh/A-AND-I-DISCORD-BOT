package com.aandi.A_AND_I_DISCORD_BOT.meeting.ui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MeetingActionIdsTest : FunSpec({

    test("요약 액션 ID는 TODO/항목수정 경로를 포함한다") {
        MeetingSummaryActionIds.addTodo(42L) shouldBe "meeting:summary:add_todo:42"
        MeetingSummaryActionIds.itemManage(42L) shouldBe "meeting:summary:item_manage:42"
        MeetingSummaryActionIds.todoModal(42L) shouldBe "meeting:summary:modal:todo:42"
    }

    test("스레드 액션 ID는 기록/종료 패널 경로를 포함한다") {
        MeetingThreadActionIds.addDecision(42L) shouldBe "meeting:thread:add_decision:42"
        MeetingThreadActionIds.addAction(42L) shouldBe "meeting:thread:add_action:42"
        MeetingThreadActionIds.addTodo(42L) shouldBe "meeting:thread:add_todo:42"
        MeetingThreadActionIds.endMeeting(42L) shouldBe "meeting:thread:end:42"
        MeetingThreadActionIds.todoModal(42L) shouldBe "meeting:thread:modal:todo:42"
    }
})
