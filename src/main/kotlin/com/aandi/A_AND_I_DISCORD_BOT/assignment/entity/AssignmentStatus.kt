package com.aandi.A_AND_I_DISCORD_BOT.assignment.entity

enum class AssignmentStatus {
    PENDING,
    DONE,
    CANCELED,
    ;

    companion object {
        fun fromFilter(raw: String): AssignmentStatus? {
            val normalized = raw.trim()
            if (normalized.isEmpty()) {
                return null
            }

            return when (normalized.uppercase()) {
                "PENDING", "대기" -> PENDING
                "DONE", "완료" -> DONE
                "CANCELED", "CANCELLED", "취소" -> CANCELED
                else -> null
            }
        }
    }
}
