package com.aandi.A_AND_I_DISCORD_BOT.agenda.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "guild_config")
class GuildConfig(
    @Id
    @Column(name = "guild_id", nullable = false)
    var guildId: Long = 0,

    @Column(name = "timezone", nullable = false, length = 64)
    var timezone: String = "Asia/Seoul",

    @Column(name = "admin_role_id")
    var adminRoleId: Long? = null,

    @Column(name = "mogakco_active_minutes", nullable = false)
    var mogakcoActiveMinutes: Int = 30,

    @Column(name = "dashboard_channel_id")
    var dashboardChannelId: Long? = null,

    @Column(name = "dashboard_message_id")
    var dashboardMessageId: Long? = null,
)
