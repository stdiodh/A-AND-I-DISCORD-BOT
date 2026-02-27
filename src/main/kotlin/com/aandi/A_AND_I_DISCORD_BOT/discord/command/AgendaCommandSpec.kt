package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(20)
class AgendaCommandSpec : DiscordCommandSpec {

    override fun definitions(): List<CommandData> {
        return listOf(
            Commands.slash("안건", "오늘 안건 링크 관리")
                .addSubcommands(
                    SubcommandData("생성", "오늘 안건 링크 등록/수정")
                        .addOption(OptionType.STRING, "링크", "http/https 링크", true)
                        .addOption(OptionType.STRING, "제목", "안건 제목", false),
                    SubcommandData("오늘", "오늘 안건 링크 조회"),
                    SubcommandData("최근", "최근 안건 링크 조회")
                        .addOption(OptionType.INTEGER, "일수", "조회할 최근 일수(기본 7)", false),
                ),
        )
    }
}
