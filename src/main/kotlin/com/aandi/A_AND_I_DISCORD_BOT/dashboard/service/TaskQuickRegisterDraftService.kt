package com.aandi.A_AND_I_DISCORD_BOT.dashboard.service

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class TaskQuickRegisterDraftService(
    private val clock: Clock,
) {

    private val drafts = ConcurrentHashMap<String, QuickDraft>()

    fun create(
        guildId: Long,
        userId: Long,
        title: String,
        link: String?,
        dueAtUtc: Instant,
        remindAtUtc: Instant,
        preReminderHoursRaw: String?,
        selectedChannelId: Long,
        selectedRoleId: Long?,
        mentionEnabled: Boolean,
    ): QuickDraft {
        cleanupExpired()
        val nowUtc = Instant.now(clock)
        val draft = QuickDraft(
            id = UUID.randomUUID().toString().replace("-", "").take(16),
            guildId = guildId,
            userId = userId,
            title = title,
            link = link,
            dueAtUtc = dueAtUtc,
            remindAtUtc = remindAtUtc,
            preReminderHoursRaw = preReminderHoursRaw,
            selectedChannelId = selectedChannelId,
            selectedRoleId = selectedRoleId,
            mentionEnabled = mentionEnabled,
            createdAt = nowUtc,
            updatedAt = nowUtc,
        )
        drafts[draft.id] = draft
        return draft
    }

    fun get(draftId: String): QuickDraft? {
        cleanupExpired()
        return drafts[draftId]
    }

    fun updateSelection(
        draftId: String,
        selectedChannelId: Long? = null,
        selectedRoleId: Long? = null,
        mentionEnabled: Boolean? = null,
    ): QuickDraft? {
        val current = get(draftId) ?: return null
        val updated = current.copy(
            selectedChannelId = selectedChannelId ?: current.selectedChannelId,
            selectedRoleId = selectedRoleId ?: current.selectedRoleId,
            mentionEnabled = mentionEnabled ?: current.mentionEnabled,
            updatedAt = Instant.now(clock),
        )
        drafts[draftId] = updated
        return updated
    }

    fun remove(draftId: String) {
        drafts.remove(draftId)
    }

    private fun cleanupExpired() {
        val nowUtc = Instant.now(clock)
        drafts.entries.removeIf { (_, draft) ->
            Duration.between(draft.updatedAt, nowUtc) > ttl
        }
    }

    data class QuickDraft(
        val id: String,
        val guildId: Long,
        val userId: Long,
        val title: String,
        val link: String?,
        val dueAtUtc: Instant,
        val remindAtUtc: Instant,
        val preReminderHoursRaw: String?,
        val selectedChannelId: Long,
        val selectedRoleId: Long?,
        val mentionEnabled: Boolean,
        val createdAt: Instant,
        val updatedAt: Instant,
    )

    companion object {
        private val ttl: Duration = Duration.ofHours(2)
    }
}
