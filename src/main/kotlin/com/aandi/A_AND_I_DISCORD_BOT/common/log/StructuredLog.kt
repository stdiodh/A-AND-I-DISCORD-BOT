package com.aandi.A_AND_I_DISCORD_BOT.common.log

object StructuredLog {
    fun event(name: String, vararg fields: Pair<String, Any?>): String {
        val payload = fields.joinToString(" ") { (key, value) ->
            "$key=${encode(value)}"
        }
        if (payload.isBlank()) {
            return "event=$name"
        }
        return "event=$name $payload"
    }

    private fun encode(value: Any?): String {
        val raw = value?.toString() ?: "null"
        return raw.replace(" ", "_")
    }
}

