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
                    SubcommandData("마법사", "핵심 설정을 한 번에 적용")
                        .addOptions(
                            OptionData(OptionType.ROLE, "운영진역할", "운영진 권한 역할", false),
                            OptionData(OptionType.ROLE, "회의열기역할", "회의 시작 권한 역할", false),
                            OptionData(OptionType.CHANNEL, "홈채널", "홈 상태판 채널", false)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                            OptionData(OptionType.CHANNEL, "회의채널", "회의 시작/요약 공지 채널", false)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                            OptionData(OptionType.CHANNEL, "모각코채널", "모각코 랭킹 공지 채널", false)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                            OptionData(OptionType.CHANNEL, "모각코음성추가", "모각코 집계 음성 채널 추가", false)
                                .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE),
                            OptionData(OptionType.CHANNEL, "모각코음성해제", "모각코 집계 음성 채널 해제", false)
                                .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE),
                            OptionData(OptionType.CHANNEL, "과제공지채널", "과제 알림/공지 채널", false)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                            OptionData(OptionType.ROLE, "과제알림역할", "과제 기본 알림 역할", false),
                        ),
                    SubcommandData("상태", "현재 길드 설정 상태 조회"),
                ),
        )
    }
}
