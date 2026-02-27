package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(40)
class AdminSettingCommandSpec : DiscordCommandSpec {

    override fun definitions(): List<CommandData> {
        return listOf(
            Commands.slash("설정", "운영 설정")
                .addSubcommands(
                    SubcommandData("운영진역할", "운영진 역할 설정")
                        .addOptions(
                            OptionData(OptionType.ROLE, "대상역할", "운영진으로 사용할 역할", true),
                        ),
                    SubcommandData("운영진해제", "운영진 역할 해제"),
                    SubcommandData("운영진조회", "현재 운영진 역할 조회"),
                ),
        )
    }
}
