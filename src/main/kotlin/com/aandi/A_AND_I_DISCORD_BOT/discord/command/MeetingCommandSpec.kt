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
                            OptionData(OptionType.CHANNEL, "채널", "회의 시작 메시지를 생성할 텍스트 채널(기본: 설정 채널)", false)
                                .setChannelTypes(ChannelType.TEXT),
                        ),
                    SubcommandData("종료", "회의 ID로 회의를 종료하고 스레드를 아카이브합니다.")
                        .addOption(OptionType.INTEGER, "회의아이디", "종료할 회의 세션 ID", true),
                    SubcommandData("기록", "진행 중 회의에 결정/액션/TODO를 기록합니다.")
                        .addOptions(
                            OptionData(OptionType.STRING, "유형", "기록 유형", true)
                                .addChoice("결정", "decision")
                                .addChoice("액션", "action")
                                .addChoice("투두", "todo"),
                            OptionData(OptionType.STRING, "내용", "기록 내용", true),
                            OptionData(OptionType.USER, "담당자", "담당자(유형=액션일 때 선택)", false),
                            OptionData(OptionType.STRING, "기한", "기한(YYYY-MM-DD, 유형=액션일 때 선택)", false),
                            OptionData(OptionType.INTEGER, "회의아이디", "회의 세션 ID(스레드 밖에서는 필수)", false),
                        ),
                    SubcommandData("항목", "회의 항목 조회/취소")
                        .addOptions(
                            OptionData(OptionType.STRING, "동작", "항목 동작", true)
                                .addChoice("조회", "list")
                                .addChoice("취소", "cancel"),
                            OptionData(OptionType.INTEGER, "아이디", "취소할 항목 ID(동작=취소 시 필수)", false),
                            OptionData(OptionType.INTEGER, "회의아이디", "회의 세션 ID(스레드 밖에서는 필수)", false),
                        ),
                ),
        )
    }
}
