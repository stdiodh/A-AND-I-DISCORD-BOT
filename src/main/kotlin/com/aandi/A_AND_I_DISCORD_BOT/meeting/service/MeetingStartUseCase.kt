package com.aandi.A_AND_I_DISCORD_BOT.meeting.service

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.AgendaLinkRepository
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodCalculator
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
    private val agendaLinkRepository: AgendaLinkRepository,
    private val periodCalculator: PeriodCalculator,
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
        val activeSession = resolveActiveSessionOrCleanup(guildId, requestedBy)
        if (activeSession != null) {
            return MeetingService.StartResult.AlreadyActive(activeSession.threadId)
        }

        val boards = guildConfigService.getBoardChannels(guildId)
        val dashboard = guildConfigService.getDashboard(guildId)
        val channelId = targetChannelId
            ?: boards.meetingChannelId
            ?: dashboard.channelId
            ?: fallbackChannelId
            ?: return MeetingService.StartResult.ChannelNotConfigured
        val channel = meetingThreadGateway.findTextChannel(channelId) ?: return MeetingService.StartResult.ChannelNotFound
        val nowUtc = Instant.now(clock)
        val todayAgenda = agendaLinkRepository.findByGuildIdAndDateLocal(guildId, periodCalculator.today(nowUtc))
        val startMessage = meetingThreadGateway.createStartMessage(channel, requestedBy, nowUtc)
        val thread = meetingThreadGateway.createThread(startMessage, resolveThreadName(rawTitle))
            ?: return MeetingService.StartResult.ThreadCreateFailed

        val savedSession = meetingSessionRepository.save(
            MeetingSessionEntity(
                guildId = guildId,
                threadId = thread.idLong,
                agendaLinkId = todayAgenda?.id,
                status = MeetingSessionStatus.ACTIVE,
                startedBy = requestedBy,
                startedAt = nowUtc,
                createdAt = nowUtc,
                updatedAt = nowUtc,
            ),
        )
        val sessionId = savedSession.id ?: return MeetingService.StartResult.ThreadCreateFailed

        meetingThreadGateway.postMeetingTemplate(guildId, thread)
        return MeetingService.StartResult.Success(
            sessionId = sessionId,
            threadId = thread.idLong,
            threadName = thread.name,
        )
    }

    private fun resolveActiveSessionOrCleanup(guildId: Long, requestedBy: Long): MeetingSessionEntity? {
        while (true) {
            val activeSession = meetingSessionRepository.findFirstByGuildIdAndStatusOrderByStartedAtDesc(
                guildId,
                MeetingSessionStatus.ACTIVE,
            ) ?: return null
            if (meetingThreadGateway.findThreadChannel(activeSession.threadId) != null) {
                return activeSession
            }
            closeMissingThreadSession(activeSession, requestedBy)
        }
    }

    private fun closeMissingThreadSession(session: MeetingSessionEntity, requestedBy: Long) {
        val nowUtc = Instant.now(clock)
        session.status = MeetingSessionStatus.ENDED
        session.endedBy = requestedBy
        session.endedAt = nowUtc
        session.updatedAt = nowUtc
        meetingSessionRepository.save(session)
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
