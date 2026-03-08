package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

class CommandSurfaceSpecTest : FunSpec({

    test("홈 공개 표면은 설치만 노출한다") {
        val home = HomeCommandSpec().definitions().first { it.name == "홈" } as SlashCommandData
        home.subcommands.map { it.name } shouldContainExactly listOf("설치")
    }

    test("회의 공개 표면은 시작/종료/기록/항목만 노출한다") {
        val meeting = MeetingCommandSpec().definitions().first { it.name == "회의" } as SlashCommandData
        meeting.subcommands.map { it.name } shouldContainExactly listOf("시작", "종료", "기록", "항목")
    }

    test("안건 공개 표면은 오늘/최근/설정만 노출한다") {
        val agenda = AgendaCommandSpec().definitions().first { it.name == "안건" } as SlashCommandData
        agenda.subcommands.map { it.name } shouldContainExactly listOf("설정", "오늘", "최근")
    }

    test("과제 공개 표면은 목록/상세/등록/완료만 노출한다") {
        val assignment = AssignmentCommandSpec().definitions().first { it.name == "과제" } as SlashCommandData
        assignment.subcommands.map { it.name } shouldContainExactly listOf("등록", "목록", "상세", "완료")
    }

    test("모각코 공개 표면은 오늘/내정보/랭킹만 노출한다") {
        val mogakco = MogakcoCommandSpec().definitions().first { it.name == "모각코" } as SlashCommandData
        mogakco.subcommands.map { it.name } shouldContainExactly listOf("랭킹", "내정보", "오늘")
        mogakco.subcommandGroups shouldContainExactly emptyList()
    }

    test("설정 공개 표면은 마법사/상태만 노출한다") {
        val settings = AdminSettingCommandSpec().definitions().first { it.name == "설정" } as SlashCommandData
        settings.subcommands.map { it.name } shouldContainExactly listOf("마법사", "상태")
    }
})
