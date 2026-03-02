package com.aandi.A_AND_I_DISCORD_BOT.common.auth

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import org.springframework.stereotype.Component

@Component
class HomeChannelGuard(
    private val guildConfigService: GuildConfigService,
) {

    fun validate(
        guildId: Long,
        currentChannelId: Long,
        featureChannelId: Long?,
        featureName: String,
        setupCommand: String,
        usageCommand: String,
    ): GuardResult {
        val homeChannelId = guildConfigService.getDashboard(guildId).channelId ?: return GuardResult.Allowed
        if (currentChannelId != homeChannelId) {
            return GuardResult.Allowed
        }
        if (featureChannelId == null) {
            return GuardResult.Blocked(
                "현재 채널은 홈 전용 채널입니다.\n" +
                    "$featureName 전용 채널이 아직 설정되지 않았습니다.\n" +
                    "`$setupCommand`로 먼저 설정해 주세요.",
            )
        }
        if (featureChannelId == homeChannelId) {
            return GuardResult.Allowed
        }
        return GuardResult.Blocked(
            "현재 채널은 홈 전용 채널입니다.\n" +
                "$featureName 명령은 <#$featureChannelId> 채널에서 실행해 주세요.\n" +
                "예: `$usageCommand`",
        )
    }

    sealed interface GuardResult {
        data object Allowed : GuardResult

        data class Blocked(
            val message: String,
        ) : GuardResult
    }
}
