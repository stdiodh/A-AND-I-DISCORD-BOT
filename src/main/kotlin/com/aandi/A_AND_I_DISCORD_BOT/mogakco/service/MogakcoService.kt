package com.aandi.A_AND_I_DISCORD_BOT.mogakco.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.GuildConfigRepository
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.MogakcoChannel
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.MogakcoChannelId
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.entity.VoiceSession
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.MogakcoChannelRepository
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.repository.VoiceSessionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Service
class MogakcoService(
    private val guildConfigRepository: GuildConfigRepository,
    private val mogakcoChannelRepository: MogakcoChannelRepository,
    private val voiceSessionRepository: VoiceSessionRepository,
    @Value("\${app.timezone:Asia/Seoul}") timezone: String,
) {

    private val zoneId: ZoneId = ZoneId.of(timezone)

    @Transactional
    fun addChannel(
        guildId: Long,
        requesterRoleIds: Set<Long>,
        channelId: Long,
    ): ChannelUpdateResult {
        when (requireAdminRole(guildId, requesterRoleIds)) {
            AdminCheckResult.Allowed -> Unit
            AdminCheckResult.MissingGuildConfig -> return ChannelUpdateResult.MissingGuildConfig
            AdminCheckResult.AdminRoleNotConfigured -> return ChannelUpdateResult.AdminRoleNotConfigured
            AdminCheckResult.Forbidden -> return ChannelUpdateResult.Forbidden
        }

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
        channelId: Long,
    ): ChannelUpdateResult {
        when (requireAdminRole(guildId, requesterRoleIds)) {
            AdminCheckResult.Allowed -> Unit
            AdminCheckResult.MissingGuildConfig -> return ChannelUpdateResult.MissingGuildConfig
            AdminCheckResult.AdminRoleNotConfigured -> return ChannelUpdateResult.AdminRoleNotConfigured
            AdminCheckResult.Forbidden -> return ChannelUpdateResult.Forbidden
        }

        val id = MogakcoChannelId(guildId = guildId, channelId = channelId)
        if (!mogakcoChannelRepository.existsById(id)) {
            return ChannelUpdateResult.NotFound
        }
        mogakcoChannelRepository.deleteById(id)
        return ChannelUpdateResult.Removed
    }

    @Transactional
    fun handleVoiceStateUpdate(
        guildId: Long,
        userId: Long,
        joinedChannelId: Long?,
        leftChannelId: Long?,
        at: Instant = Instant.now(),
    ) {
        val joinedTracked = joinedChannelId?.let { isTrackedChannel(guildId, it) } == true
        val leftTracked = leftChannelId?.let { isTrackedChannel(guildId, it) } == true
        if (!joinedTracked && !leftTracked) {
            return
        }

        if (leftTracked) {
            closeOpenSession(guildId, userId, at)
        }
        if (joinedTracked) {
            openSession(guildId, userId, joinedChannelId!!, at)
        }
    }

    @Transactional
    fun closeOpenSessionsAtStartup(now: Instant = Instant.now()): Int {
        val openSessions = voiceSessionRepository.findByLeftAtIsNull()
        openSessions.forEach { session ->
            closeSession(session, now)
        }
        return openSessions.size
    }

    @Transactional(readOnly = true)
    fun getLeaderboard(
        guildId: Long,
        period: PeriodType,
        top: Int,
        now: Instant = Instant.now(),
    ): LeaderboardView {
        val window = buildWindow(period, now)
        val sessions = voiceSessionRepository.findSessionsInRange(guildId, window.startInclusive, window.measureEndExclusive)
        val totalsByUser = mutableMapOf<Long, Long>()
        sessions.forEach { session ->
            val overlapped = overlapDurationSeconds(session, window)
            if (overlapped > 0) {
                totalsByUser.merge(session.userId, overlapped, Long::plus)
            }
        }

        val entries = totalsByUser.entries
            .sortedByDescending { it.value }
            .take(top.coerceIn(1, 50))
            .map { LeaderboardEntry(userId = it.key, totalSeconds = it.value) }

        return LeaderboardView(
            entries = entries,
            periodType = period,
        )
    }

    @Transactional(readOnly = true)
    fun getMyStats(
        guildId: Long,
        userId: Long,
        period: PeriodType,
        now: Instant = Instant.now(),
    ): MyStatsView {
        val window = buildWindow(period, now)
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

        val activeMinutes = guildConfigRepository.findById(guildId).orElse(null)?.mogakcoActiveMinutes ?: DEFAULT_ACTIVE_MINUTES
        val activeDays = dailySeconds.values.count { it >= activeMinutes.toLong() * 60L }
        val participationRate = if (window.totalDays == 0) 0.0 else activeDays.toDouble() / window.totalDays.toDouble()

        return MyStatsView(
            totalSeconds = totalSeconds,
            activeDays = activeDays,
            totalDays = window.totalDays,
            participationRate = participationRate,
            periodType = period,
            activeMinutesThreshold = activeMinutes,
        )
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
            if (open.channelId == channelId) {
                return
            }
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
        leftAt: Instant,
    ) {
        val open = voiceSessionRepository.findFirstByGuildIdAndUserIdAndLeftAtIsNullOrderByJoinedAtDesc(guildId, userId) ?: return
        closeSession(open, leftAt)
    }

    private fun closeSession(session: VoiceSession, closedAt: Instant) {
        val leftAt = if (closedAt.isBefore(session.joinedAt)) session.joinedAt else closedAt
        session.leftAt = leftAt
        session.durationSec = Duration.between(session.joinedAt, leftAt).seconds.coerceAtLeast(0).toInt()
    }

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
        return InstantRange(clippedStart, clippedEnd)
    }

    private fun accumulateDailySeconds(
        target: MutableMap<LocalDate, Long>,
        startInclusive: Instant,
        endExclusive: Instant,
    ) {
        var cursor = startInclusive
        while (cursor.isBefore(endExclusive)) {
            val currentDate = cursor.atZone(zoneId).toLocalDate()
            val nextDayStart = currentDate.plusDays(1).atStartOfDay(zoneId).toInstant()
            val segmentEnd = minOf(nextDayStart, endExclusive)
            val seconds = Duration.between(cursor, segmentEnd).seconds
            if (seconds > 0) {
                target.merge(currentDate, seconds, Long::plus)
            }
            cursor = segmentEnd
        }
    }

    private fun buildWindow(period: PeriodType, now: Instant): PeriodWindow {
        val nowZoned = now.atZone(zoneId)
        val startDate = when (period) {
            PeriodType.WEEK -> nowZoned.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            PeriodType.MONTH -> nowZoned.toLocalDate().withDayOfMonth(1)
        }
        val endDate = when (period) {
            PeriodType.WEEK -> startDate.plusWeeks(1)
            PeriodType.MONTH -> startDate.plusMonths(1)
        }
        val startInstant = startDate.atStartOfDay(zoneId).toInstant()
        val endInstant = endDate.atStartOfDay(zoneId).toInstant()
        val measureEndInstant = minOf(endInstant, now)
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt()

        return PeriodWindow(
            startInclusive = startInstant,
            endExclusive = endInstant,
            measureEndExclusive = measureEndInstant,
            totalDays = totalDays,
        )
    }

    private fun requireAdminRole(guildId: Long, requesterRoleIds: Set<Long>): AdminCheckResult {
        val guildConfig = guildConfigRepository.findById(guildId).orElse(null) ?: return AdminCheckResult.MissingGuildConfig
        val adminRoleId = guildConfig.adminRoleId ?: return AdminCheckResult.AdminRoleNotConfigured
        if (!requesterRoleIds.contains(adminRoleId)) {
            return AdminCheckResult.Forbidden
        }
        return AdminCheckResult.Allowed
    }

    enum class PeriodType {
        WEEK,
        MONTH,
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
        data object MissingGuildConfig : ChannelUpdateResult
        data object AdminRoleNotConfigured : ChannelUpdateResult
        data object Forbidden : ChannelUpdateResult
    }

    private sealed interface AdminCheckResult {
        data object Allowed : AdminCheckResult
        data object MissingGuildConfig : AdminCheckResult
        data object AdminRoleNotConfigured : AdminCheckResult
        data object Forbidden : AdminCheckResult
    }

    private data class InstantRange(
        val startInclusive: Instant,
        val endExclusive: Instant,
    )

    private data class PeriodWindow(
        val startInclusive: Instant,
        val endExclusive: Instant,
        val measureEndExclusive: Instant,
        val totalDays: Int,
    )

    companion object {
        private const val DEFAULT_ACTIVE_MINUTES = 30
    }
}
