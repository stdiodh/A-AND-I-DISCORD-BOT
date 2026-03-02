package com.aandi.A_AND_I_DISCORD_BOT.meeting.structured.repository

import com.aandi.A_AND_I_DISCORD_BOT.meeting.structured.entity.MeetingStructuredItemEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MeetingStructuredItemRepository : JpaRepository<MeetingStructuredItemEntity, Long> {

    fun findByMeetingSessionIdOrderByCreatedAtAsc(meetingSessionId: Long): List<MeetingStructuredItemEntity>
    fun findByMeetingSessionIdAndCanceledAtIsNullOrderByCreatedAtAsc(meetingSessionId: Long): List<MeetingStructuredItemEntity>
    fun findByMeetingSessionIdAndIdAndCanceledAtIsNull(meetingSessionId: Long, id: Long): MeetingStructuredItemEntity?
}
