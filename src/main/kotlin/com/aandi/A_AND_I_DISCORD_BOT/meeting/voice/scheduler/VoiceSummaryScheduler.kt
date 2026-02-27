package com.aandi.A_AND_I_DISCORD_BOT.meeting.voice.scheduler

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "voice.summary",
    name = ["enabled"],
    havingValue = "true",
)
class VoiceSummaryScheduler {

    private val log = LoggerFactory.getLogger(javaClass)

    // TODO(voice-summary): Java 25 + JDA 6.3 + DAVE + audio pipeline ready 시 작업 큐 폴링 구현
    @Scheduled(fixedDelayString = "\${voice.summary.poll-delay-ms:60000}")
    fun tick() {
        log.debug("Voice summary scheduler tick (skeleton)")
    }
}

