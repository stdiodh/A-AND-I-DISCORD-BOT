package com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository

import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.VoiceSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface VoiceSessionRepository : JpaRepository<VoiceSession, Long> {
    fun findFirstByGuildIdAndUserIdAndLeftAtIsNullOrderByJoinedAtDesc(guildId: Long, userId: Long): VoiceSession?
    fun findByLeftAtIsNull(): List<VoiceSession>

    @Query(
        """
        select v
        from VoiceSession v
        where v.guildId = :guildId
          and v.joinedAt < :endExclusive
          and (v.leftAt is null or v.leftAt > :startInclusive)
        """,
    )
    fun findSessionsInRange(
        @Param("guildId") guildId: Long,
        @Param("startInclusive") startInclusive: Instant,
        @Param("endExclusive") endExclusive: Instant,
    ): List<VoiceSession>
}
