package com.aandi.A_AND_I_DISCORD_BOT.assignment.notifier

import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService.AssignmentTaskView
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class AssignmentNotifier(
    @Lazy private val jda: JDA,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun sendNotification(task: AssignmentTaskView, notificationType: NotificationType): SendResult {
        val channel = jda.getTextChannelById(task.channelId)
        if (channel == null) {
            log.error("Assignment notification failed (channel missing): taskId={}, channelId={}", task.id, task.channelId)
            return SendResult.NonRetryable("채널을 찾을 수 없습니다.")
        }

        val embed = buildEmbed(task, notificationType)
        val content = buildContent(task, notificationType)
        return runCatching {
            channel.sendMessage(content)
                .setEmbeds(embed)
                .setComponents(ActionRow.of(Button.link(task.verifyUrl, "과제 확인하기")))
                .submit()
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.fold(
            onSuccess = {
                SendResult.Success(Instant.now(clock))
            },
            onFailure = { exception ->
                classifyFailure(exception)
            },
        )
    }

    private fun buildContent(task: AssignmentTaskView, notificationType: NotificationType): String {
        val roleMention = resolveRoleMention(task)
        val prefix = if (roleMention.isBlank()) "" else "$roleMention "
        return when (notificationType) {
            NotificationType.InitialReminder -> "${prefix}과제 알림이 도착했습니다."
            is NotificationType.PreDueReminder -> "${prefix}과제가 ${notificationType.hoursBeforeDue}시간 후 마감됩니다."
            NotificationType.CloseDue -> buildClosingMessage(prefix, task)
        }
    }

    private fun buildClosingMessage(prefix: String, task: AssignmentTaskView): String {
        val customMessage = task.closingMessage?.trim().orEmpty()
        if (customMessage.isNotBlank()) {
            return "$prefix$customMessage"
        }
        return "${prefix}과제가 마감되었습니다. 모두 고생 많았습니다."
    }

    private fun buildEmbed(task: AssignmentTaskView, notificationType: NotificationType) = EmbedBuilder()
        .setTitle(resolveTitle(notificationType))
        .setColor(resolveColor(notificationType))
        .addField("제목", task.title, false)
        .addField("검증 링크", task.verifyUrl, false)
        .addField("알림시각(KST)", KstTime.formatInstantToKst(task.remindAt), true)
        .addField("마감시각(KST)", KstTime.formatInstantToKst(task.dueAt), true)
        .addField("등록자", "<@${task.createdBy}>", true)
        .addField("과제ID", task.id.toString(), true)
        .build()

    private fun resolveTitle(notificationType: NotificationType): String {
        return when (notificationType) {
            NotificationType.InitialReminder -> "과제 알림"
            is NotificationType.PreDueReminder -> "과제 마감 임박"
            NotificationType.CloseDue -> "과제 마감 안내"
        }
    }

    private fun resolveColor(notificationType: NotificationType): Color {
        return when (notificationType) {
            NotificationType.InitialReminder -> Color(0xF1C40F)
            is NotificationType.PreDueReminder -> Color(0xE67E22)
            NotificationType.CloseDue -> Color(0x2ECC71)
        }
    }

    private fun resolveRoleMention(task: AssignmentTaskView): String {
        val roleId = task.notifyRoleId ?: return ""
        val role = jda.getRoleById(roleId) ?: return ""
        return role.asMention
    }

    private fun classifyFailure(exception: Throwable): SendResult {
        if (exception is TimeoutException) {
            return SendResult.Retryable("전송 타임아웃")
        }
        if (exception is ErrorResponseException) {
            val response = exception.errorResponse
            if (response == ErrorResponse.UNKNOWN_CHANNEL) {
                return SendResult.NonRetryable("알림 채널이 삭제되었거나 접근 불가입니다.")
            }
            if (response == ErrorResponse.MISSING_ACCESS) {
                return SendResult.NonRetryable("알림 채널 접근 권한이 없습니다.")
            }
            if (response == ErrorResponse.MISSING_PERMISSIONS) {
                return SendResult.NonRetryable("알림 전송 권한이 없습니다.")
            }
            return SendResult.Retryable("Discord API 오류: ${response.name}")
        }
        return SendResult.Retryable("일시적 전송 오류: ${exception.javaClass.simpleName}")
    }

    sealed interface NotificationType {
        data object InitialReminder : NotificationType
        data class PreDueReminder(val hoursBeforeDue: Int) : NotificationType
        data object CloseDue : NotificationType
    }

    sealed interface SendResult {
        data class Success(val sentAtUtc: Instant) : SendResult
        data class Retryable(val reason: String) : SendResult
        data class NonRetryable(val reason: String) : SendResult
    }

    companion object {
        private const val SEND_TIMEOUT_SECONDS = 10L
    }
}
