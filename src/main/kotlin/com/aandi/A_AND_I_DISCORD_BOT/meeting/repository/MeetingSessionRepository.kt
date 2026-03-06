package com.aandi.A_AND_I_DISCORD_BOT.meeting.repository

import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import org.springframework.data.jpa.repository.JpaRepository

interface MeetingSessionRepository : JpaRepository<MeetingSessionEntity, Long> {

    fun findFirstByGuildIdAndStatusOrderByStartedAtDesc(
        guildId: Long,
        status: MeetingSessionStatus,
    ): MeetingSessionEntity?

    fun findFirstByGuildIdAndBoardChannelIdAndStatusOrderByStartedAtDesc(
        guildId: Long,
        boardChannelId: Long,
        status: MeetingSessionStatus,
    ): MeetingSessionEntity?

    fun findAllByGuildIdAndBoardChannelIdAndStatusOrderByStartedAtDesc(
        guildId: Long,
        boardChannelId: Long,
        status: MeetingSessionStatus,
    ): List<MeetingSessionEntity>

    fun findAllByGuildIdAndStatusOrderByStartedAtDesc(
        guildId: Long,
        status: MeetingSessionStatus,
    ): List<MeetingSessionEntity>

    fun findFirstByGuildIdOrderByStartedAtDesc(guildId: Long): MeetingSessionEntity?

    fun findByGuildIdAndThreadId(guildId: Long, threadId: Long): MeetingSessionEntity?

    fun findByIdAndGuildId(id: Long, guildId: Long): MeetingSessionEntity?

    fun findTop50ByGuildIdOrderByStartedAtDesc(guildId: Long): List<MeetingSessionEntity>

    fun findTop50ByGuildIdAndStatusOrderByStartedAtDesc(
        guildId: Long,
        status: MeetingSessionStatus,
    ): List<MeetingSessionEntity>
}
