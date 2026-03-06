package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["voice.summary.enabled"], havingValue = "true")
@Order(80)
class MeetingVoiceCommandSpec : DiscordCommandSpec {

    override fun definitions(): List<CommandData> {
        return listOf(
            Commands.slash("회의음성", "회의 음성요약 Skeleton 제어")
                .addSubcommands(
                    SubcommandData("시작", "회의 음성요약 시작 (Skeleton)")
                        .addOptions(
                            OptionData(OptionType.INTEGER, "아이디", "회의 세션 ID 또는 스레드 ID", false),
                            OptionData(OptionType.INTEGER, "회의아이디", "(레거시) 회의 세션 ID 또는 스레드 ID", false),
                            OptionData(OptionType.CHANNEL, "채널", "대상 보이스 채널", false)
                                .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE),
                            OptionData(OptionType.CHANNEL, "보이스채널", "(레거시) 대상 보이스 채널", false)
                                .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE),
                        ),
                    SubcommandData("종료", "회의 음성요약 종료 (Skeleton)")
                        .addOptions(
                            OptionData(OptionType.INTEGER, "아이디", "회의 세션 ID 또는 스레드 ID", false),
                            OptionData(OptionType.INTEGER, "회의아이디", "(레거시) 회의 세션 ID 또는 스레드 ID", false),
                        ),
                    SubcommandData("상태", "회의 음성요약 상태 조회")
                        .addOptions(
                            OptionData(OptionType.INTEGER, "아이디", "회의 세션 ID 또는 스레드 ID", false),
                            OptionData(OptionType.INTEGER, "회의아이디", "(레거시) 회의 세션 ID 또는 스레드 ID", false),
                        ),
                ),
        )
    }
}
