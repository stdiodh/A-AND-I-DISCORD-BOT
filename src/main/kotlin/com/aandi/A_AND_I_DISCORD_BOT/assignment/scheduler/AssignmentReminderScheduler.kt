package com.aandi.A_AND_I_DISCORD_BOT.assignment.scheduler

import com.aandi.A_AND_I_DISCORD_BOT.assignment.notifier.AssignmentNotifier
import com.aandi.A_AND_I_DISCORD_BOT.assignment.notifier.AssignmentNotifier.NotificationType
import com.aandi.A_AND_I_DISCORD_BOT.assignment.notifier.AssignmentNotifier.SendResult
import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService.PendingAction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class AssignmentReminderScheduler(
    private val assignmentTaskService: AssignmentTaskService,
    private val assignmentNotifier: AssignmentNotifier,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
    @Value("\${assignment.reminder.grace-hours:24}") private val graceHours: Long,
    @Value("\${assignment.reminder.max-per-tick:20}") private val maxPerTick: Int,
    @Value("\${assignment.reminder.pre-scan-hours:24}") private val preScanHours: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${assignment.reminder.poll-delay-ms:30000}")
    fun poll() {
        val safeMax = maxPerTick.coerceAtLeast(1)
        repeat(safeMax) {
            val nowUtc = Instant.now(clock)
            val graceStartUtc = nowUtc.minus(graceHours, ChronoUnit.HOURS)
            val scanEndUtc = nowUtc.plus(preScanHours, ChronoUnit.HOURS)
            val processed = transactionTemplate.execute {
                processOne(nowUtc, graceStartUtc, scanEndUtc)
            } ?: false
            if (!processed) {
                return
            }
        }
    }

    private fun processOne(nowUtc: Instant, graceStartUtc: Instant, scanEndUtc: Instant): Boolean {
        val closingTask = assignmentTaskService.lockNextDueClosingTask(nowUtc, graceStartUtc)
        if (closingTask != null) {
            return processTaskWithAction(closingTask, PendingAction.CloseDue, nowUtc)
        }

        val initialTask = assignmentTaskService.lockNextInitialReminderTask(nowUtc, graceStartUtc)
        if (initialTask != null) {
            return processTaskWithAction(initialTask, PendingAction.InitialReminder, nowUtc)
        }

        val preTask = assignmentTaskService.lockNextPreReminderTask(nowUtc, scanEndUtc, graceStartUtc)
            ?: return false
        val action = assignmentTaskService.nextPendingAction(preTask, nowUtc)
        if (action is PendingAction.PreDueReminder) {
            return processTaskWithAction(preTask, action, nowUtc)
        }
        return false
    }

    private fun processTaskWithAction(
        task: AssignmentTaskService.AssignmentTaskView,
        action: PendingAction,
        nowUtc: Instant,
    ): Boolean {
        val notificationType = toNotificationType(action) ?: return false
        when (val result = assignmentNotifier.sendNotification(task, notificationType)) {
            is SendResult.Success -> {
                return applyPostSuccess(task.id, action, result.sentAtUtc)
            }

            is SendResult.Retryable -> {
                log.warn(
                    "Assignment notification retryable failure: taskId={}, action={}, reason={}",
                    task.id,
                    action,
                    result.reason,
                )
                return true
            }

            is SendResult.NonRetryable -> {
                val canceled = assignmentTaskService.cancelPendingForNonRetryable(task.id, nowUtc)
                log.error(
                    "Assignment notification non-retryable failure: taskId={}, action={}, canceled={}, reason={}",
                    task.id,
                    action,
                    canceled,
                    result.reason,
                )
                return true
            }
        }
    }

    private fun applyPostSuccess(taskId: Long, action: PendingAction, sentAtUtc: Instant): Boolean {
        return when (action) {
            PendingAction.InitialReminder -> assignmentTaskService.markNotified(taskId, sentAtUtc)
            is PendingAction.PreDueReminder -> assignmentTaskService.markPreNotified(taskId, action.hoursBeforeDue, sentAtUtc)
            PendingAction.CloseDue -> assignmentTaskService.markClosed(taskId, sentAtUtc)
            PendingAction.None -> false
        }
    }

    private fun toNotificationType(action: PendingAction): NotificationType? {
        return when (action) {
            PendingAction.InitialReminder -> NotificationType.InitialReminder
            is PendingAction.PreDueReminder -> NotificationType.PreDueReminder(action.hoursBeforeDue)
            PendingAction.CloseDue -> NotificationType.CloseDue
            PendingAction.None -> null
        }
    }
}
