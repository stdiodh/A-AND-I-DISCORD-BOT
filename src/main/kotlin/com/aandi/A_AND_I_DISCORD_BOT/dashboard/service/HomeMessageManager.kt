package com.aandi.A_AND_I_DISCORD_BOT.dashboard.service

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.common.log.StructuredLog
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class HomeMessageManager(
    private val guildConfigService: GuildConfigService,
    @Lazy private val jda: JDA,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun ensureHomeMessage(
        guildId: Long,
        preferredChannelId: Long?,
        payload: HomePayload,
    ): EnsureResult {
        val dashboard = guildConfigService.getDashboard(guildId)
        val configuredChannelId = dashboard.channelId
        val configuredMessageId = dashboard.messageId
        val marker = markerForGuild(guildId)
        val targetChannelId = preferredChannelId ?: configuredChannelId

        if (configuredChannelId != null && configuredMessageId != null) {
            val configuredChannel = jda.getTextChannelById(configuredChannelId)
            if (configuredChannel != null) {
                val configuredMessage = fetchMessage(configuredChannel, configuredMessageId)
                if (configuredMessage != null) {
                    log.info(
                        StructuredLog.event(
                            name = "reused_existing_home_message",
                            "guildId" to guildId,
                            "channelId" to configuredChannelId,
                            "messageId" to configuredMessage.idLong,
                        ),
                    )
                    return EnsureResult.Success(
                        channelId = configuredChannelId,
                        messageId = configuredMessage.idLong,
                        message = configuredMessage,
                        outcome = EnsureOutcome.REUSED,
                    )
                }

                val repaired = findRecentHomeMessage(configuredChannel, marker)
                if (repaired != null) {
                    guildConfigService.setDashboard(guildId, configuredChannelId, repaired.idLong)
                    log.info(
                        StructuredLog.event(
                            name = "repaired_missing_home_message",
                            "guildId" to guildId,
                            "channelId" to configuredChannelId,
                            "messageId" to repaired.idLong,
                        ),
                    )
                    return EnsureResult.Success(
                        channelId = configuredChannelId,
                        messageId = repaired.idLong,
                        message = repaired,
                        outcome = EnsureOutcome.REPAIRED,
                    )
                }
            }
        }

        if (targetChannelId == null) {
            return EnsureResult.ChannelNotConfigured
        }
        val targetChannel = jda.getTextChannelById(targetChannelId) ?: return EnsureResult.ChannelNotFound

        val repairedInTarget = findRecentHomeMessage(targetChannel, marker)
        if (repairedInTarget != null) {
            guildConfigService.setDashboard(guildId, targetChannelId, repairedInTarget.idLong)
            log.info(
                StructuredLog.event(
                    name = "repaired_missing_home_message",
                    "guildId" to guildId,
                    "channelId" to targetChannelId,
                    "messageId" to repairedInTarget.idLong,
                ),
            )
            return EnsureResult.Success(
                channelId = targetChannelId,
                messageId = repairedInTarget.idLong,
                message = repairedInTarget,
                outcome = EnsureOutcome.REPAIRED,
            )
        }

        val created = targetChannel.sendMessageEmbeds(withMarker(payload.embed, marker))
            .setComponents(payload.components)
            .complete()
        guildConfigService.setDashboard(guildId, targetChannelId, created.idLong)
        log.info(
            StructuredLog.event(
                name = "created_home_message",
                "guildId" to guildId,
                "channelId" to targetChannelId,
                "messageId" to created.idLong,
            ),
        )
        return EnsureResult.Success(
            channelId = targetChannelId,
            messageId = created.idLong,
            message = created,
            outcome = EnsureOutcome.CREATED,
        )
    }

    fun updateHomeMessage(guildId: Long, payload: HomePayload): UpdateResult {
        val dashboard = guildConfigService.getDashboard(guildId)
        val channelId = dashboard.channelId ?: return UpdateResult.NotConfigured
        val messageId = dashboard.messageId ?: return UpdateResult.NotConfigured
        val channel = jda.getTextChannelById(channelId) ?: return UpdateResult.ChannelNotFound
        val message = fetchMessage(channel, messageId) ?: return UpdateResult.MessageNotFound
        val marker = markerForGuild(guildId)

        message.editMessageEmbeds(withMarker(payload.embed, marker))
            .setComponents(payload.components)
            .complete()
        return UpdateResult.Success(channelId = channelId, messageId = messageId)
    }

    internal fun markerForGuild(guildId: Long): String = "$HOME_MARKER_PREFIX$guildId"

    private fun fetchMessage(channel: TextChannel, messageId: Long): Message? {
        return runCatching { channel.retrieveMessageById(messageId).complete() }.getOrNull()
    }

    private fun findRecentHomeMessage(channel: TextChannel, marker: String): Message? {
        val history = runCatching { channel.history.retrievePast(MARKER_SCAN_LIMIT).complete() }
            .getOrElse { emptyList() }
        return history.firstOrNull { message -> hasMarker(message, marker) }
    }

    private fun hasMarker(message: Message, marker: String): Boolean {
        return message.embeds.any { it.footer?.text == marker }
    }

    private fun withMarker(embed: MessageEmbed, marker: String): MessageEmbed {
        return EmbedBuilder(embed)
            .setFooter(marker)
            .build()
    }

    data class HomePayload(
        val embed: MessageEmbed,
        val components: List<ActionRow>,
    )

    enum class EnsureOutcome {
        REUSED,
        REPAIRED,
        CREATED,
    }

    sealed interface EnsureResult {
        data class Success(
            val channelId: Long,
            val messageId: Long,
            val message: Message,
            val outcome: EnsureOutcome,
        ) : EnsureResult

        data object ChannelNotConfigured : EnsureResult
        data object ChannelNotFound : EnsureResult
    }

    sealed interface UpdateResult {
        data class Success(
            val channelId: Long,
            val messageId: Long,
        ) : UpdateResult

        data object NotConfigured : UpdateResult
        data object ChannelNotFound : UpdateResult
        data object MessageNotFound : UpdateResult
    }

    companion object {
        private const val HOME_MARKER_PREFIX = "A&I_HOME_MARKER:"
        private const val MARKER_SCAN_LIMIT = 50
    }
}
