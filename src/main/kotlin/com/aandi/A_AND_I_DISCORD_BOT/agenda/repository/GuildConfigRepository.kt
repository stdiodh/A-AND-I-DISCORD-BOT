package com.aandi.A_AND_I_DISCORD_BOT.agenda.repository

import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.GuildConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GuildConfigRepository : JpaRepository<GuildConfig, Long> {

    @Modifying
    @Query(
        value = """
            INSERT INTO guild_config (guild_id)
            VALUES (:guildId)
            ON CONFLICT (guild_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun createDefaultIfAbsent(
        @Param("guildId") guildId: Long,
    ): Int
}
