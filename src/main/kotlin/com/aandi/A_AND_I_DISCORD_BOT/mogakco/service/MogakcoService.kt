package com.aandi.A_AND_I_DISCORD_BOT.mogakco.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.GuildConfigRepository
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodCalculator
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodType
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodWindow
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.MogakcoChannel
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.MogakcoChannelId
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.VoiceSession
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.MogakcoChannelRepository
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.VoiceSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Service
class MogakcoService(
    private val guildConfigRepository: GuildConfigRepository,
    private val mogakcoChannelRepository: MogakcoChannelRepository,
    private val voiceSessionRepository: VoiceSessionRepository,
    private val periodCalculator: PeriodCalculator,
    private val permissionChecker: PermissionChecker,
) {

    @Transactional
    fun addChannel(
        guildId: Long,
        requesterRoleIds: Set<Long>,
        hasManageServerPermission: Boolean,
        channelId: Long,
    ): ChannelUpdateResult {
        if (!isAdmin(guildId, requesterRoleIds, hasManageServerPermission)) {
            return ChannelUpdateResult.Forbidden
        }

        guildConfigRepository.createDefaultIfAbsent(guildId)
        val id = MogakcoChannelId(guildId = guildId, channelId = channelId)
        if (mogakcoChannelRepository.existsById(id)) {
            return ChannelUpdateResult.AlreadyExists
        }

        mogakcoChannelRepository.save(MogakcoChannel(id = id))
        return ChannelUpdateResult.Added
    }

    @Transactional
    fun removeChannel(
        guildId: Long,
        requesterRoleIds: Set<Long>,
        hasManageServerPermission: Boolean,
        channelId: Long,
    ): ChannelUpdateResult {
        if (!isAdmin(guildId, requesterRoleIds, hasManageServerPermission)) {
            return ChannelUpdateResult.Forbidden
        }

        val id = MogakcoChannelId(guildId = guildId, channelId = channelId)
        if (!mogakcoChannelRepository.existsById(id)) {
            return ChannelUpdateResult.NotFound
        }

        mogakcoChannelRepository.deleteById(id)
        return ChannelUpdateResult.Removed
    }

    @Transactional(readOnly = true)
    fun listChannels(
        guildId: Long,
        requesterRoleIds: Set<Long>,
        hasManageServerPermission: Boolean,
    ): ChannelListResult {
        if (!isAdmin(guildId, requesterRoleIds, hasManageServerPermission)) {
            return ChannelListResult.Forbidden
        }

        val channels = mogakcoChannelRepository.findAllByIdGuildIdOrderByIdChannelIdAsc(guildId)
            .map { it.id.channelId }
        return ChannelListResult.Success(channels)
    }

    @Transactional(readOnly = true)
    fun getLeaderboard(
        guildId: Long,
        period: PeriodType,
        top: Int,
        now: Instant = Instant.now(),
    ): LeaderboardView {
        val window = periodCalculator.currentWindow(period, now)
        val sessions = voiceSessionRepository.findSessionsInRange(guildId, window.startInclusive, window.measureEndExclusive)
        val totalsByUser = mutableMapOf<Long, Long>()

        sessions.forEach { session ->
            val overlapped = overlapDurationSeconds(session, window)
            if (overlapped <= 0) {
                return@forEach
            }
            totalsByUser.merge(session.userId, overlapped, Long::plus)
        }

        val entries = totalsByUser.entries
            .sortedByDescending { it.value }
            .take(top.coerceIn(1, 50))
            .map { LeaderboardEntry(userId = it.key, totalSeconds = it.value) }

        return LeaderboardView(entries = entries, periodType = period)
    }

    @Transactional(readOnly = true)
    fun getMyStats(
        guildId: Long,
        userId: Long,
        period: PeriodType,
        now: Instant = Instant.now(),
    ): MyStatsView {
        val window = periodCalculator.currentWindow(period, now)
        val sessions = voiceSessionRepository.findSessionsInRange(guildId, window.startInclusive, window.measureEndExclusive)
        var totalSeconds = 0L
        val dailySeconds = mutableMapOf<LocalDate, Long>()

        sessions.forEach { session ->
            if (session.userId != userId) {
                return@forEach
            }

            val clipped = clipSession(session, window) ?: return@forEach
            val duration = Duration.between(clipped.startInclusive, clipped.endExclusive).seconds
            if (duration <= 0) {
                return@forEach
            }

            totalSeconds += duration
            accumulateDailySeconds(dailySeconds, clipped.startInclusive, clipped.endExclusive)
        }

        val activeMinutes = resolveActiveMinutes(guildId)
        val activeSecondsThreshold = activeMinutes.toLong() * 60L
        val activeDays = dailySeconds.values.count { it >= activeSecondsThreshold }
        val participationRate = calculateParticipationRate(activeDays, window.totalDays)

        return MyStatsView(
            totalSeconds = totalSeconds,
            activeDays = activeDays,
            totalDays = window.totalDays,
            participationRate = participationRate,
            periodType = period,
            activeMinutesThreshold = activeMinutes,
        )
    }

    private fun isAdmin(
        guildId: Long,
        requesterRoleIds: Set<Long>,
        hasManageServerPermission: Boolean,
    ): Boolean = permissionChecker.isAdmin(
        guildId = guildId,
        requesterRoleIds = requesterRoleIds,
        hasManageServerPermission = hasManageServerPermission,
    )

    private fun overlapDurationSeconds(session: VoiceSession, window: PeriodWindow): Long {
        val clipped = clipSession(session, window) ?: return 0
        return Duration.between(clipped.startInclusive, clipped.endExclusive).seconds.coerceAtLeast(0)
    }

    private fun clipSession(session: VoiceSession, window: PeriodWindow): InstantRange? {
        val sessionStart = session.joinedAt
        val sessionEnd = session.leftAt ?: window.measureEndExclusive
        val clippedStart = maxOf(sessionStart, window.startInclusive)
        val clippedEnd = minOf(sessionEnd, window.measureEndExclusive)
        if (!clippedEnd.isAfter(clippedStart)) {
            return null
        }
        return InstantRange(startInclusive = clippedStart, endExclusive = clippedEnd)
    }

    private fun accumulateDailySeconds(
        target: MutableMap<LocalDate, Long>,
        startInclusive: Instant,
        endExclusive: Instant,
    ) {
        var cursor = startInclusive
        while (cursor.isBefore(endExclusive)) {
            val currentDate = cursor.atZone(periodCalculator.zoneId).toLocalDate()
            val nextDayStart = currentDate.plusDays(1).atStartOfDay(periodCalculator.zoneId).toInstant()
            val segmentEnd = minOf(nextDayStart, endExclusive)
            val seconds = Duration.between(cursor, segmentEnd).seconds
            if (seconds > 0) {
                target.merge(currentDate, seconds, Long::plus)
            }
            cursor = segmentEnd
        }
    }

    private fun resolveActiveMinutes(guildId: Long): Int {
        val configured = guildConfigRepository.findById(guildId).orElse(null)?.mogakcoActiveMinutes
        if (configured == null) {
            return DEFAULT_ACTIVE_MINUTES
        }
        if (configured <= 0) {
            return DEFAULT_ACTIVE_MINUTES
        }
        return configured
    }

    private fun calculateParticipationRate(activeDays: Int, totalDays: Int): Double {
        if (totalDays == 0) {
            return 0.0
        }
        return activeDays.toDouble() / totalDays.toDouble()
    }

    data class LeaderboardView(
        val entries: List<LeaderboardEntry>,
        val periodType: PeriodType,
    )

    data class LeaderboardEntry(
        val userId: Long,
        val totalSeconds: Long,
    )

    data class MyStatsView(
        val totalSeconds: Long,
        val activeDays: Int,
        val totalDays: Int,
        val participationRate: Double,
        val periodType: PeriodType,
        val activeMinutesThreshold: Int,
    )

    sealed interface ChannelUpdateResult {
        data object Added : ChannelUpdateResult
        data object Removed : ChannelUpdateResult
        data object AlreadyExists : ChannelUpdateResult
        data object NotFound : ChannelUpdateResult
        data object Forbidden : ChannelUpdateResult
    }

    sealed interface ChannelListResult {
        data class Success(
            val channelIds: List<Long>,
        ) : ChannelListResult

        data object Forbidden : ChannelListResult
    }

    private data class InstantRange(
        val startInclusive: Instant,
        val endExclusive: Instant,
    )

    companion object {
        private const val DEFAULT_ACTIVE_MINUTES = 30
    }
}
