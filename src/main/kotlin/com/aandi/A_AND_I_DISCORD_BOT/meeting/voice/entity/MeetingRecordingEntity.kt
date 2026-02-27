package com.aandi.A_AND_I_DISCORD_BOT.meeting.voice.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "meeting_recordings")
class MeetingRecordingEntity(
    @Id
    @Column(name = "meeting_id")
    var meetingId: Long = 0,

    @Column(name = "voice_channel_id", nullable = false)
    var voiceChannelId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: MeetingRecordingStatus = MeetingRecordingStatus.DISABLED,

    @Column(name = "data_dir", nullable = false)
    var dataDir: String = "",

    @Column(name = "started_at_utc")
    var startedAtUtc: Instant? = null,

    @Column(name = "ended_at_utc")
    var endedAtUtc: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
