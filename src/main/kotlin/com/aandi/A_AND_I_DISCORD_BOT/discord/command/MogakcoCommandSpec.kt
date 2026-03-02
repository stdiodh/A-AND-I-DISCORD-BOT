package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(30)
class MogakcoCommandSpec : DiscordCommandSpec {

    override fun definitions(): List<CommandData> {
        return listOf(
            Commands.slash("모각코", "모각코 관리")
                .addSubcommandGroups(
                    SubcommandGroupData("채널", "모각코 채널 설정")
                        .addSubcommands(
                            SubcommandData("등록", "모각코 채널 등록")
                                .addOptions(
                                    OptionData(OptionType.CHANNEL, "채널", "모각코 집계 음성채널", true)
                                        .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE),
                                ),
                            SubcommandData("해제", "모각코 채널 등록 해제")
                                .addOptions(
                                    OptionData(OptionType.CHANNEL, "채널", "등록 해제할 음성채널", true)
                                        .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE),
                                ),
                            SubcommandData("목록", "등록된 모각코 채널 목록 조회"),
                        ),
                )
                .addSubcommands(
                    SubcommandData("랭킹", "모각코 랭킹 조회")
                        .addOptions(
                            OptionData(OptionType.STRING, "기간", "조회 기간", true)
                                .addChoice("주간", "week")
                                .addChoice("월간", "month"),
                            OptionData(OptionType.INTEGER, "인원", "조회 인원 수(기본 10)", false),
                        ),
                    SubcommandData("내정보", "내 모각코 통계 조회")
                        .addOptions(
                            OptionData(OptionType.STRING, "기간", "조회 기간", true)
                                .addChoice("주간", "week")
                                .addChoice("월간", "month"),
                        ),
                ),
        )
    }
}
