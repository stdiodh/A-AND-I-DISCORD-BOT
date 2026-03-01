package com.aandi.A_AND_I_DISCORD_BOT.assignment.preference.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

@Entity
@Table(name = "guild_user_task_preferences")
@IdClass(GuildUserTaskPreferenceEntity.GuildUserTaskPreferenceId::class)
class GuildUserTaskPreferenceEntity(
    @Id
    @Column(name = "guild_id", nullable = false)
    var guildId: Long = 0,

    @Id
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "last_task_channel_id")
    var lastTaskChannelId: Long? = null,

    @Column(name = "last_notify_role_id")
    var lastNotifyRoleId: Long? = null,

    @Column(name = "last_mention_enabled", nullable = false)
    var lastMentionEnabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {

    data class GuildUserTaskPreferenceId(
        var guildId: Long = 0,
        var userId: Long = 0,
    ) : Serializable
}
