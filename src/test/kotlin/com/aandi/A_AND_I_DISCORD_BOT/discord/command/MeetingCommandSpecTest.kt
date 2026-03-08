package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

class MeetingCommandSpecTest : FunSpec({

    val spec = MeetingCommandSpec()

    test("회의 명령은 기록/항목 서브커맨드를 제공한다") {
        val meetingCommand = spec.definitions()
            .first { it.name == "회의" } as SlashCommandData
        val subcommandsByName = meetingCommand.subcommands.associateBy { it.name }

        meetingCommand.subcommands.map { it.name } shouldContainExactly listOf("시작", "종료", "기록", "항목")
        subcommandsByName shouldContainKey "기록"
        subcommandsByName shouldContainKey "항목"
    }

    test("회의 시작 서브커맨드의 채널 옵션은 선택값이다") {
        val meetingCommand = spec.definitions()
            .first { it.name == "회의" } as SlashCommandData
        val start = meetingCommand.subcommands.first { it.name == "시작" }

        start.options.first { it.name == "채널" }.isRequired shouldBe false
    }

    test("회의 기록 서브커맨드는 유형/내용/담당자/기한/회의아이디 옵션을 가진다") {
        val meetingCommand = spec.definitions()
            .first { it.name == "회의" } as SlashCommandData
        val record = meetingCommand.subcommands.first { it.name == "기록" }

        record.options.map { it.name } shouldContainExactly listOf("유형", "내용", "담당자", "기한", "회의아이디")

        val typeOption = record.options.first { it.name == "유형" }
        typeOption.type shouldBe OptionType.STRING
        typeOption.isRequired shouldBe true
        typeOption.choices.map { it.name } shouldContainExactly listOf("결정", "액션", "투두")
    }

    test("회의 항목 서브커맨드는 동작/아이디/회의아이디 옵션을 가진다") {
        val meetingCommand = spec.definitions()
            .first { it.name == "회의" } as SlashCommandData
        val item = meetingCommand.subcommands.first { it.name == "항목" }

        item.options.map { it.name } shouldContainExactly listOf("동작", "아이디", "회의아이디")

        val actionOption = item.options.first { it.name == "동작" }
        actionOption.type shouldBe OptionType.STRING
        actionOption.isRequired shouldBe true
        actionOption.choices.map { it.name } shouldContainExactly listOf("조회", "취소")
    }

    test("회의 스펙은 공개 표면에 단일 회의 명령만 노출한다") {
        spec.definitions().map { it.name } shouldContainExactly listOf("회의")
    }
})
