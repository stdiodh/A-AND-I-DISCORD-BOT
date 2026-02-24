package com.aandi.A_AND_I_DISCORD_BOT.ingestion

import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.VoiceSessionService
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.VoiceSessionService.VoiceEventOutcome
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.VoiceTransition
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class VoiceStateIngestionListener(
    private val voiceSessionService: VoiceSessionService,
) : ListenerAdapter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
        if (event.member.user.isBot) {
            return
        }

        val transition = VoiceTransition(
            guildId = event.guild.idLong,
            userId = event.member.idLong,
            oldChannelId = event.channelLeft?.idLong,
            newChannelId = event.channelJoined?.idLong,
            occurredAt = Instant.now(),
        )
        val outcome = voiceSessionService.ingest(transition)
        if (outcome == VoiceEventOutcome.Ignored) {
            return
        }

        log.debug(
            "Voice event processed: guild={}, user={}, old={}, new={}, outcome={}",
            transition.guildId,
            transition.userId,
            transition.oldChannelId,
            transition.newChannelId,
            outcome,
        )
    }
}
