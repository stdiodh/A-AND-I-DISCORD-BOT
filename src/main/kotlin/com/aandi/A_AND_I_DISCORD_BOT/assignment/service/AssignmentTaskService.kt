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
import java.time.temporal.ChronoUnit

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
        verifyUrl: String?,
        remindAtUtc: Instant,
        dueAtUtc: Instant,
        createdBy: Long,
        nowUtc: Instant,
        notifyRoleId: Long?,
        preReminderHoursRaw: String?,
        closingMessageRaw: String?,
    ): CreateResult {
        val normalizedTitle = normalizeTitle(title) ?: return CreateResult.InvalidTitle
        val normalizedUrl = when (val urlValidation = normalizeVerifyUrl(verifyUrl)) {
            is VerifyUrlValidation.Valid -> urlValidation.value
            VerifyUrlValidation.Invalid -> return CreateResult.InvalidUrl
        }
        if (!remindAtUtc.isAfter(nowUtc)) {
            return CreateResult.RemindAtMustBeFuture
        }
        if (!dueAtUtc.isAfter(nowUtc)) {
            return CreateResult.DueAtMustBeFuture
        }
        if (dueAtUtc.isBefore(remindAtUtc)) {
            return CreateResult.DueAtMustBeAfterRemindAt
        }

        val preReminderHours = parsePreReminderHours(preReminderHoursRaw)
            ?: return CreateResult.InvalidPreReminderHours
        val normalizedClosingMessage = when (val closingResult = validateClosingMessage(closingMessageRaw)) {
            is ClosingMessageValidation.Valid -> closingResult.value
            ClosingMessageValidation.Invalid -> return CreateResult.InvalidClosingMessage
        }

        guildConfigService.getOrCreate(guildId)
        val entity = AssignmentTaskEntity(
            guildId = guildId,
            channelId = channelId,
            title = normalizedTitle,
            verifyUrl = normalizedUrl,
            remindAt = remindAtUtc,
            dueAt = dueAtUtc,
            notifyRoleId = notifyRoleId,
            preRemindHoursJson = serializeHours(preReminderHours),
            preNotifiedJson = serializeHours(emptySet()),
            closingMessage = normalizedClosingMessage,
            status = AssignmentStatus.PENDING,
            createdBy = createdBy,
            createdAt = nowUtc,
            updatedAt = nowUtc,
        )
        entity.nextFireAt = computeNextFireAt(entity, nowUtc)
        val saved = assignmentTaskRepository.save(entity)
        return CreateResult.Success(saved.toView())
    }

    @Transactional(readOnly = true)
    fun list(guildId: Long, rawStatus: String?): ListResult {
        val statusFilter = resolveStatusFilter(rawStatus)
        if (statusFilter is StatusFilter.Invalid) {
            return ListResult.InvalidStatus
        }
        if (statusFilter is StatusFilter.HiddenDeleted) {
            return ListResult.HiddenDeleted
        }

        val tasks = when (statusFilter) {
            StatusFilter.AllVisible -> assignmentTaskRepository.findByGuildIdAndStatusInOrderByRemindAtDescCreatedAtDesc(
                guildId = guildId,
                statuses = visibleListStatuses,
            )
            is StatusFilter.Selected -> assignmentTaskRepository.findByGuildIdAndStatusOrderByRemindAtDescCreatedAtDesc(
                guildId = guildId,
                status = statusFilter.status,
            )
            StatusFilter.Invalid -> emptyList()
            StatusFilter.HiddenDeleted -> emptyList()
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
        task.nextFireAt = null
        assignmentTaskRepository.save(task)
        return UpdateResult.Success(task.toView())
    }

    @Transactional
    fun cancel(guildId: Long, id: Long): UpdateResult {
        val task = assignmentTaskRepository.findByGuildIdAndId(guildId, id) ?: return UpdateResult.NotFound
        task.status = AssignmentStatus.CANCELED
        task.updatedAt = Instant.now(clock)
        task.nextFireAt = null
        assignmentTaskRepository.save(task)
        return UpdateResult.Success(task.toView())
    }

    @Transactional
    fun lockNextReadyTask(
        nowUtc: Instant,
        graceStartUtc: Instant,
    ): AssignmentTaskView? {
        val tasks = assignmentTaskRepository.lockReadyTasksByNextFire(
            nowUtc = nowUtc,
            graceStartUtc = graceStartUtc,
            limit = 1,
        )
        return tasks.firstOrNull()?.toView()
    }

    @Transactional
    fun refreshNextFireAt(taskId: Long, nowUtc: Instant): Boolean {
        val task = assignmentTaskRepository.findById(taskId).orElse(null) ?: return false
        val nextFireAt = computeNextFireAt(task, nowUtc)
        task.nextFireAt = nextFireAt
        task.updatedAt = nowUtc
        assignmentTaskRepository.save(task)
        return true
    }

    fun nextPendingAction(task: AssignmentTaskView, nowUtc: Instant): PendingAction {
        if (task.status != AssignmentStatus.PENDING) {
            return PendingAction.None
        }
        if (task.closedAt == null && !task.dueAt.isAfter(nowUtc)) {
            return PendingAction.CloseDue
        }
        if (task.notifiedAt == null && !task.remindAt.isAfter(nowUtc)) {
            return PendingAction.InitialReminder
        }
        if (!task.dueAt.isAfter(nowUtc)) {
            return PendingAction.None
        }
        val remainingMinutes = ChronoUnit.MINUTES.between(nowUtc, task.dueAt)
        if (remainingMinutes <= 0) {
            return PendingAction.None
        }

        val unsentHour = task.preRemindHours
            .sortedDescending()
            .firstOrNull { hour ->
                if (task.preNotifiedHours.contains(hour)) {
                    return@firstOrNull false
                }
                val triggerAt = task.dueAt.minus(hour.toLong(), ChronoUnit.HOURS)
                if (triggerAt.isAfter(nowUtc)) {
                    return@firstOrNull false
                }
                remainingMinutes >= hour.toLong() * 60L
            }
            ?: return PendingAction.None

        return PendingAction.PreDueReminder(unsentHour)
    }

    @Transactional
    fun markNotified(taskId: Long, notifiedAtUtc: Instant): Boolean {
        val task = assignmentTaskRepository.findById(taskId).orElse(null) ?: return false
        if (task.status != AssignmentStatus.PENDING || task.notifiedAt != null) {
            return false
        }
        task.notifiedAt = notifiedAtUtc
        task.nextFireAt = computeNextFireAt(task, notifiedAtUtc)
        task.updatedAt = notifiedAtUtc
        assignmentTaskRepository.save(task)
        return true
    }

    @Transactional
    fun markPreNotified(taskId: Long, hoursBeforeDue: Int, notifiedAtUtc: Instant): Boolean {
        val task = assignmentTaskRepository.findById(taskId).orElse(null) ?: return false
        if (task.status != AssignmentStatus.PENDING) {
            return false
        }

        val sentHours = parseHours(task.preNotifiedJson).toMutableSet()
        if (sentHours.contains(hoursBeforeDue)) {
            return true
        }

        sentHours.add(hoursBeforeDue)
        task.preNotifiedJson = serializeHours(sentHours)
        task.nextFireAt = computeNextFireAt(task, notifiedAtUtc)
        task.updatedAt = notifiedAtUtc
        assignmentTaskRepository.save(task)
        return true
    }

    @Transactional
    fun markClosed(taskId: Long, closedAtUtc: Instant): Boolean {
        val task = assignmentTaskRepository.findById(taskId).orElse(null) ?: return false
        if (task.status == AssignmentStatus.CLOSED) {
            return true
        }
        if (task.status != AssignmentStatus.PENDING) {
            return false
        }

        task.status = AssignmentStatus.CLOSED
        task.closedAt = closedAtUtc
        task.nextFireAt = null
        task.updatedAt = closedAtUtc
        assignmentTaskRepository.save(task)
        return true
    }

    @Transactional
    fun cancelPendingForNonRetryable(taskId: Long, updatedAtUtc: Instant): Boolean {
        val task = assignmentTaskRepository.findById(taskId).orElse(null) ?: return false
        if (task.status != AssignmentStatus.PENDING) {
            return false
        }
        task.status = AssignmentStatus.CANCELED
        task.nextFireAt = null
        task.updatedAt = updatedAtUtc
        assignmentTaskRepository.save(task)
        return true
    }

    fun parsePreReminderHours(raw: String?): Set<Int>? {
        if (raw.isNullOrBlank()) {
            return DEFAULT_PRE_REMINDER_HOURS
        }

        val numbers = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.toIntOrNull() }
        if (numbers.any { it == null }) {
            return null
        }

        val valid = numbers.map { it ?: 0 }
            .filter { it in 1..MAX_PRE_REMINDER_HOURS }
            .toSet()
        if (valid.isEmpty()) {
            return null
        }
        return valid
    }

    fun serializeHours(hours: Set<Int>): String {
        val normalized = hours
            .filter { it in 1..MAX_PRE_REMINDER_HOURS }
            .toSortedSet(compareByDescending { it })
        return normalized.joinToString(prefix = "[", postfix = "]", separator = ",")
    }

    fun parseHours(raw: String?): Set<Int> {
        if (raw.isNullOrBlank()) {
            return emptySet()
        }
        return HOUR_REGEX.findAll(raw)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..MAX_PRE_REMINDER_HOURS }
            .toSet()
    }

    private fun resolveStatusFilter(rawStatus: String?): StatusFilter {
        if (rawStatus.isNullOrBlank()) {
            return StatusFilter.AllVisible
        }
        val mapped = AssignmentStatus.fromFilter(rawStatus) ?: return StatusFilter.Invalid
        if (mapped == AssignmentStatus.CANCELED) {
            return StatusFilter.HiddenDeleted
        }
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

    private fun validateClosingMessage(rawMessage: String?): ClosingMessageValidation {
        if (rawMessage == null) {
            return ClosingMessageValidation.Valid(null)
        }
        val trimmed = rawMessage.trim()
        if (trimmed.isEmpty()) {
            return ClosingMessageValidation.Valid(null)
        }
        if (trimmed.length > MAX_CLOSING_MESSAGE_LENGTH) {
            return ClosingMessageValidation.Invalid
        }
        return ClosingMessageValidation.Valid(trimmed)
    }

    private fun normalizeVerifyUrl(rawUrl: String?): VerifyUrlValidation {
        if (rawUrl == null) {
            return VerifyUrlValidation.Valid(null)
        }
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return VerifyUrlValidation.Valid(null)
        }
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return VerifyUrlValidation.Invalid
        val scheme = uri.scheme?.lowercase() ?: return VerifyUrlValidation.Invalid
        if (scheme != "http" && scheme != "https") {
            return VerifyUrlValidation.Invalid
        }
        if (uri.host.isNullOrBlank()) {
            return VerifyUrlValidation.Invalid
        }
        return VerifyUrlValidation.Valid(uri.toString())
    }

    private fun computeNextFireAt(task: AssignmentTaskEntity, nowUtc: Instant): Instant? {
        val taskView = task.toScheduleView()
        return when (nextPendingAction(taskView, nowUtc)) {
            PendingAction.InitialReminder,
            is PendingAction.PreDueReminder,
            PendingAction.CloseDue,
            -> nowUtc

            PendingAction.None -> computeFutureNextFireAt(taskView, nowUtc)
        }
    }

    private fun computeFutureNextFireAt(task: AssignmentTaskView, nowUtc: Instant): Instant? {
        val candidates = mutableListOf<Instant>()
        if (task.status != AssignmentStatus.PENDING) {
            return null
        }

        if (task.notifiedAt == null) {
            if (task.remindAt.isAfter(nowUtc)) {
                candidates += task.remindAt
            }
        } else if (task.dueAt.isAfter(nowUtc)) {
            task.preRemindHours
                .filterNot { task.preNotifiedHours.contains(it) }
                .forEach { hours ->
                    val triggerAt = task.dueAt.minus(hours.toLong(), ChronoUnit.HOURS)
                    if (triggerAt.isAfter(nowUtc)) {
                        candidates += triggerAt
                    }
                }
        }

        if (task.closedAt == null && task.dueAt.isAfter(nowUtc)) {
            candidates += task.dueAt
        }
        return candidates.minOrNull()
    }

    private fun AssignmentTaskEntity.toView(): AssignmentTaskView = AssignmentTaskView(
        id = requireNotNull(id),
        guildId = guildId,
        channelId = channelId,
        title = title,
        verifyUrl = verifyUrl,
        remindAt = remindAt,
        dueAt = dueAt,
        notifyRoleId = notifyRoleId,
        preRemindHours = parseHours(preRemindHoursJson).ifEmpty { DEFAULT_PRE_REMINDER_HOURS },
        preNotifiedHours = parseHours(preNotifiedJson),
        closingMessage = closingMessage,
        status = status,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        notifiedAt = notifiedAt,
        closedAt = closedAt,
        nextFireAt = nextFireAt,
    )

    private fun AssignmentTaskEntity.toScheduleView(): AssignmentTaskView = AssignmentTaskView(
        id = id ?: 0L,
        guildId = guildId,
        channelId = channelId,
        title = title,
        verifyUrl = verifyUrl,
        remindAt = remindAt,
        dueAt = dueAt,
        notifyRoleId = notifyRoleId,
        preRemindHours = parseHours(preRemindHoursJson).ifEmpty { DEFAULT_PRE_REMINDER_HOURS },
        preNotifiedHours = parseHours(preNotifiedJson),
        closingMessage = closingMessage,
        status = status,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        notifiedAt = notifiedAt,
        closedAt = closedAt,
        nextFireAt = nextFireAt,
    )

    data class AssignmentTaskView(
        val id: Long,
        val guildId: Long,
        val channelId: Long,
        val title: String,
        val verifyUrl: String?,
        val remindAt: Instant,
        val dueAt: Instant,
        val notifyRoleId: Long?,
        val preRemindHours: Set<Int>,
        val preNotifiedHours: Set<Int>,
        val closingMessage: String?,
        val status: AssignmentStatus,
        val createdBy: Long,
        val createdAt: Instant,
        val updatedAt: Instant,
        val notifiedAt: Instant?,
        val closedAt: Instant?,
        val nextFireAt: Instant?,
    )

    sealed interface PendingAction {
        data object InitialReminder : PendingAction
        data class PreDueReminder(val hoursBeforeDue: Int) : PendingAction
        data object CloseDue : PendingAction
        data object None : PendingAction
    }

    sealed interface CreateResult {
        data class Success(val task: AssignmentTaskView) : CreateResult
        data object InvalidUrl : CreateResult
        data object InvalidTitle : CreateResult
        data object RemindAtMustBeFuture : CreateResult
        data object DueAtMustBeFuture : CreateResult
        data object DueAtMustBeAfterRemindAt : CreateResult
        data object InvalidPreReminderHours : CreateResult
        data object InvalidClosingMessage : CreateResult
    }

    sealed interface ListResult {
        data class Success(val tasks: List<AssignmentTaskView>) : ListResult
        data object InvalidStatus : ListResult
        data object HiddenDeleted : ListResult
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
        data object AllVisible : StatusFilter
        data class Selected(val status: AssignmentStatus) : StatusFilter
        data object Invalid : StatusFilter
        data object HiddenDeleted : StatusFilter
    }

    private sealed interface ClosingMessageValidation {
        data class Valid(val value: String?) : ClosingMessageValidation
        data object Invalid : ClosingMessageValidation
    }

    private sealed interface VerifyUrlValidation {
        data class Valid(val value: String?) : VerifyUrlValidation
        data object Invalid : VerifyUrlValidation
    }

    companion object {
        private val visibleListStatuses = setOf(
            AssignmentStatus.PENDING,
            AssignmentStatus.DONE,
            AssignmentStatus.CLOSED,
        )
        private val HOUR_REGEX = Regex("""\d+""")
        private val DEFAULT_PRE_REMINDER_HOURS = setOf(24, 3, 1)
        private const val MAX_PRE_REMINDER_HOURS = 168
        private const val MAX_CLOSING_MESSAGE_LENGTH = 500
    }
}
