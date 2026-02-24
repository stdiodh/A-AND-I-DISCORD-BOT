package com.aandi.A_AND_I_DISCORD_BOT.mogakco.service

import java.time.Instant

data class VoiceTransition(
    val guildId: Long,
    val userId: Long,
    val oldChannelId: Long?,
    val newChannelId: Long?,
    val occurredAt: Instant,
)
