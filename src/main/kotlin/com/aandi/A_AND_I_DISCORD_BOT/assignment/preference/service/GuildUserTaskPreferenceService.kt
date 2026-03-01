package com.aandi.A_AND_I_DISCORD_BOT.assignment.preference.service

import com.aandi.A_AND_I_DISCORD_BOT.assignment.preference.entity.GuildUserTaskPreferenceEntity
import com.aandi.A_AND_I_DISCORD_BOT.assignment.preference.repository.GuildUserTaskPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class GuildUserTaskPreferenceService(
    private val guildUserTaskPreferenceRepository: GuildUserTaskPreferenceRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun find(guildId: Long, userId: Long): TaskPreference? {
        val entity = guildUserTaskPreferenceRepository.findByGuildIdAndUserId(guildId, userId) ?: return null
        return TaskPreference(
            guildId = entity.guildId,
            userId = entity.userId,
            lastTaskChannelId = entity.lastTaskChannelId,
            lastNotifyRoleId = entity.lastNotifyRoleId,
            lastMentionEnabled = entity.lastMentionEnabled,
        )
    }

    @Transactional
    fun save(
        guildId: Long,
        userId: Long,
        lastTaskChannelId: Long?,
        lastNotifyRoleId: Long?,
        lastMentionEnabled: Boolean,
    ) {
        val nowUtc = Instant.now(clock)
        val existing = guildUserTaskPreferenceRepository.findByGuildIdAndUserId(guildId, userId)
        val target = existing ?: GuildUserTaskPreferenceEntity(
            guildId = guildId,
            userId = userId,
            createdAt = nowUtc,
            updatedAt = nowUtc,
        )
        target.lastTaskChannelId = lastTaskChannelId
        target.lastNotifyRoleId = lastNotifyRoleId
        target.lastMentionEnabled = lastMentionEnabled
        target.updatedAt = nowUtc
        guildUserTaskPreferenceRepository.save(target)
    }

    data class TaskPreference(
        val guildId: Long,
        val userId: Long,
        val lastTaskChannelId: Long?,
        val lastNotifyRoleId: Long?,
        val lastMentionEnabled: Boolean,
    )
}
