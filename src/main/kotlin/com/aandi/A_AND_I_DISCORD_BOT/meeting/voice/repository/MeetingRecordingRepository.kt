package com.aandi.A_AND_I_DISCORD_BOT.meeting.voice.repository

import com.aandi.A_AND_I_DISCORD_BOT.meeting.voice.entity.MeetingRecordingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MeetingRecordingRepository : JpaRepository<MeetingRecordingEntity, Long>
