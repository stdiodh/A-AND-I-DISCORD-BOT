package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingStartUseCase(
    private val guildConfigService: GuildConfigService,
    private val meetingSessionRepository: MeetingSessionRepository,
    private val meetingThreadGateway: MeetingThreadGateway,
    private val clock: Clock,
) {

    fun startMeeting(
        guildId: Long,
        requestedBy: Long,
        targetChannelId: Long?,
        fallbackChannelId: Long?,
        rawTitle: String?,
    ): MeetingService.StartResult {
        val activeSession = meetingSessionRepository.findFirstByGuildIdAndStatusOrderByStartedAtDesc(
            guildId,
            MeetingSessionStatus.ACTIVE,
        )
        if (activeSession != null) {
            return MeetingService.StartResult.AlreadyActive(activeSession.threadId)
        }

        val dashboard = guildConfigService.getDashboard(guildId)
        val channelId = targetChannelId ?: dashboard.channelId ?: fallbackChannelId ?: return MeetingService.StartResult.ChannelNotConfigured
        val channel = meetingThreadGateway.findTextChannel(channelId) ?: return MeetingService.StartResult.ChannelNotFound
        val nowUtc = Instant.now(clock)
        val startMessage = meetingThreadGateway.createStartMessage(channel, requestedBy, nowUtc)
        val thread = meetingThreadGateway.createThread(startMessage, resolveThreadName(rawTitle))
            ?: return MeetingService.StartResult.ThreadCreateFailed

        meetingSessionRepository.save(
            MeetingSessionEntity(
                guildId = guildId,
                threadId = thread.idLong,
                status = MeetingSessionStatus.ACTIVE,
                startedBy = requestedBy,
                startedAt = nowUtc,
                createdAt = nowUtc,
                updatedAt = nowUtc,
            ),
        )

        meetingThreadGateway.postMeetingTemplate(guildId, thread)
        return MeetingService.StartResult.Success(thread.idLong, thread.name)
    }

    private fun resolveThreadName(rawTitle: String?): String {
        val normalized = rawTitle?.trim().orEmpty()
        if (normalized.isNotBlank()) {
            return normalized.take(MAX_THREAD_NAME_LENGTH)
        }
        val meetingDate = Instant.now(clock)
            .atZone(ZoneId.of("Asia/Seoul"))
            .toLocalDate()
        return "$meetingDate 회의"
    }

    companion object {
        private const val MAX_THREAD_NAME_LENGTH = 100
    }
}
