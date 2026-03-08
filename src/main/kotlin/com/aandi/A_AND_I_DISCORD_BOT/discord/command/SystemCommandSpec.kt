package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(10)
class SystemCommandSpec : DiscordCommandSpec {

    override fun definitions(): List<CommandData> {
        return listOf(
            Commands.slash("핑", "봇 동작 확인"),
            Commands.slash("도움말", "주요 명령어 사용법과 레거시 매핑 안내")
                .addOptions(
                    OptionData(OptionType.STRING, "카테고리", "확인할 명령 카테고리", false)
                        .addChoice("전체", "all")
                        .addChoice("회의", "meeting")
                        .addChoice("안건", "agenda")
                        .addChoice("과제", "assignment")
                        .addChoice("모각코", "mogakco")
                        .addChoice("설정/홈", "admin_home"),
                ),
        )
    }
}
