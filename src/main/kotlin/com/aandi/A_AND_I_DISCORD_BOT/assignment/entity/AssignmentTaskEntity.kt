package com.aandi.A_AND_I_DISCORD_BOT.assignment.entity

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
@Table(name = "assignment_tasks")
class AssignmentTaskEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "guild_id", nullable = false)
    var guildId: Long = 0,

    @Column(name = "channel_id", nullable = false)
    var channelId: Long = 0,

    @Column(name = "title", nullable = false, length = 200)
    var title: String = "",

    @Column(name = "verify_url", nullable = false)
    var verifyUrl: String = "",

    @Column(name = "remind_at", nullable = false)
    var remindAt: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: AssignmentStatus = AssignmentStatus.PENDING,

    @Column(name = "created_by", nullable = false)
    var createdBy: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "notified_at")
    var notifiedAt: Instant? = null,
)
