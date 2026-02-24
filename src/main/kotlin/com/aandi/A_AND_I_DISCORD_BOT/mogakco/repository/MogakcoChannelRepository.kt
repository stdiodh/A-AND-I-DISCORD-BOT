package com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository

import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.MogakcoChannel
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.MogakcoChannelId
import org.springframework.data.jpa.repository.JpaRepository

interface MogakcoChannelRepository : JpaRepository<MogakcoChannel, MogakcoChannelId> {
    fun existsByIdGuildIdAndIdChannelId(guildId: Long, channelId: Long): Boolean
    fun findAllByIdGuildIdOrderByIdChannelIdAsc(guildId: Long): List<MogakcoChannel>
}
