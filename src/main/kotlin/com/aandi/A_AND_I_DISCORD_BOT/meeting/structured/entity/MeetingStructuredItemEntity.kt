package com.aandi.A_AND_I_DISCORD_BOT.meeting.structured.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "meeting_structured_items")
class MeetingStructuredItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "meeting_session_id", nullable = false)
    var meetingSessionId: Long = 0,

    @Column(name = "guild_id", nullable = false)
    var guildId: Long = 0,

    @Column(name = "thread_id", nullable = false)
    var threadId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 16)
    var itemType: MeetingStructuredItemType = MeetingStructuredItemType.DECISION,

    @Column(name = "content", nullable = false)
    var content: String = "",

    @Column(name = "assignee_user_id")
    var assigneeUserId: Long? = null,

    @Column(name = "due_date_local")
    var dueDateLocal: LocalDate? = null,

    @Column(name = "source", nullable = false, length = 16)
    var source: String = "SLASH",

    @Column(name = "source_message_id")
    var sourceMessageId: Long? = null,

    @Column(name = "created_by", nullable = false)
    var createdBy: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
