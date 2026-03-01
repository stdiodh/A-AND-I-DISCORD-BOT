package com.aandi.A_AND_I_DISCORD_BOT.meeting.summary.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "meeting_summary_artifacts")
class MeetingSummaryArtifactEntity(
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

    @Column(name = "summary_message_id")
    var summaryMessageId: Long? = null,

    @Column(name = "message_count", nullable = false)
    var messageCount: Int = 0,

    @Column(name = "participant_count", nullable = false)
    var participantCount: Int = 0,

    @Column(name = "decision_count", nullable = false)
    var decisionCount: Int = 0,

    @Column(name = "action_count", nullable = false)
    var actionCount: Int = 0,

    @Column(name = "todo_count", nullable = false)
    var todoCount: Int = 0,

    @Column(name = "generated_at", nullable = false)
    var generatedAt: Instant = Instant.now(),

    @Column(name = "version", nullable = false, length = 16)
    var version: String = "v2",

    @Column(name = "source_window_start", nullable = false)
    var sourceWindowStart: Instant = Instant.now(),

    @Column(name = "source_window_end", nullable = false)
    var sourceWindowEnd: Instant = Instant.now(),

    @Column(name = "source_buffer_seconds", nullable = false)
    var sourceBufferSeconds: Int = 0,

    @Column(name = "decisions_text")
    var decisionsText: String? = null,

    @Column(name = "actions_text")
    var actionsText: String? = null,

    @Column(name = "todos_text")
    var todosText: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
