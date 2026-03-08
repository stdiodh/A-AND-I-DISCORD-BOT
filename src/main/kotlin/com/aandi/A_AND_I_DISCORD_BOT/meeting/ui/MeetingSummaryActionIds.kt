package com.aandi.A_AND_I_DISCORD_BOT.meeting.ui

object MeetingSummaryActionIds {
    fun regenerate(sessionId: Long): String = "meeting:summary:regen:$sessionId"
    fun addDecision(sessionId: Long): String = "meeting:summary:add_decision:$sessionId"
    fun addAction(sessionId: Long): String = "meeting:summary:add_action:$sessionId"
    fun addTodo(sessionId: Long): String = "meeting:summary:add_todo:$sessionId"
    fun itemManage(sessionId: Long): String = "meeting:summary:item_manage:$sessionId"
    fun source(sessionId: Long): String = "meeting:summary:source:$sessionId"

    fun decisionModal(sessionId: Long): String = "meeting:summary:modal:decision:$sessionId"
    fun actionModal(sessionId: Long): String = "meeting:summary:modal:action:$sessionId"
    fun todoModal(sessionId: Long): String = "meeting:summary:modal:todo:$sessionId"
}
