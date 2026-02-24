package com.aandi.A_AND_I_DISCORD_BOT.discord

import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.MogakcoService
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class VoiceStateListener(
    private val mogakcoService: MogakcoService,
) : ListenerAdapter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
        if (event.member.user.isBot) {
            return
        }
        val joinedChannelId = event.channelJoined?.idLong
        val leftChannelId = event.channelLeft?.idLong
        mogakcoService.handleVoiceStateUpdate(
            guildId = event.guild.idLong,
            userId = event.member.idLong,
            joinedChannelId = joinedChannelId,
            leftChannelId = leftChannelId,
        )
        if (joinedChannelId != null || leftChannelId != null) {
            log.debug(
                "Voice state updated: guild={}, user={}, joined={}, left={}",
                event.guild.idLong,
                event.member.idLong,
                joinedChannelId,
                leftChannelId,
            )
        }
    }
}
