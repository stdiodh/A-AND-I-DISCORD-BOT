package com.aandi.A_AND_I_DISCORD_BOT.common.auth

import com.aandi.A_AND_I_DISCORD_BOT.admin.auth.AdminPermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import net.dv8tion.jda.api.entities.Member
import org.springframework.stereotype.Component

@Component
class PermissionGate(
    private val adminPermissionChecker: AdminPermissionChecker,
    private val guildConfigService: GuildConfigService,
) {

    fun canAdminAction(guildId: Long, member: Member): Boolean {
        if (adminPermissionChecker.isAdmin(guildId, member)) {
            return true
        }
        return adminPermissionChecker.canSetAdminRole(guildId, member)
    }

    fun canStartMeeting(guildId: Long, member: Member): Boolean {
        if (canAdminAction(guildId, member)) {
            return true
        }
        val meetingOpenerRoleId = guildConfigService.getMeetingOpenerRole(guildId) ?: return false
        return member.roles.any { it.idLong == meetingOpenerRoleId }
    }
}
