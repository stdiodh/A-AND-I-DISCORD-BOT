package com.aandi.A_AND_I_DISCORD_BOT.meeting.domain

import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import org.springframework.stereotype.Component

@Component
class MeetingSessionStateMachine {

    fun start(current: MeetingSessionStatus?): Transition {
        if (current == null) {
            return Transition.Changed(MeetingSessionStatus.ACTIVE)
        }
        if (current == MeetingSessionStatus.ENDED) {
            return Transition.Changed(MeetingSessionStatus.ACTIVE)
        }
        return Transition.Rejected("이미 진행 중인 회의가 있습니다.")
    }

    fun end(current: MeetingSessionStatus?): Transition {
        if (current == null) {
            return Transition.Rejected("종료할 회의 세션이 없습니다.")
        }
        if (current == MeetingSessionStatus.ACTIVE) {
            return Transition.Changed(MeetingSessionStatus.ENDED)
        }
        return Transition.Rejected("이미 종료된 회의입니다.")
    }

    sealed interface Transition {
        data class Changed(val to: MeetingSessionStatus) : Transition
        data class Rejected(val reason: String) : Transition
    }
}
