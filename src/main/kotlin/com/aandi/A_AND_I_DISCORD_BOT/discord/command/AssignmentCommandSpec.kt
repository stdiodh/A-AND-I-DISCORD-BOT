package com.aandi.A_AND_I_DISCORD_BOT.discord.command

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
                    SubcommandData("등록", "과제 등록 모달 열기"),
                    SubcommandData("목록", "과제 목록 조회")
                        .addOptions(
                            OptionData(OptionType.STRING, "상태", "대기/완료/취소", false)
                                .addChoice("대기", "대기")
                                .addChoice("완료", "완료")
                                .addChoice("취소", "취소"),
                        ),
                    SubcommandData("상세", "과제 상세 조회")
                        .addOptions(
                            OptionData(OptionType.INTEGER, "과제아이디", "조회할 과제 ID", true),
                        ),
                    SubcommandData("완료", "과제 완료 처리")
                        .addOptions(
                            OptionData(OptionType.INTEGER, "과제아이디", "완료 처리할 과제 ID", true),
                        ),
                    SubcommandData("삭제", "과제 삭제(취소) 처리")
                        .addOptions(
                            OptionData(OptionType.INTEGER, "과제아이디", "삭제할 과제 ID", true),
                        ),
                ),
        )
    }
}
