package com.aandi.A_AND_I_DISCORD_BOT.admin.service

import com.aandi.A_AND_I_DISCORD_BOT.agenda.entity.GuildConfig
import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.GuildConfigRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GuildConfigService(
    private val guildConfigRepository: GuildConfigRepository,
) {

    @Transactional
    fun getOrCreate(guildId: Long): GuildConfig {
        guildConfigRepository.createDefaultIfAbsent(guildId)
        return guildConfigRepository.findById(guildId)
            .orElseThrow { IllegalStateException("guild_config row was not created for guildId=$guildId") }
    }

    @Transactional
    fun setAdminRole(guildId: Long, roleId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.adminRoleId = roleId
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun clearAdminRole(guildId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.adminRoleId = null
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun getAdminRole(guildId: Long): Long? {
        val config = getOrCreate(guildId)
        return config.adminRoleId
    }

    @Transactional
    fun setDashboard(guildId: Long, channelId: Long, messageId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.dashboardChannelId = channelId
        config.dashboardMessageId = messageId
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun clearDashboardMessage(guildId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.dashboardMessageId = null
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun getDashboard(guildId: Long): DashboardConfig {
        val config = getOrCreate(guildId)
        return DashboardConfig(
            channelId = config.dashboardChannelId,
            messageId = config.dashboardMessageId,
        )
    }

    data class DashboardConfig(
        val channelId: Long?,
        val messageId: Long?,
    )
}
