package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(30)
class MogakcoCommandSpec : DiscordCommandSpec {

    override fun definitions(): List<CommandData> {
        return listOf(
            Commands.slash("모각코", "모각코 관리")
                .addSubcommands(
                    SubcommandData("랭킹", "모각코 랭킹 조회")
                        .addOptions(
                            OptionData(OptionType.STRING, "기간", "조회 기간", true)
                                .addChoice("일간", "day")
                                .addChoice("주간", "week")
                                .addChoice("월간", "month"),
                            OptionData(OptionType.INTEGER, "인원", "조회 인원 수(기본 10)", false),
                        ),
                    SubcommandData("내정보", "내 모각코 통계 조회")
                        .addOptions(
                            OptionData(OptionType.STRING, "기간", "조회 기간", true)
                                .addChoice("일간", "day")
                                .addChoice("주간", "week")
                                .addChoice("월간", "month"),
                        ),
                    SubcommandData("오늘", "오늘 모각코 출석/1시간 목표 진행률 조회"),
                ),
        )
    }
}
