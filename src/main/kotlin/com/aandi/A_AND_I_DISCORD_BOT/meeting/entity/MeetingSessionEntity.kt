package com.aandi.A_AND_I_DISCORD_BOT.meeting.entity

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
@Table(name = "meeting_sessions")
class MeetingSessionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "guild_id", nullable = false)
    var guildId: Long = 0,

    @Column(name = "thread_id", nullable = false)
    var threadId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: MeetingSessionStatus = MeetingSessionStatus.ACTIVE,

    @Column(name = "started_by", nullable = false)
    var startedBy: Long = 0,

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.now(),

    @Column(name = "ended_by")
    var endedBy: Long? = null,

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column(name = "summary_message_id")
    var summaryMessageId: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
