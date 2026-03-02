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
                    SubcommandData("항목조회", "회의 중 기록한 결정/액션/TODO 항목을 조회합니다."),
                    SubcommandData("항목취소", "회의 중 기록한 항목을 ID로 취소합니다.")
                        .addOption(OptionType.INTEGER, "아이디", "취소할 항목 ID", true),
                ),
            Commands.slash("결정", "진행 중 회의에 결정을 기록합니다.")
                .addOption(OptionType.STRING, "내용", "결정 내용", true),
            Commands.slash("액션", "진행 중 회의에 액션을 기록합니다.")
                .addOption(OptionType.STRING, "내용", "액션 내용", true)
                .addOption(OptionType.USER, "담당자", "담당자(선택)", false)
                .addOption(OptionType.STRING, "기한", "기한(YYYY-MM-DD, 선택)", false),
            Commands.slash("투두", "진행 중 회의에 TODO를 기록합니다.")
                .addOption(OptionType.STRING, "내용", "TODO 내용", true),
        )
    }
}
