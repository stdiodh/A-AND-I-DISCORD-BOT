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
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.VoiceSessionDailyRollupRepository
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.VoiceSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Service
class MogakcoService(
    private val guildConfigRepository: GuildConfigRepository,
    private val mogakcoChannelRepository: MogakcoChannelRepository,
    private val voiceSessionRepository: VoiceSessionRepository,
    private val voiceSessionDailyRollupRepository: VoiceSessionDailyRollupRepository,
    private val periodCalculator: PeriodCalculator,
    private val permissionChecker: PermissionChecker,
    private val clock: Clock,
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
    ): LeaderboardView = getLeaderboard(guildId, period, top, Instant.now(clock))

    @Transactional(readOnly = true)
    fun getLeaderboard(
        guildId: Long,
        period: PeriodType,
        top: Int,
        now: Instant,
    ): LeaderboardView {
        val window = periodCalculator.currentWindow(period, now)
        val totalsByUser = collectTotalsByUser(guildId, window, now)

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
    ): MyStatsView = getMyStats(guildId, userId, period, Instant.now(clock))

    @Transactional(readOnly = true)
    fun getTodayStats(
        guildId: Long,
        userId: Long,
    ): MyStatsView = getMyStats(guildId, userId, PeriodType.DAY, Instant.now(clock))

    @Transactional(readOnly = true)
    fun getMyStats(
        guildId: Long,
        userId: Long,
        period: PeriodType,
        now: Instant,
    ): MyStatsView {
        val window = periodCalculator.currentWindow(period, now)
        var totalSeconds = 0L
        val dailySeconds = mutableMapOf<LocalDate, Long>()
        val todayStart = now.atZone(periodCalculator.zoneId).toLocalDate()
            .atStartOfDay(periodCalculator.zoneId)
            .toInstant()
        val historyEnd = minOf(todayStart, window.measureEndExclusive)

        if (historyEnd.isAfter(window.startInclusive)) {
            val historyStartDate = window.startInclusive.atZone(periodCalculator.zoneId).toLocalDate()
            val historyEndDateExclusive = historyEnd.atZone(periodCalculator.zoneId).toLocalDate()
            if (historyEndDateExclusive.isAfter(historyStartDate)) {
                val dailyRollups = voiceSessionDailyRollupRepository.findUserDailyTotals(
                    guildId = guildId,
                    userId = userId,
                    startDate = historyStartDate,
                    endDateExclusive = historyEndDateExclusive,
                )
                dailyRollups.forEach { item ->
                    totalSeconds += item.totalSeconds
                    dailySeconds.merge(item.dateLocal, item.totalSeconds, Long::plus)
                }
            }
        }

        val liveStart = maxOf(window.startInclusive, todayStart)
        if (window.measureEndExclusive.isAfter(liveStart)) {
            val closedLiveSessions = voiceSessionRepository.findClosedSessionsInRange(
                guildId = guildId,
                startInclusive = liveStart,
                endExclusive = window.measureEndExclusive,
            )
            accumulateSessions(
                sessions = closedLiveSessions,
                window = window,
                targetUserId = userId,
                totalByUser = null,
                totalRef = { totalSeconds },
                updateTotal = { totalSeconds = it },
                dailySeconds = dailySeconds,
            )
        }

        val openSessions = voiceSessionRepository.findOpenSessionsInRange(
            guildId = guildId,
            endExclusive = window.measureEndExclusive,
        )
        accumulateSessions(
            sessions = openSessions,
            window = window,
            targetUserId = userId,
            totalByUser = null,
            totalRef = { totalSeconds },
            updateTotal = { totalSeconds = it },
            dailySeconds = dailySeconds,
        )

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

    private fun collectTotalsByUser(
        guildId: Long,
        window: PeriodWindow,
        now: Instant,
    ): MutableMap<Long, Long> {
        val totalsByUser = mutableMapOf<Long, Long>()
        val todayStart = now.atZone(periodCalculator.zoneId).toLocalDate()
            .atStartOfDay(periodCalculator.zoneId)
            .toInstant()
        val historyEnd = minOf(todayStart, window.measureEndExclusive)

        if (historyEnd.isAfter(window.startInclusive)) {
            val historyStartDate = window.startInclusive.atZone(periodCalculator.zoneId).toLocalDate()
            val historyEndDateExclusive = historyEnd.atZone(periodCalculator.zoneId).toLocalDate()
            if (historyEndDateExclusive.isAfter(historyStartDate)) {
                val rollups = voiceSessionDailyRollupRepository.aggregateUserTotals(
                    guildId = guildId,
                    startDate = historyStartDate,
                    endDateExclusive = historyEndDateExclusive,
                )
                rollups.forEach { totalsByUser.merge(it.userId, it.totalSeconds, Long::plus) }
            }
        }

        val liveStart = maxOf(window.startInclusive, todayStart)
        if (window.measureEndExclusive.isAfter(liveStart)) {
            val closedLiveSessions = voiceSessionRepository.findClosedSessionsInRange(
                guildId = guildId,
                startInclusive = liveStart,
                endExclusive = window.measureEndExclusive,
            )
            accumulateSessions(
                sessions = closedLiveSessions,
                window = window,
                targetUserId = null,
                totalByUser = totalsByUser,
                totalRef = null,
                updateTotal = null,
                dailySeconds = null,
            )
        }

        val openSessions = voiceSessionRepository.findOpenSessionsInRange(
            guildId = guildId,
            endExclusive = window.measureEndExclusive,
        )
        accumulateSessions(
            sessions = openSessions,
            window = window,
            targetUserId = null,
            totalByUser = totalsByUser,
            totalRef = null,
            updateTotal = null,
            dailySeconds = null,
        )
        return totalsByUser
    }

    private fun accumulateSessions(
        sessions: List<VoiceSession>,
        window: PeriodWindow,
        targetUserId: Long?,
        totalByUser: MutableMap<Long, Long>?,
        totalRef: (() -> Long)?,
        updateTotal: ((Long) -> Unit)?,
        dailySeconds: MutableMap<LocalDate, Long>?,
    ) {
        sessions.forEach { session ->
            if (targetUserId != null && session.userId != targetUserId) {
                return@forEach
            }
            val clipped = clipSession(session, window) ?: return@forEach
            val duration = Duration.between(clipped.startInclusive, clipped.endExclusive).seconds
            if (duration <= 0) {
                return@forEach
            }

            if (totalByUser != null) {
                totalByUser.merge(session.userId, duration, Long::plus)
            }
            if (totalRef != null && updateTotal != null) {
                updateTotal(totalRef() + duration)
            }
            if (dailySeconds != null) {
                accumulateDailySeconds(dailySeconds, clipped.startInclusive, clipped.endExclusive)
            }
        }
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
