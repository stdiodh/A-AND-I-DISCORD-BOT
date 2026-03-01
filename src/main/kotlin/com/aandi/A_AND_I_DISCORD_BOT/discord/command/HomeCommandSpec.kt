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
@Order(60)
class HomeCommandSpec : DiscordCommandSpec {

    override fun definitions(): List<CommandData> {
        return listOf(
            Commands.slash("홈", "운영 홈 대시보드")
                .addSubcommands(
                    SubcommandData("생성", "홈 메시지를 생성합니다.")
                        .addOptions(
                            OptionData(OptionType.CHANNEL, "채널", "홈 메시지를 게시할 텍스트 채널", true)
                                .setChannelTypes(ChannelType.TEXT),
                        ),
                    SubcommandData("갱신", "홈 메시지를 최신 상태로 갱신합니다."),
                    SubcommandData("설치", "홈 메시지를 보장 생성/복구하고 고정 상태를 점검합니다.")
                        .addOptions(
                            OptionData(OptionType.CHANNEL, "채널", "홈 메시지를 둘 텍스트 채널(기본: 현재 채널)", false)
                                .setChannelTypes(ChannelType.TEXT),
                        ),
                ),
            Commands.slash("home", "home dashboard")
                .addSubcommands(
                    SubcommandData("create", "create home message")
                        .addOptions(
                            OptionData(OptionType.CHANNEL, "channel", "target text channel", true)
                                .setChannelTypes(ChannelType.TEXT),
                        ),
                    SubcommandData("refresh", "refresh home message"),
                    SubcommandData("install", "ensure home message and verify pin status")
                        .addOptions(
                            OptionData(OptionType.CHANNEL, "channel", "target text channel (default: current channel)", false)
                                .setChannelTypes(ChannelType.TEXT),
                        ),
                ),
        )
    }
}
