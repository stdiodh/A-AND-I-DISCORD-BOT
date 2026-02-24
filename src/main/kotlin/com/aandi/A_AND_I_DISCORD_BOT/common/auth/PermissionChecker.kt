package com.aandi.A_AND_I_DISCORD_BOT.common.auth

import com.aandi.A_AND_I_DISCORD_BOT.agenda.repository.GuildConfigRepository
import org.springframework.stereotype.Component

@Component
class PermissionChecker(
    private val guildConfigRepository: GuildConfigRepository,
) {

    fun isAdmin(
        guildId: Long,
        requesterRoleIds: Set<Long>,
        hasManageServerPermission: Boolean,
    ): Boolean {
        val adminRoleId = guildConfigRepository.findById(guildId).orElse(null)?.adminRoleId
        if (adminRoleId == null) {
            return hasManageServerPermission
        }
        return requesterRoleIds.contains(adminRoleId)
    }
}
