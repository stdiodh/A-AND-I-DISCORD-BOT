package com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui

object HomeCustomIdParser {

    fun parse(raw: String?): ParsedCustomId? {
        if (raw.isNullOrBlank()) {
            return null
        }

        val tokens = raw.split(":")
        if (tokens.size < 3) {
            return null
        }
        if (tokens.first() != PREFIX) {
            return null
        }

        val domain = tokens[1]
        val action = tokens[2]
        val tail = if (tokens.size > 3) tokens.subList(3, tokens.size) else emptyList()
        return ParsedCustomId(
            domain = domain,
            action = action,
            tail = tail,
        )
    }

    fun of(domain: String, action: String, vararg tail: String): String {
        val parts = mutableListOf(PREFIX, domain, action)
        parts.addAll(tail.toList())
        return parts.joinToString(separator = ":")
    }

    data class ParsedCustomId(
        val domain: String,
        val action: String,
        val tail: List<String>,
    ) {
        fun tailAt(index: Int): String? {
            if (index < 0 || index >= tail.size) {
                return null
            }
            return tail[index]
        }
    }

    private const val PREFIX = "home"
}
