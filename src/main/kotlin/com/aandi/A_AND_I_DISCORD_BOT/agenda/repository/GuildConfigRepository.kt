package com.aandi.A_AND_I_DISCORD_BOT.agenda.repository

import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.GuildConfig
import org.springframework.data.jpa.repository.JpaRepository

interface GuildConfigRepository : JpaRepository<GuildConfig, Long>
