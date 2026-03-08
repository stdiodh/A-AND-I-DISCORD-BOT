package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

class AssignmentCommandSpecTest : FunSpec({

    val spec = AssignmentCommandSpec()

    test("과제 등록은 V2 모달 기본 경로를 위해 제목/링크 옵션이 선택값이다") {
        val assignmentCommand = spec.definitions()
            .first { it.name == "과제" } as SlashCommandData
        val create = assignmentCommand.subcommands.first { it.name == "등록" }

        create.options.first { it.name == "제목" }.isRequired shouldBe false
        create.options.first { it.name == "링크" }.isRequired shouldBe false
    }

    test("과제 스펙은 목록/상세/등록/완료만 공개한다") {
        val assignmentCommand = spec.definitions()
            .first { it.name == "과제" } as SlashCommandData

        assignmentCommand.subcommands.map { it.name } shouldContainExactly listOf("등록", "목록", "상세", "완료")
    }
})
