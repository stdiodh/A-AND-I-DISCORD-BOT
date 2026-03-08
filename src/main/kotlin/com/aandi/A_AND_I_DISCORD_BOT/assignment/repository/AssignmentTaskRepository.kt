package com.aandi.A_AND_I_DISCORD_BOT.assignment.repository

import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentStatus
import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentTaskEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface AssignmentTaskRepository : JpaRepository<AssignmentTaskEntity, Long> {

    fun findByGuildIdAndId(guildId: Long, id: Long): AssignmentTaskEntity?

    fun findByGuildIdOrderByRemindAtDescCreatedAtDesc(guildId: Long): List<AssignmentTaskEntity>

    fun findByGuildIdAndStatusOrderByRemindAtDescCreatedAtDesc(
        guildId: Long,
        status: AssignmentStatus,
    ): List<AssignmentTaskEntity>

    fun findByGuildIdAndStatusInOrderByRemindAtDescCreatedAtDesc(
        guildId: Long,
        statuses: Collection<AssignmentStatus>,
    ): List<AssignmentTaskEntity>

    @Query(
        value = """
            SELECT *
            FROM assignment_tasks
            WHERE status = 'PENDING'
              AND next_fire_at IS NOT NULL
              AND next_fire_at <= :nowUtc
              AND next_fire_at >= :graceStartUtc
            ORDER BY next_fire_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockReadyTasksByNextFire(
        @Param("nowUtc") nowUtc: Instant,
        @Param("graceStartUtc") graceStartUtc: Instant,
        @Param("limit") limit: Int,
    ): List<AssignmentTaskEntity>
}
