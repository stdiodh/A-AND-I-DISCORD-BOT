package com.aandi.A_AND_I_DISCORD_BOT.common.discord

import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorCode
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.error.DiscordErrorResponse
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import org.springframework.stereotype.Component

@Component
class DiscordReplyFactory {

    fun invalidInput(event: IReplyCallback, message: String, ephemeral: Boolean = true) {
        replyError(event, DiscordErrorCode.COMMON_INVALID_INPUT, message, false, ephemeral)
    }

    fun accessDenied(event: IReplyCallback, message: String, ephemeral: Boolean = true) {
        replyError(event, DiscordErrorCode.ACCESS_DENIED, message, false, ephemeral)
    }

    fun resourceNotFound(event: IReplyCallback, message: String, ephemeral: Boolean = true) {
        replyError(event, DiscordErrorCode.RESOURCE_NOT_FOUND, message, false, ephemeral)
    }

    fun internalError(event: IReplyCallback, message: String, ephemeral: Boolean = true) {
        replyError(event, DiscordErrorCode.INTERNAL_ERROR, message, true, ephemeral)
    }

    private fun replyError(
        event: IReplyCallback,
        code: DiscordErrorCode,
        message: String,
        retryable: Boolean,
        ephemeral: Boolean,
    ) {
        val payload = DiscordErrorFormatter.format(
            DiscordErrorResponse(
                code = code,
                message = message,
                retryable = retryable,
            ),
        )
        event.reply(payload)
            .setEphemeral(ephemeral)
            .queue()
    }
}
