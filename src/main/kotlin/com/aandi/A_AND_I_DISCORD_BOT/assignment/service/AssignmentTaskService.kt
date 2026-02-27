package com.aandi.A_AND_I_DISCORD_BOT.assignment.service

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentStatus
import com.aandi.A_AND_I_DISCORD_BOT.assignment.entity.AssignmentTaskEntity
import com.aandi.A_AND_I_DISCORD_BOT.assignment.repository.AssignmentTaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Clock
import java.time.Instant

@Service
class AssignmentTaskService(
    private val assignmentTaskRepository: AssignmentTaskRepository,
    private val guildConfigService: GuildConfigService,
    private val clock: Clock,
) {

    @Transactional
    fun create(
        guildId: Long,
        channelId: Long,
        title: String,
        verifyUrl: String,
        remindAtUtc: Instant,
        createdBy: Long,
        nowUtc: Instant,
    ): CreateResult {
        val normalizedTitle = normalizeTitle(title) ?: return CreateResult.InvalidTitle
        val normalizedUrl = validateUrl(verifyUrl) ?: return CreateResult.InvalidUrl
        if (!remindAtUtc.isAfter(nowUtc)) {
            return CreateResult.RemindAtMustBeFuture
        }

        guildConfigService.getOrCreate(guildId)
        val saved = assignmentTaskRepository.save(
            AssignmentTaskEntity(
                guildId = guildId,
                channelId = channelId,
                title = normalizedTitle,
                verifyUrl = normalizedUrl,
                remindAt = remindAtUtc,
                status = AssignmentStatus.PENDING,
                createdBy = createdBy,
                createdAt = nowUtc,
                updatedAt = nowUtc,
            ),
        )
        return CreateResult.Success(saved.toView())
    }

    @Transactional(readOnly = true)
    fun list(guildId: Long, rawStatus: String?): ListResult {
        val statusFilter = resolveStatusFilter(rawStatus)
        if (statusFilter is StatusFilter.Invalid) {
            return ListResult.InvalidStatus
        }

        val tasks = when (statusFilter) {
            StatusFilter.All -> assignmentTaskRepository.findByGuildIdOrderByRemindAtDescCreatedAtDesc(guildId)
            is StatusFilter.Selected -> assignmentTaskRepository.findByGuildIdAndStatusOrderByRemindAtDescCreatedAtDesc(
                guildId = guildId,
                status = statusFilter.status,
            )
            StatusFilter.Invalid -> emptyList()
        }
        return ListResult.Success(tasks.map { it.toView() })
    }

    @Transactional(readOnly = true)
    fun detail(guildId: Long, id: Long): DetailResult {
        val task = assignmentTaskRepository.findByGuildIdAndId(guildId, id) ?: return DetailResult.NotFound
        return DetailResult.Success(task.toView())
    }

    @Transactional
    fun markDone(guildId: Long, id: Long): UpdateResult {
        val task = assignmentTaskRepository.findByGuildIdAndId(guildId, id) ?: return UpdateResult.NotFound
        task.status = AssignmentStatus.DONE
        task.updatedAt = Instant.now(clock)
        assignmentTaskRepository.save(task)
        return UpdateResult.Success(task.toView())
    }

    @Transactional
    fun cancel(guildId: Long, id: Long): UpdateResult {
        val task = assignmentTaskRepository.findByGuildIdAndId(guildId, id) ?: return UpdateResult.NotFound
        task.status = AssignmentStatus.CANCELED
        task.updatedAt = Instant.now(clock)
        assignmentTaskRepository.save(task)
        return UpdateResult.Success(task.toView())
    }

    @Transactional
    fun findDueTasksForNotification(
        nowUtc: Instant,
        graceStartUtc: Instant,
        limit: Int,
    ): List<AssignmentTaskView> {
        if (limit <= 0) {
            return emptyList()
        }

        val dueTasks = assignmentTaskRepository.findDueTasksForUpdate(
            nowUtc = nowUtc,
            graceStartUtc = graceStartUtc,
            limit = limit,
        )
        return dueTasks.map { it.toView() }
    }

    @Transactional
    fun claimOneDueTask(
        nowUtc: Instant,
        graceStartUtc: Instant,
    ): AssignmentTaskView? = lockNextDueTask(nowUtc, graceStartUtc)

    @Transactional
    fun lockNextDueTask(
        nowUtc: Instant,
        graceStartUtc: Instant,
    ): AssignmentTaskView? {
        val dueTasks = assignmentTaskRepository.findDueTasksForUpdate(
            nowUtc = nowUtc,
            graceStartUtc = graceStartUtc,
            limit = 1,
        )
        return dueTasks.firstOrNull()?.toView()
    }

    @Transactional
    fun markNotified(taskId: Long, notifiedAtUtc: Instant): Boolean {
        val updatedCount = assignmentTaskRepository.markNotifiedIfPending(taskId, notifiedAtUtc)
        return updatedCount > 0
    }

    @Transactional
    fun cancelPendingForNonRetryable(taskId: Long, updatedAtUtc: Instant): Boolean {
        val updatedCount = assignmentTaskRepository.cancelPendingTask(taskId, updatedAtUtc)
        return updatedCount > 0
    }

    private fun resolveStatusFilter(rawStatus: String?): StatusFilter {
        if (rawStatus.isNullOrBlank()) {
            return StatusFilter.All
        }
        val mapped = AssignmentStatus.fromFilter(rawStatus) ?: return StatusFilter.Invalid
        return StatusFilter.Selected(mapped)
    }

    private fun normalizeTitle(rawTitle: String): String? {
        val trimmed = rawTitle.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (trimmed.length > 200) {
            return null
        }
        return trimmed
    }

    private fun validateUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return null
        }
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") {
            return null
        }
        if (uri.host.isNullOrBlank()) {
            return null
        }
        return uri.toString()
    }

    private fun AssignmentTaskEntity.toView(): AssignmentTaskView = AssignmentTaskView(
        id = requireNotNull(id),
        guildId = guildId,
        channelId = channelId,
        title = title,
        verifyUrl = verifyUrl,
        remindAt = remindAt,
        status = status,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        notifiedAt = notifiedAt,
    )

    data class AssignmentTaskView(
        val id: Long,
        val guildId: Long,
        val channelId: Long,
        val title: String,
        val verifyUrl: String,
        val remindAt: Instant,
        val status: AssignmentStatus,
        val createdBy: Long,
        val createdAt: Instant,
        val updatedAt: Instant,
        val notifiedAt: Instant?,
    )

    sealed interface CreateResult {
        data class Success(val task: AssignmentTaskView) : CreateResult
        data object InvalidUrl : CreateResult
        data object InvalidTitle : CreateResult
        data object RemindAtMustBeFuture : CreateResult
    }

    sealed interface ListResult {
        data class Success(val tasks: List<AssignmentTaskView>) : ListResult
        data object InvalidStatus : ListResult
    }

    sealed interface DetailResult {
        data class Success(val task: AssignmentTaskView) : DetailResult
        data object NotFound : DetailResult
    }

    sealed interface UpdateResult {
        data class Success(val task: AssignmentTaskView) : UpdateResult
        data object NotFound : UpdateResult
    }

    private sealed interface StatusFilter {
        data object All : StatusFilter
        data class Selected(val status: AssignmentStatus) : StatusFilter
        data object Invalid : StatusFilter
    }
}
