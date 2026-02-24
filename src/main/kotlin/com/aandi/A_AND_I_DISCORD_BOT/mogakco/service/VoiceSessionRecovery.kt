package com.aandi.A_AND_I_DISCORD_BOT.mogakco.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class VoiceSessionRecovery(
    private val voiceSessionService: VoiceSessionService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun closeOpenSessions() {
        val closedCount = voiceSessionService.closeOpenSessionsAtStartup()
        if (closedCount > 0) {
            log.info("Closed {} open voice sessions at startup.", closedCount)
        }
    }
}
