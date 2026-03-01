package com.aandi.A_AND_I_DISCORD_BOT.assignment.preference.repository

import com.aandi.A_AND_I_DISCORD_BOT.assignment.preference.entity.GuildUserTaskPreferenceEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GuildUserTaskPreferenceRepository :
    JpaRepository<GuildUserTaskPreferenceEntity, GuildUserTaskPreferenceEntity.GuildUserTaskPreferenceId> {

    fun findByGuildIdAndUserId(guildId: Long, userId: Long): GuildUserTaskPreferenceEntity?
}
