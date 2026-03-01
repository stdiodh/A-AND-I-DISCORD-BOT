package com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.repository

import com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.entity.MeetingSummaryArtifactEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MeetingSummaryArtifactRepository : JpaRepository<MeetingSummaryArtifactEntity, Long> {

    fun findFirstByMeetingSessionIdOrderByGeneratedAtDesc(meetingSessionId: Long): MeetingSummaryArtifactEntity?

    fun findFirstByGuildIdAndThreadIdOrderByGeneratedAtDesc(
        guildId: Long,
        threadId: Long,
    ): MeetingSummaryArtifactEntity?
}
