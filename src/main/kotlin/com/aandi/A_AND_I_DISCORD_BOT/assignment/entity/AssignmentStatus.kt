package com.aandi.A_AND_I_DISCORD_BOT.assignment.entity

enum class AssignmentStatus {
    PENDING,
    DONE,
    CANCELED,
    ;

    companion object {
        private val filterMapping = mapOf(
            "PENDING" to PENDING,
            "대기" to PENDING,
            "DONE" to DONE,
            "완료" to DONE,
            "CANCELED" to CANCELED,
            "CANCELLED" to CANCELED,
            "취소" to CANCELED,
        )

        fun fromFilter(raw: String): AssignmentStatus? {
            val normalized = raw.trim()
            if (normalized.isEmpty()) {
                return null
            }
            return filterMapping[normalized.uppercase()]
        }
    }
}
