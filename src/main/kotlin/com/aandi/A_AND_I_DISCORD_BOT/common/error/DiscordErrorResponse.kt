package com.aandi.A_AND_I_DISCORD_BOT.common.error

data class DiscordErrorResponse(
    val code: DiscordErrorCode,
    val message: String,
    val retryable: Boolean? = null,
)
