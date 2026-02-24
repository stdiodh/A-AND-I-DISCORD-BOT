package com.aandi.A_AND_I_DISCORD_BOT.mogakco.service

import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.VoiceSession
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.MogakcoChannelRepository
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.VoiceSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class VoiceSessionService(
    private val mogakcoChannelRepository: MogakcoChannelRepository,
    private val voiceSessionRepository: VoiceSessionRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun ingest(transition: VoiceTransition): VoiceEventOutcome {
        val oldTracked = transition.oldChannelId?.let { isTrackedChannel(transition.guildId, it) } == true
        val newTracked = transition.newChannelId?.let { isTrackedChannel(transition.guildId, it) } == true
        if (!oldTracked && !newTracked) {
            return VoiceEventOutcome.Ignored
        }

        if (oldTracked) {
            closeOpenSession(
                guildId = transition.guildId,
                userId = transition.userId,
                closedAt = transition.occurredAt,
            )
        }
        if (!newTracked) {
            log.info(
                "Voice session closed: guild={}, user={}, channel={}",
                transition.guildId,
                transition.userId,
                transition.oldChannelId,
            )
            return VoiceEventOutcome.LeftTracked
        }

        openSession(
            guildId = transition.guildId,
            userId = transition.userId,
            channelId = transition.newChannelId!!,
            joinedAt = transition.occurredAt,
        )
        if (oldTracked) {
            log.info(
                "Voice session moved: guild={}, user={}, from={}, to={}",
                transition.guildId,
                transition.userId,
                transition.oldChannelId,
                transition.newChannelId,
            )
            return VoiceEventOutcome.MovedTracked
        }

        log.info(
            "Voice session opened: guild={}, user={}, channel={}",
            transition.guildId,
            transition.userId,
            transition.newChannelId,
        )
        return VoiceEventOutcome.JoinedTracked
    }

    @Transactional
    fun closeOpenSessionsAtStartup(now: Instant = Instant.now()): Int {
        val openSessions = voiceSessionRepository.findByLeftAtIsNull()
        openSessions.forEach { closeSession(it, now) }
        return openSessions.size
    }

    private fun isTrackedChannel(guildId: Long, channelId: Long): Boolean =
        mogakcoChannelRepository.existsByIdGuildIdAndIdChannelId(guildId, channelId)

    private fun openSession(
        guildId: Long,
        userId: Long,
        channelId: Long,
        joinedAt: Instant,
    ) {
        val open = voiceSessionRepository.findFirstByGuildIdAndUserIdAndLeftAtIsNullOrderByJoinedAtDesc(guildId, userId)
        if (open != null) {
            closeSession(open, joinedAt)
        }

        voiceSessionRepository.save(
            VoiceSession(
                guildId = guildId,
                userId = userId,
                channelId = channelId,
                joinedAt = joinedAt,
            ),
        )
    }

    private fun closeOpenSession(
        guildId: Long,
        userId: Long,
        closedAt: Instant,
    ) {
        val open = voiceSessionRepository.findFirstByGuildIdAndUserIdAndLeftAtIsNullOrderByJoinedAtDesc(guildId, userId)
            ?: return
        closeSession(open, closedAt)
    }

    private fun closeSession(session: VoiceSession, closedAt: Instant) {
        val leftAt = resolveClosedAt(session.joinedAt, closedAt)
        session.leftAt = leftAt
        session.durationSec = Duration.between(session.joinedAt, leftAt).seconds.coerceAtLeast(0).toInt()
    }

    private fun resolveClosedAt(joinedAt: Instant, closedAt: Instant): Instant {
        if (closedAt.isBefore(joinedAt)) {
            return joinedAt
        }
        return closedAt
    }

    enum class VoiceEventOutcome {
        Ignored,
        JoinedTracked,
        LeftTracked,
        MovedTracked,
    }
}
