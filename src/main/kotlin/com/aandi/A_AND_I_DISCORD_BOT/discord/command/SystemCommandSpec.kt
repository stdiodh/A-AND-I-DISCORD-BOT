package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(10)
class SystemCommandSpec : DiscordCommandSpec {

    override fun definitions(): List<CommandData> {
        return listOf(
            Commands.slash("핑", "봇 동작 확인"),
        )
    }
}
