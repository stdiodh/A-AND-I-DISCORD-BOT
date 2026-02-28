package com.aandi.A_AND_I_DISCORD_BOT.assignment.repository

import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentStatus
import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentTaskEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
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
              AND notified_at IS NULL
              AND remind_at <= :nowUtc
              AND remind_at >= :graceStartUtc
            ORDER BY remind_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findDueTasksForUpdate(
        @Param("nowUtc") nowUtc: Instant,
        @Param("graceStartUtc") graceStartUtc: Instant,
        @Param("limit") limit: Int,
    ): List<AssignmentTaskEntity>

    @Query(
        value = """
            SELECT *
            FROM assignment_tasks
            WHERE status = 'PENDING'
              AND notified_at IS NULL
              AND remind_at <= :nowUtc
              AND remind_at >= :graceStartUtc
            ORDER BY remind_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockInitialReminderTasks(
        @Param("nowUtc") nowUtc: Instant,
        @Param("graceStartUtc") graceStartUtc: Instant,
        @Param("limit") limit: Int,
    ): List<AssignmentTaskEntity>

    @Query(
        value = """
            SELECT *
            FROM assignment_tasks
            WHERE status = 'PENDING'
              AND closed_at IS NULL
              AND due_at <= :nowUtc
              AND due_at >= :graceStartUtc
            ORDER BY due_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockDueClosingTasks(
        @Param("nowUtc") nowUtc: Instant,
        @Param("graceStartUtc") graceStartUtc: Instant,
        @Param("limit") limit: Int,
    ): List<AssignmentTaskEntity>

    @Query(
        value = """
            SELECT *
            FROM assignment_tasks
            WHERE status = 'PENDING'
              AND closed_at IS NULL
              AND due_at > :nowUtc
              AND due_at <= :scanEndUtc
              AND due_at >= :graceStartUtc
              AND pre_remind_hours_json IS NOT NULL
            ORDER BY due_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockPreReminderCandidates(
        @Param("nowUtc") nowUtc: Instant,
        @Param("scanEndUtc") scanEndUtc: Instant,
        @Param("graceStartUtc") graceStartUtc: Instant,
        @Param("limit") limit: Int,
    ): List<AssignmentTaskEntity>

    @Modifying
    @Query(
        value = """
            UPDATE assignment_tasks
            SET notified_at = :notifiedAtUtc,
                updated_at = :notifiedAtUtc
            WHERE id = :taskId
              AND status = 'PENDING'
              AND notified_at IS NULL
        """,
        nativeQuery = true,
    )
    fun markNotifiedIfPending(
        @Param("taskId") taskId: Long,
        @Param("notifiedAtUtc") notifiedAtUtc: Instant,
    ): Int

    @Modifying
    @Query(
        value = """
            UPDATE assignment_tasks
            SET status = 'CANCELED',
                updated_at = :updatedAtUtc
            WHERE id = :taskId
              AND status = 'PENDING'
        """,
        nativeQuery = true,
    )
    fun cancelPendingTask(
        @Param("taskId") taskId: Long,
        @Param("updatedAtUtc") updatedAtUtc: Instant,
    ): Int
}
