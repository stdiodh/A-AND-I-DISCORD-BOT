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
@Order(40)
class AdminSettingCommandSpec : DiscordCommandSpec {

    override fun definitions(): List<CommandData> {
        return listOf(
            Commands.slash("설정", "운영 설정")
                .addSubcommands(
                    SubcommandData("운영진역할", "운영진 역할 설정")
                        .addOptions(
                            OptionData(OptionType.ROLE, "역할", "운영진으로 사용할 역할", true),
                        ),
                    SubcommandData("운영진해제", "운영진 역할 해제"),
                    SubcommandData("운영진조회", "현재 운영진 역할 조회"),
                    SubcommandData("회의열기역할", "회의 시작 권한 역할 설정")
                        .addOptions(
                            OptionData(OptionType.ROLE, "역할", "회의 시작 권한을 부여할 역할", true),
                        ),
                    SubcommandData("회의열기해제", "회의 시작 권한 역할 해제"),
                    SubcommandData("회의열기조회", "현재 회의 시작 권한 역할 조회"),
                    SubcommandData("회의채널", "회의 공지 채널 설정")
                        .addOptions(
                            OptionData(OptionType.CHANNEL, "채널", "회의 시작/요약 공지 채널", true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                        ),
                    SubcommandData("회의채널해제", "회의 공지 채널 해제"),
                    SubcommandData("모각코채널", "모각코 랭킹 공지 채널 설정")
                        .addOptions(
                            OptionData(OptionType.CHANNEL, "채널", "모각코 랭킹 공지 채널", true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                        ),
                    SubcommandData("모각코채널해제", "모각코 랭킹 공지 채널 해제"),
                    SubcommandData("과제공지채널", "과제 공지 채널 설정")
                        .addOptions(
                            OptionData(OptionType.CHANNEL, "채널", "과제 알림/공지 채널", true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                        ),
                    SubcommandData("과제공지해제", "과제 공지 채널 해제"),
                    SubcommandData("채널조회", "회의/모각코/과제 공지 채널 조회"),
                ),
        )
    }
}
