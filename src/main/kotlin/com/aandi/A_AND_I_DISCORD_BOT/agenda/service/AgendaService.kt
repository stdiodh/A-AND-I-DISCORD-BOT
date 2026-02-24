package com.aandi.A_AND_I_DISCORD_BOT.agenda.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.AgendaLink
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.AgendaLinkRepository
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.GuildConfigRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Service
class AgendaService(
    private val guildConfigRepository: GuildConfigRepository,
    private val agendaLinkRepository: AgendaLinkRepository,
    @Value("\${app.timezone:Asia/Seoul}") timezone: String,
) {

    private val zoneId: ZoneId = ZoneId.of(timezone)

    @Transactional
    fun setTodayAgenda(
        guildId: Long,
        requesterUserId: Long,
        requesterRoleIds: Set<Long>,
        rawUrl: String,
        rawTitle: String?,
    ): SetAgendaResult {
        val guildConfig = guildConfigRepository.findById(guildId).orElse(null) ?: return SetAgendaResult.MissingGuildConfig
        val adminRoleId = guildConfig.adminRoleId ?: return SetAgendaResult.AdminRoleNotConfigured
        if (!requesterRoleIds.contains(adminRoleId)) {
            return SetAgendaResult.Forbidden
        }

        val normalizedUrl = validateUrl(rawUrl) ?: return SetAgendaResult.InvalidUrl
        val normalizedTitle = when (val titleResult = validateAndNormalizeTitle(rawTitle)) {
            is TitleValidationResult.Invalid -> return SetAgendaResult.InvalidTitle
            is TitleValidationResult.Valid -> titleResult.value
        }
        val today = LocalDate.now(zoneId)
        val now = Instant.now()
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
        val today = LocalDate.now(zoneId)
        val agenda = agendaLinkRepository.findByGuildIdAndDateLocal(guildId, today) ?: return null
        return AgendaView(
            title = agenda.title,
            url = agenda.url,
            dateLocal = agenda.dateLocal,
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

        data object MissingGuildConfig : SetAgendaResult
        data object AdminRoleNotConfigured : SetAgendaResult
        data object Forbidden : SetAgendaResult
        data object InvalidUrl : SetAgendaResult
        data object InvalidTitle : SetAgendaResult
    }

    private sealed interface TitleValidationResult {
        data class Valid(val value: String?) : TitleValidationResult
        data object Invalid : TitleValidationResult
    }

    companion object {
        private const val DEFAULT_TITLE = "오늘 안건"
    }
}
