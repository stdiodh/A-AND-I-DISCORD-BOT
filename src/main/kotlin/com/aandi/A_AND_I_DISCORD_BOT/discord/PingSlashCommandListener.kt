package com.aandi.A_AND_I_DISCORD_BOT.discord

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component

@Component
class PingSlashCommandListener : ListenerAdapter() {
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != "핑" && event.name != "ping") {
            return
        }
        event.reply("pong")
            .setEphemeral(true)
            .queue()
    }
}
