package com.aandi.A_AND_I_DISCORD_BOT.agenda.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.AgendaLink
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.AgendaLinkRepository
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.GuildConfigRepository
import com.aandi.A_AND_I_DISCORD_BOT.common.auth.PermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodCalculator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class AgendaService(
    private val agendaLinkRepository: AgendaLinkRepository,
    private val guildConfigRepository: GuildConfigRepository,
    private val periodCalculator: PeriodCalculator,
    private val permissionChecker: PermissionChecker,
    private val clock: Clock,
) {

    @Transactional
    fun setTodayAgenda(
        guildId: Long,
        requesterUserId: Long,
        requesterRoleIds: Set<Long>,
        hasManageServerPermission: Boolean,
        rawUrl: String,
        rawTitle: String?,
    ): SetAgendaResult {
        val isAdmin = permissionChecker.isAdmin(
            guildId = guildId,
            requesterRoleIds = requesterRoleIds,
            hasManageServerPermission = hasManageServerPermission,
        )
        if (!isAdmin) {
            return SetAgendaResult.Forbidden
        }

        val normalizedUrl = validateUrl(rawUrl) ?: return SetAgendaResult.InvalidUrl
        val normalizedTitle = when (val titleResult = validateAndNormalizeTitle(rawTitle)) {
            is TitleValidationResult.Invalid -> return SetAgendaResult.InvalidTitle
            is TitleValidationResult.Valid -> titleResult.value
        }
        guildConfigRepository.createDefaultIfAbsent(guildId)
        val now = Instant.now(clock)
        val today = periodCalculator.today(now)
        val existing = agendaLinkRepository.findByGuildIdAndDateLocal(guildId, today)

        val agenda = existing ?: AgendaLink(
            guildId = guildId,
            dateLocal = today,
            title = normalizedTitle ?: DEFAULT_TITLE,
            url = normalizedUrl,
            createdBy = requesterUserId,
            createdAt = now,
            updatedAt = now,
        )

        agenda.url = normalizedUrl
        agenda.title = normalizedTitle ?: agenda.title
        agenda.updatedAt = now
        agendaLinkRepository.save(agenda)
        return SetAgendaResult.Success(
            title = agenda.title,
            url = agenda.url,
            updated = existing != null,
        )
    }

    @Transactional(readOnly = true)
    fun getTodayAgenda(guildId: Long): AgendaView? {
        val today = periodCalculator.today(Instant.now(clock))
        val agenda = agendaLinkRepository.findByGuildIdAndDateLocal(guildId, today) ?: return null
        return AgendaView(
            title = agenda.title,
            url = agenda.url,
            dateLocal = agenda.dateLocal,
        )
    }

    @Transactional(readOnly = true)
    fun getRecentAgendas(guildId: Long, days: Int): RecentAgendaResult {
        if (days <= 0) {
            return RecentAgendaResult.InvalidDays
        }

        val today = periodCalculator.today(Instant.now(clock))
        val startDate = today.minusDays(days.toLong() - 1L)
        val agendas = agendaLinkRepository.findByGuildIdAndDateLocalBetweenOrderByDateLocalDesc(
            guildId = guildId,
            startDate = startDate,
            endDate = today,
        )
        if (agendas.isEmpty()) {
            return RecentAgendaResult.Empty
        }
        return RecentAgendaResult.Success(
            agendas = agendas.map {
                AgendaView(
                    title = it.title,
                    url = it.url,
                    dateLocal = it.dateLocal,
                )
            },
        )
    }

    private fun validateUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return null
        }

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") {
            return null
        }
        if (uri.host.isNullOrBlank()) {
            return null
        }
        return uri.toString()
    }

    private fun validateAndNormalizeTitle(rawTitle: String?): TitleValidationResult {
        val trimmed = rawTitle?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return TitleValidationResult.Valid(null)
        }
        if (trimmed.length > 255) {
            return TitleValidationResult.Invalid
        }
        return TitleValidationResult.Valid(trimmed)
    }

    data class AgendaView(
        val title: String,
        val url: String,
        val dateLocal: LocalDate,
    )

    sealed interface SetAgendaResult {
        data class Success(
            val title: String,
            val url: String,
            val updated: Boolean,
        ) : SetAgendaResult

        data object Forbidden : SetAgendaResult
        data object InvalidUrl : SetAgendaResult
        data object InvalidTitle : SetAgendaResult
    }

    sealed interface RecentAgendaResult {
        data class Success(
            val agendas: List<AgendaView>,
        ) : RecentAgendaResult

        data object Empty : RecentAgendaResult
        data object InvalidDays : RecentAgendaResult
    }

    private sealed interface TitleValidationResult {
        data class Valid(val value: String?) : TitleValidationResult
        data object Invalid : TitleValidationResult
    }

    companion object {
        private const val DEFAULT_TITLE = "오늘 안건"
    }
}
