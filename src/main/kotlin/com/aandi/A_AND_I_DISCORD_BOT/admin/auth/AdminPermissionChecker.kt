package com.aandi.A_AND_I_DISCORD_BOT.admin.auth

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import org.springframework.stereotype.Component

@Component
class AdminPermissionChecker(
    private val guildConfigService: GuildConfigService,
) {

    fun isAdmin(guildId: Long, member: Member): Boolean {
        val adminRoleId = guildConfigService.getAdminRole(guildId) ?: return false
        return member.roles.any { it.idLong == adminRoleId }
    }

    fun canSetAdminRole(guildId: Long, member: Member): Boolean {
        val adminRoleId = guildConfigService.getAdminRole(guildId)
        if (adminRoleId == null) {
            return hasManageServerPermission(member)
        }
        return member.roles.any { it.idLong == adminRoleId }
    }

    fun canManageAdminRole(guildId: Long, member: Member): Boolean {
        if (hasManageServerPermission(member)) {
            return true
        }
        return canSetAdminRole(guildId, member)
    }

    private fun hasManageServerPermission(member: Member): Boolean {
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true
        }
        return member.hasPermission(Permission.MANAGE_SERVER)
    }
}
