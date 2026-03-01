package com.aandi.A_AND_I_DISCORD_BOT.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "feature")
class FeatureFlagsProperties {
    var homeV2: Boolean = false
    var meetingSummaryV2: Boolean = false
    var taskQuickregisterV2: Boolean = false
}

