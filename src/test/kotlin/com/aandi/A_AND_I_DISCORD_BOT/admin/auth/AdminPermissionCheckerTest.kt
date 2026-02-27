package com.aandi.A_AND_I_DISCORD_BOT.admin.auth

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role

class AdminPermissionCheckerTest : FunSpec({

    val guildConfigService = mockk<GuildConfigService>()
    val checker = AdminPermissionChecker(guildConfigService)
    val guildId = 777L

    test("isAdmin-admin_role_id가 있으면 역할 보유 여부로 판단한다") {
        every { guildConfigService.getAdminRole(guildId) } returns 100L
        val member = member(roleIds = listOf(100L), isAdmin = false, canManageServer = false)

        checker.isAdmin(guildId, member) shouldBe true
    }

    test("isAdmin-admin_role_id가 없으면 false다") {
        every { guildConfigService.getAdminRole(guildId) } returns null
        val member = member(roleIds = listOf(100L), isAdmin = true, canManageServer = true)

        checker.isAdmin(guildId, member) shouldBe false
    }

    test("canSetAdminRole-admin_role_id가 있으면 운영진만 가능하다") {
        every { guildConfigService.getAdminRole(guildId) } returns 100L
        val nonAdmin = member(roleIds = listOf(200L), isAdmin = true, canManageServer = true)
        val admin = member(roleIds = listOf(100L), isAdmin = false, canManageServer = false)

        checker.canSetAdminRole(guildId, nonAdmin) shouldBe false
        checker.canSetAdminRole(guildId, admin) shouldBe true
    }

    test("canSetAdminRole-admin_role_id가 없으면 Manage Server 또는 Administrator 권한자만 가능하다") {
        every { guildConfigService.getAdminRole(guildId) } returns null
        val manageServer = member(roleIds = emptyList(), isAdmin = false, canManageServer = true)
        val administrator = member(roleIds = emptyList(), isAdmin = true, canManageServer = false)
        val noPrivilege = member(roleIds = emptyList(), isAdmin = false, canManageServer = false)

        checker.canSetAdminRole(guildId, manageServer) shouldBe true
        checker.canSetAdminRole(guildId, administrator) shouldBe true
        checker.canSetAdminRole(guildId, noPrivilege) shouldBe false
    }

    test("assignment 권한-admin_role_id 설정 후 해당 role만 create done delete 가능하다") {
        every { guildConfigService.getAdminRole(guildId) } returns 555L
        val adminRoleMember = member(roleIds = listOf(555L), isAdmin = false, canManageServer = false)
        val manageServerOnlyMember = member(roleIds = emptyList(), isAdmin = false, canManageServer = true)

        checker.isAdmin(guildId, adminRoleMember) shouldBe true
        checker.isAdmin(guildId, manageServerOnlyMember) shouldBe false
    }
}) {
    companion object {
        private fun member(
            roleIds: List<Long>,
            isAdmin: Boolean,
            canManageServer: Boolean,
        ): Member {
            val member = mockk<Member>()
            every { member.roles } returns roleIds.map { role(it) }
            every { member.hasPermission(Permission.ADMINISTRATOR) } returns isAdmin
            every { member.hasPermission(Permission.MANAGE_SERVER) } returns canManageServer
            return member
        }

        private fun role(id: Long): Role {
            val role = mockk<Role>()
            every { role.idLong } returns id
            return role
        }
    }
}
