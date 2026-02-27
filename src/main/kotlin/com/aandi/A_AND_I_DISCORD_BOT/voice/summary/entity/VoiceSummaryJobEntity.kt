package com.aandi.A_AND_I_DISCORD_BOT.voice.summary.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "voice_summary_jobs")
class VoiceSummaryJobEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "guild_id", nullable = false)
    var guildId: Long = 0,

    @Column(name = "meeting_thread_id")
    var meetingThreadId: Long? = null,

    @Column(name = "voice_channel_id", nullable = false)
    var voiceChannelId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    var status: VoiceSummaryStatus = VoiceSummaryStatus.READY,

    @Column(name = "data_dir", nullable = false)
    var dataDir: String = "",

    @Column(name = "max_minutes", nullable = false)
    var maxMinutes: Int = 120,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column(name = "created_by", nullable = false)
    var createdBy: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "last_error")
    var lastError: String? = null,
)

