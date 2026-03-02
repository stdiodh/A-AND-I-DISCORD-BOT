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
    fun setMeetingOpenerRole(guildId: Long, roleId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.meetingOpenerRoleId = roleId
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun clearMeetingOpenerRole(guildId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.meetingOpenerRoleId = null
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun getMeetingOpenerRole(guildId: Long): Long? {
        val config = getOrCreate(guildId)
        return config.meetingOpenerRoleId
    }

    @Transactional
    fun setMeetingBoardChannel(guildId: Long, channelId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.meetingBoardChannelId = channelId
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun clearMeetingBoardChannel(guildId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.meetingBoardChannelId = null
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun setMogakcoBoardChannel(guildId: Long, channelId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.mogakcoBoardChannelId = channelId
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun clearMogakcoBoardChannel(guildId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.mogakcoBoardChannelId = null
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun setAssignmentBoardChannel(guildId: Long, channelId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.defaultTaskChannelId = channelId
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun clearAssignmentBoardChannel(guildId: Long): GuildConfig {
        val config = getOrCreate(guildId)
        config.defaultTaskChannelId = null
        return guildConfigRepository.save(config)
    }

    @Transactional
    fun getBoardChannels(guildId: Long): BoardChannelConfig {
        val config = getOrCreate(guildId)
        return BoardChannelConfig(
            meetingChannelId = config.meetingBoardChannelId,
            mogakcoChannelId = config.mogakcoBoardChannelId,
            assignmentChannelId = config.defaultTaskChannelId,
        )
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

    @Transactional
    fun getTaskDefaults(guildId: Long): TaskDefaultConfig {
        val config = getOrCreate(guildId)
        return TaskDefaultConfig(
            defaultTaskChannelId = config.defaultTaskChannelId,
            defaultNotifyRoleId = config.defaultNotifyRoleId,
        )
    }

    @Transactional
    fun setTaskDefaults(guildId: Long, defaultTaskChannelId: Long?, defaultNotifyRoleId: Long?): GuildConfig {
        val config = getOrCreate(guildId)
        config.defaultTaskChannelId = defaultTaskChannelId
        config.defaultNotifyRoleId = defaultNotifyRoleId
        return guildConfigRepository.save(config)
    }

    data class DashboardConfig(
        val channelId: Long?,
        val messageId: Long?,
    )

    data class TaskDefaultConfig(
        val defaultTaskChannelId: Long?,
        val defaultNotifyRoleId: Long?,
    )

    data class BoardChannelConfig(
        val meetingChannelId: Long?,
        val mogakcoChannelId: Long?,
        val assignmentChannelId: Long?,
    )
}
