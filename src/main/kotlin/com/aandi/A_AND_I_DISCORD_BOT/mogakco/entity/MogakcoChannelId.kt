package com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class MogakcoChannelId(
    @Column(name = "guild_id", nullable = false)
    var guildId: Long = 0,

    @Column(name = "channel_id", nullable = false)
    var channelId: Long = 0,
) : Serializable
