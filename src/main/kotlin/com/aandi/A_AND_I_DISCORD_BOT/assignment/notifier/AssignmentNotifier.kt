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
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class AssignmentNotifier(
    private val jda: JDA,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun sendReminder(task: AssignmentTaskView): SendResult {
        val channel = jda.getTextChannelById(task.channelId)
        if (channel == null) {
            log.error("Assignment reminder failed (channel missing): taskId={}, channelId={}", task.id, task.channelId)
            return SendResult.NonRetryable("채널을 찾을 수 없습니다.")
        }

        val embed = EmbedBuilder()
            .setTitle("과제 알림")
            .setColor(Color(0xF1C40F))
            .addField("제목", task.title, false)
            .addField("검증 링크", task.verifyUrl, false)
            .addField("알림시각(KST)", KstTime.formatInstantToKst(task.remindAt), true)
            .addField("등록자", "<@${task.createdBy}>", true)
            .build()

        return runCatching {
            channel.sendMessageEmbeds(embed)
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

    sealed interface SendResult {
        data class Success(val sentAtUtc: Instant) : SendResult
        data class Retryable(val reason: String) : SendResult
        data class NonRetryable(val reason: String) : SendResult
    }

    companion object {
        private const val SEND_TIMEOUT_SECONDS = 10L
    }
}
