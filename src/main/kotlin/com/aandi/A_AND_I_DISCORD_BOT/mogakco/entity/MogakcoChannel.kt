package com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "mogakco_channels")
class MogakcoChannel(
    @EmbeddedId
    var id: MogakcoChannelId = MogakcoChannelId(),
)
