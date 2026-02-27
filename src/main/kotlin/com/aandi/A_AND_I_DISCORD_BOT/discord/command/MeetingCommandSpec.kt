package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(70)
class MeetingCommandSpec : DiscordCommandSpec {

    override fun definitions(): List<CommandData> {
        return listOf(
            Commands.slash("회의", "회의 진행 도구")
                .addSubcommands(
                    SubcommandData("시작", "회의 스레드를 생성합니다.")
                        .addOptions(
                            OptionData(OptionType.CHANNEL, "채널", "회의 시작 메시지를 생성할 텍스트 채널", true)
                                .setChannelTypes(ChannelType.TEXT),
                        ),
                    SubcommandData("종료", "최근 회의를 종료하고 스레드를 아카이브합니다."),
                ),
        )
    }
}
