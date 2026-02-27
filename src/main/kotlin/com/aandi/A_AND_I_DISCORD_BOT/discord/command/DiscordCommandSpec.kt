package com.aandi.A_AND_I_DISCORD_BOT.discord.command

import net.dv8tion.jda.api.interactions.commands.build.CommandData

interface DiscordCommandSpec {
    fun definitions(): List<CommandData>
}
