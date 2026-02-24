package com.aandi.A_AND_I_DISCORD_BOT.common.format

import org.springframework.stereotype.Component
import java.util.Locale

@Component
class DurationFormatter {
    fun toHourMinute(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        return String.format(Locale.US, "%02d:%02d", hours, minutes)
    }
}
