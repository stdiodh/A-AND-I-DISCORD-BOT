package com.aandi.A_AND_I_DISCORD_BOT.voice.summary.repository

import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.entity.VoiceSummaryJobEntity
import org.springframework.data.jpa.repository.JpaRepository

interface VoiceSummaryJobRepository : JpaRepository<VoiceSummaryJobEntity, Long> {

    fun findFirstByGuildIdAndMeetingThreadIdOrderByIdDesc(
        guildId: Long,
        meetingThreadId: Long,
    ): VoiceSummaryJobEntity?
}
