package com.aandi.A_AND_I_DISCORD_BOT.agenda.repository

import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.AgendaLink
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface AgendaLinkRepository : JpaRepository<AgendaLink, Long> {
    fun findByGuildIdAndDateLocal(guildId: Long, dateLocal: LocalDate): AgendaLink?
}
