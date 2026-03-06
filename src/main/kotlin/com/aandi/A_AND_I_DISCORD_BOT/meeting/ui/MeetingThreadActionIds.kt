package com.aandi.A_AND_I_DISCORD_BOT.meeting.ui

object MeetingThreadActionIds {
    fun addDecision(sessionId: Long): String = "meeting:thread:add_decision:$sessionId"
    fun addAction(sessionId: Long): String = "meeting:thread:add_action:$sessionId"
    fun addTodo(sessionId: Long): String = "meeting:thread:add_todo:$sessionId"
    fun endMeeting(sessionId: Long): String = "meeting:thread:end:$sessionId"

    fun decisionModal(sessionId: Long): String = "meeting:thread:modal:decision:$sessionId"
    fun actionModal(sessionId: Long): String = "meeting:thread:modal:action:$sessionId"
    fun todoModal(sessionId: Long): String = "meeting:thread:modal:todo:$sessionId"
}
