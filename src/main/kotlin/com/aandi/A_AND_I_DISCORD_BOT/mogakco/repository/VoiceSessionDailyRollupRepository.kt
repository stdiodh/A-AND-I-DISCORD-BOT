package com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository

import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.VoiceSessionDailyRollup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface VoiceSessionDailyRollupRepository : JpaRepository<VoiceSessionDailyRollup, Long> {

    @Modifying
    @Query(
        value = """
            INSERT INTO voice_session_daily_rollups (
                guild_id,
                user_id,
                date_local,
                total_seconds,
                created_at,
                updated_at
            )
            VALUES (
                :guildId,
                :userId,
                :dateLocal,
                :seconds,
                NOW(),
                NOW()
            )
            ON CONFLICT (guild_id, user_id, date_local)
            DO UPDATE
               SET total_seconds = voice_session_daily_rollups.total_seconds + EXCLUDED.total_seconds,
                   updated_at = NOW()
        """,
        nativeQuery = true,
    )
    fun upsertAddSeconds(
        @Param("guildId") guildId: Long,
        @Param("userId") userId: Long,
        @Param("dateLocal") dateLocal: LocalDate,
        @Param("seconds") seconds: Long,
    ): Int

    @Query(
        """
        select r.userId as userId, sum(r.totalSeconds) as totalSeconds
        from VoiceSessionDailyRollup r
        where r.guildId = :guildId
          and r.dateLocal >= :startDate
          and r.dateLocal < :endDateExclusive
        group by r.userId
        """,
    )
    fun aggregateUserTotals(
        @Param("guildId") guildId: Long,
        @Param("startDate") startDate: LocalDate,
        @Param("endDateExclusive") endDateExclusive: LocalDate,
    ): List<UserTotalProjection>

    @Query(
        """
        select r.dateLocal as dateLocal, r.totalSeconds as totalSeconds
        from VoiceSessionDailyRollup r
        where r.guildId = :guildId
          and r.userId = :userId
          and r.dateLocal >= :startDate
          and r.dateLocal < :endDateExclusive
        """,
    )
    fun findUserDailyTotals(
        @Param("guildId") guildId: Long,
        @Param("userId") userId: Long,
        @Param("startDate") startDate: LocalDate,
        @Param("endDateExclusive") endDateExclusive: LocalDate,
    ): List<UserDailyTotalProjection>

    interface UserTotalProjection {
        val userId: Long
        val totalSeconds: Long
    }

    interface UserDailyTotalProjection {
        val dateLocal: LocalDate
        val totalSeconds: Long
    }
}
