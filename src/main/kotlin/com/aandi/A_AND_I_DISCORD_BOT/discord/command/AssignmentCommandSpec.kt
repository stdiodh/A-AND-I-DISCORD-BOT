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
@Order(50)
class AssignmentCommandSpec : DiscordCommandSpec {

    override fun definitions(): List<CommandData> {
        return listOf(
            Commands.slash("과제", "과제 관리")
                .addSubcommands(
                    SubcommandData("등록", "과제 등록")
                        .addOptions(
                            OptionData(OptionType.STRING, "제목", "예: 3주차 API 과제 제출 (미입력 시 V2 모달)", false),
                            OptionData(OptionType.STRING, "링크", "예: https://lms.example.com/tasks/123", false),
                            OptionData(OptionType.STRING, "알림", "예: 2026-03-01 21:30 (KST)", false),
                            OptionData(OptionType.STRING, "알림시각", "(레거시) 예: 2026-03-01 21:30 (KST)", false),
                            OptionData(OptionType.STRING, "마감", "예: 2026-03-02 23:59 (KST)", false),
                            OptionData(OptionType.STRING, "마감시각", "(레거시) 예: 2026-03-02 23:59 (KST)", false),
                            OptionData(OptionType.CHANNEL, "채널", "알림 전송 채널(기본: 현재 채널)", false)
                                .setChannelTypes(ChannelType.TEXT),
                            OptionData(OptionType.ROLE, "역할", "지정 시 해당 역할만 멘션", false),
                            OptionData(OptionType.ROLE, "알림역할", "(레거시) 지정 시 해당 역할만 멘션", false),
                            OptionData(OptionType.STRING, "임박알림옵션", "추천 프리셋(선택)", false)
                                .addChoice("기본 (24,3,1)", "24,3,1")
                                .addChoice("당일 집중 (6,3,1)", "6,3,1")
                                .addChoice("마감 직전 (3,1)", "3,1"),
                            OptionData(OptionType.STRING, "임박알림", "직접 입력 예: 24,3,1 (옵션보다 우선)", false),
                            OptionData(OptionType.STRING, "마감메시지", "마감 후 전송할 메시지(선택)", false),
                            OptionData(OptionType.STRING, "종료메시지", "(레거시) 마감 후 전송할 메시지(선택)", false),
                        ),
                    SubcommandData("목록", "과제 목록 조회")
                        .addOptions(
                            OptionData(OptionType.STRING, "상태", "대기/완료/종료", false)
                                .addChoice("대기", "대기")
                                .addChoice("완료", "완료")
                                .addChoice("종료", "종료"),
                        ),
                    SubcommandData("상세", "과제 상세 조회")
                        .addOptions(
                            OptionData(OptionType.INTEGER, "아이디", "조회할 과제 ID", false),
                            OptionData(OptionType.INTEGER, "과제아이디", "(레거시) 조회할 과제 ID", false),
                        ),
                    SubcommandData("완료", "과제 완료 처리")
                        .addOptions(
                            OptionData(OptionType.INTEGER, "아이디", "완료 처리할 과제 ID", false),
                            OptionData(OptionType.INTEGER, "과제아이디", "(레거시) 완료 처리할 과제 ID", false),
                        ),
                ),
        )
    }
}
