package com.aandi.A_AND_I_DISCORD_BOT.common.auth

import com.aandi.A_AND_I_DISCORD_BOT.admin.auth.AdminPermissionChecker
import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role

class PermissionGateTest : FunSpec({
    val adminPermissionChecker = mockk<AdminPermissionChecker>()
    val guildConfigService = mockk<GuildConfigService>()
    val permissionGate = PermissionGate(adminPermissionChecker, guildConfigService)
    val guildId = 777L

    beforeTest {
        clearMocks(adminPermissionChecker, guildConfigService)
    }

    test("canStartMeeting-admin 권한이면 true다") {
        val member = member(roleIds = emptyList())
        every { adminPermissionChecker.isAdmin(guildId, member) } returns true

        permissionGate.canStartMeeting(guildId, member) shouldBe true
    }

    test("canStartMeeting-관리자 설정 권한이면 true다") {
        val member = member(roleIds = emptyList())
        every { adminPermissionChecker.isAdmin(guildId, member) } returns false
        every { adminPermissionChecker.canSetAdminRole(guildId, member) } returns true

        permissionGate.canStartMeeting(guildId, member) shouldBe true
    }

    test("canStartMeeting-회의 열기 역할 보유 시 true다") {
        val member = member(roleIds = listOf(100L))
        every { adminPermissionChecker.isAdmin(guildId, member) } returns false
        every { adminPermissionChecker.canSetAdminRole(guildId, member) } returns false
        every { guildConfigService.getMeetingOpenerRole(guildId) } returns 100L

        permissionGate.canStartMeeting(guildId, member) shouldBe true
    }

    test("canStartMeeting-권한이 모두 없으면 false다") {
        val member = member(roleIds = listOf(200L))
        every { adminPermissionChecker.isAdmin(guildId, member) } returns false
        every { adminPermissionChecker.canSetAdminRole(guildId, member) } returns false
        every { guildConfigService.getMeetingOpenerRole(guildId) } returns 100L

        permissionGate.canStartMeeting(guildId, member) shouldBe false
    }

    test("canStartMeeting-회의 열기 역할 미설정이면 false다") {
        val member = member(roleIds = listOf(200L))
        every { adminPermissionChecker.isAdmin(guildId, member) } returns false
        every { adminPermissionChecker.canSetAdminRole(guildId, member) } returns false
        every { guildConfigService.getMeetingOpenerRole(guildId) } returns null

        permissionGate.canStartMeeting(guildId, member) shouldBe false
    }
}) {
    companion object {
        private fun member(roleIds: List<Long>): Member {
            val member = mockk<Member>()
            every { member.roles } returns roleIds.map { role(it) }
            return member
        }

        private fun role(id: Long): Role {
            val role = mockk<Role>()
            every { role.idLong } returns id
            return role
        }
    }
}
