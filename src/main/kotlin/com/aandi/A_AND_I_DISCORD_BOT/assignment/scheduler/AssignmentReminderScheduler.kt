package com.aandi.A_AND_I_DISCORD_BOT.assignment.scheduler

import com.aandi.A_AND_I_DISCORD_BOT.assignment.notifier.AssignmentNotifier
import com.aandi.A_AND_I_DISCORD_BOT.assignment.notifier.AssignmentNotifier.SendResult
import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
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
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${assignment.reminder.poll-delay-ms:30000}")
    fun poll() {
        val safeMax = maxPerTick.coerceAtLeast(1)
        repeat(safeMax) {
            val nowUtc = Instant.now(clock)
            val graceStartUtc = nowUtc.minus(graceHours, ChronoUnit.HOURS)
            val processed = transactionTemplate.execute {
                processOne(nowUtc, graceStartUtc)
            } ?: false
            if (!processed) {
                return
            }
        }
    }

    private fun processOne(nowUtc: Instant, graceStartUtc: Instant): Boolean {
        val task = assignmentTaskService.lockNextDueTask(
            nowUtc = nowUtc,
            graceStartUtc = graceStartUtc,
        ) ?: return false

        when (val result = assignmentNotifier.sendReminder(task)) {
            is SendResult.Success -> {
                val updated = assignmentTaskService.markNotified(task.id, result.sentAtUtc)
                if (!updated) {
                    log.warn("Assignment notified_at update skipped: taskId={}", task.id)
                }
                return true
            }

            is SendResult.Retryable -> {
                log.warn("Assignment reminder retryable failure: taskId={}, reason={}", task.id, result.reason)
                return true
            }

            is SendResult.NonRetryable -> {
                val canceled = assignmentTaskService.cancelPendingForNonRetryable(task.id, nowUtc)
                log.error(
                    "Assignment reminder non-retryable failure: taskId={}, canceled={}, reason={}",
                    task.id,
                    canceled,
                    result.reason,
                )
                return true
            }
        }
    }
}
