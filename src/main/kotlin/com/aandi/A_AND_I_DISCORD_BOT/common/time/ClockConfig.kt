package com.aandi.A_AND_I_DISCORD_BOT.common.time

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfig {

    @Bean
    fun utcClock(): Clock = Clock.systemUTC()
}
