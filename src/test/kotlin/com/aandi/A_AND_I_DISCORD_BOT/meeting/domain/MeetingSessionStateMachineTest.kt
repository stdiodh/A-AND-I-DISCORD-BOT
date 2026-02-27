package com.aandi.A_AND_I_DISCORD_BOT.meeting.domain

import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MeetingSessionStateMachineTest : FunSpec({

    test("세션 시작은 null 또는 ENDED에서 ACTIVE로 전이한다") {
        val stateMachine = MeetingSessionStateMachine()

        val fromNone = stateMachine.start(null)
        val fromEnded = stateMachine.start(MeetingSessionStatus.ENDED)

        (fromNone as MeetingSessionStateMachine.Transition.Changed).to shouldBe MeetingSessionStatus.ACTIVE
        (fromEnded as MeetingSessionStateMachine.Transition.Changed).to shouldBe MeetingSessionStatus.ACTIVE
    }

    test("ACTIVE 세션에서 다시 시작하면 거부한다") {
        val stateMachine = MeetingSessionStateMachine()

        val result = stateMachine.start(MeetingSessionStatus.ACTIVE)

        (result is MeetingSessionStateMachine.Transition.Rejected) shouldBe true
    }

    test("ACTIVE 세션 종료는 ENDED로 전이하고 그 외는 거부한다") {
        val stateMachine = MeetingSessionStateMachine()

        val ended = stateMachine.end(MeetingSessionStatus.ACTIVE)
        val endedAgain = stateMachine.end(MeetingSessionStatus.ENDED)
        val noSession = stateMachine.end(null)

        (ended as MeetingSessionStateMachine.Transition.Changed).to shouldBe MeetingSessionStatus.ENDED
        (endedAgain is MeetingSessionStateMachine.Transition.Rejected) shouldBe true
        (noSession is MeetingSessionStateMachine.Transition.Rejected) shouldBe true
    }
})
