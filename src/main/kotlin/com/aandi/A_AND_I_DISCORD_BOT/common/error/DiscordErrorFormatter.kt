package com.aandi.A_AND_I_DISCORD_BOT.common.error

object DiscordErrorFormatter {

    fun format(error: DiscordErrorResponse): String {
        val retryableLine = retryableLine(error)
        return """
```json
{
  "code": "${error.code}",
  "message": "${escape(error.message)}"$retryableLine
}
```
""".trimIndent()
    }

    private fun retryableLine(error: DiscordErrorResponse): String {
        val retryable = error.retryable
        if (retryable == null) {
            return ""
        }
        return ",\n  \"retryable\": $retryable"
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
