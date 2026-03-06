package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

class AdminSettingCommandSpecTest : FunSpec({

    val spec = AdminSettingCommandSpec()

    test("설정 명령은 마법사와 상태 서브커맨드를 제공한다") {
        val settingCommand = spec.definitions()
            .first { it.name == "설정" } as SlashCommandData
        val subcommandsByName = settingCommand.subcommands.associateBy { it.name }

        settingCommand.subcommands.map { it.name } shouldContainExactly listOf("마법사", "상태")
        subcommandsByName shouldContainKey "마법사"
        subcommandsByName shouldContainKey "상태"
    }

    test("설정 마법사는 핵심 채널/역할 옵션을 선택적으로 받는다") {
        val settingCommand = spec.definitions()
            .first { it.name == "설정" } as SlashCommandData
        val wizard = settingCommand.subcommands.first { it.name == "마법사" }

        wizard.options.map { it.name } shouldContainExactly listOf(
            "운영진역할",
            "회의열기역할",
            "홈채널",
            "회의채널",
            "모각코채널",
            "모각코음성추가",
            "모각코음성해제",
            "과제공지채널",
            "과제알림역할",
        )
    }

    test("설정 스펙은 공개 표면을 마법사/상태로 제한한다") {
        val settingCommand = spec.definitions()
            .first { it.name == "설정" } as SlashCommandData
        settingCommand.subcommands.map { it.name } shouldContainExactly listOf("마법사", "상태")
    }
})
