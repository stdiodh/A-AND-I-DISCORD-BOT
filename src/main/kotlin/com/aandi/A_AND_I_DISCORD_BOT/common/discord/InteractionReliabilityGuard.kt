package com.aandi.A_AND_I_DISCORD_BOT.common.discord

import com.aandi.A_AND_I_DISCORD_BOT.common.log.StructuredLog
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class InteractionReliabilityGuard {

    private val log = LoggerFactory.getLogger(javaClass)

    fun safeDefer(
        interaction: IDeferrableCallback,
        preferUpdate: Boolean = false,
        onDeferred: (InteractionCtx) -> Unit,
        onFailure: ((InteractionCtx, Throwable) -> Unit)? = null,
    ) {
        val ctx = InteractionCtx.from(interaction)
        if (interaction.isAcknowledged) {
            log.info(
                StructuredLog.event(
                    name = "interaction.defer.already_ack",
                    "interactionId" to ctx.interactionId,
                    "guildId" to ctx.guildId,
                    "userId" to ctx.userId,
                    "customId" to ctx.customId,
                    "elapsedMs" to ctx.elapsedMs(),
                ),
            )
            onDeferred(ctx)
            return
        }

        if (preferUpdate && tryDeferUpdate(interaction, ctx, onDeferred, onFailure)) {
            return
        }

        val deferAction = deferReplyEphemeral(interaction)
        if (deferAction == null) {
            onFailure?.invoke(ctx, IllegalStateException("Unsupported interaction type for deferReply"))
                ?: safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/홈 설치` 또는 `/회의 종료`를 다시 시도해 주세요.",
                )
            return
        }

        deferAction.queue(
            {
                log.info(
                    StructuredLog.event(
                        name = "interaction.defer.reply.ok",
                        "interactionId" to ctx.interactionId,
                        "guildId" to ctx.guildId,
                        "userId" to ctx.userId,
                        "customId" to ctx.customId,
                        "elapsedMs" to ctx.elapsedMs(),
                    ),
                )
                onDeferred(ctx)
            },
            { exception ->
                log.error(
                    StructuredLog.event(
                        name = "interaction.defer.reply.failed",
                        "interactionId" to ctx.interactionId,
                        "guildId" to ctx.guildId,
                        "userId" to ctx.userId,
                        "customId" to ctx.customId,
                        "elapsedMs" to ctx.elapsedMs(),
                    ),
                    exception,
                )
                onFailure?.invoke(ctx, exception) ?: safeFailureReply(
                    ctx = ctx,
                    alternativeCommandGuide = "`/홈 설치` 또는 `/회의 종료`를 다시 시도해 주세요.",
                )
            },
        )
    }

    fun safeEditReply(
        ctx: InteractionCtx,
        message: String,
        embeds: List<MessageEmbed> = emptyList(),
        components: List<ActionRow> = emptyList(),
    ) {
        val action = ctx.interaction.hook.editOriginal(message)
        if (embeds.isNotEmpty()) {
            action.setEmbeds(embeds)
        }
        if (components.isNotEmpty()) {
            action.setComponents(components)
        }
        action.queue(
            {
                log.info(
                    StructuredLog.event(
                        name = "interaction.edit_reply.ok",
                        "interactionId" to ctx.interactionId,
                        "guildId" to ctx.guildId,
                        "userId" to ctx.userId,
                        "customId" to ctx.customId,
                        "elapsedMs" to ctx.elapsedMs(),
                    ),
                )
            },
            { exception ->
                log.error(
                    StructuredLog.event(
                        name = "interaction.edit_reply.failed",
                        "interactionId" to ctx.interactionId,
                        "guildId" to ctx.guildId,
                        "userId" to ctx.userId,
                        "customId" to ctx.customId,
                        "elapsedMs" to ctx.elapsedMs(),
                    ),
                    exception,
                )
            },
        )
    }

    fun safeFollowUp(
        ctx: InteractionCtx,
        message: String,
        ephemeral: Boolean = true,
    ) {
        ctx.interaction.hook.sendMessage(message)
            .setEphemeral(ephemeral)
            .queue(
                {
                    log.info(
                        StructuredLog.event(
                            name = "interaction.follow_up.ok",
                            "interactionId" to ctx.interactionId,
                            "guildId" to ctx.guildId,
                            "userId" to ctx.userId,
                            "customId" to ctx.customId,
                            "elapsedMs" to ctx.elapsedMs(),
                        ),
                    )
                },
                { exception ->
                    log.error(
                        StructuredLog.event(
                            name = "interaction.follow_up.failed",
                            "interactionId" to ctx.interactionId,
                            "guildId" to ctx.guildId,
                            "userId" to ctx.userId,
                            "customId" to ctx.customId,
                            "elapsedMs" to ctx.elapsedMs(),
                        ),
                        exception,
                    )
                },
            )
    }

    fun safeFailureReply(ctx: InteractionCtx, alternativeCommandGuide: String) {
        val message = "처리 중 문제가 발생했어요. [다시 시도] [대체 명령 안내: $alternativeCommandGuide]"
        if (ctx.interaction.isAcknowledged) {
            safeEditReply(ctx, message)
            return
        }
        val replyable = ctx.interaction as? IReplyCallback ?: return
        replyable.reply(message)
            .setEphemeral(true)
            .queue()
    }

    private fun tryDeferUpdate(
        interaction: IDeferrableCallback,
        ctx: InteractionCtx,
        onDeferred: (InteractionCtx) -> Unit,
        onFailure: ((InteractionCtx, Throwable) -> Unit)?,
    ): Boolean {
        when (interaction) {
            is ButtonInteractionEvent -> {
                interaction.deferEdit().queue(
                    {
                        log.info(
                            StructuredLog.event(
                                name = "interaction.defer.update.ok",
                                "interactionId" to ctx.interactionId,
                                "guildId" to ctx.guildId,
                                "userId" to ctx.userId,
                                "customId" to ctx.customId,
                                "elapsedMs" to ctx.elapsedMs(),
                            ),
                        )
                        onDeferred(ctx)
                    },
                    { exception ->
                        onFailure?.invoke(ctx, exception) ?: safeFailureReply(
                            ctx = ctx,
                            alternativeCommandGuide = "`/홈 설치` 또는 `/회의 종료`를 다시 시도해 주세요.",
                        )
                    },
                )
                return true
            }

            is StringSelectInteractionEvent -> {
                interaction.deferEdit().queue(
                    {
                        log.info(
                            StructuredLog.event(
                                name = "interaction.defer.update.ok",
                                "interactionId" to ctx.interactionId,
                                "guildId" to ctx.guildId,
                                "userId" to ctx.userId,
                                "customId" to ctx.customId,
                                "elapsedMs" to ctx.elapsedMs(),
                            ),
                        )
                        onDeferred(ctx)
                    },
                    { exception ->
                        onFailure?.invoke(ctx, exception) ?: safeFailureReply(
                            ctx = ctx,
                            alternativeCommandGuide = "`/홈 설치` 또는 `/회의 종료`를 다시 시도해 주세요.",
                        )
                    },
                )
                return true
            }

            is EntitySelectInteractionEvent -> {
                interaction.deferEdit().queue(
                    {
                        log.info(
                            StructuredLog.event(
                                name = "interaction.defer.update.ok",
                                "interactionId" to ctx.interactionId,
                                "guildId" to ctx.guildId,
                                "userId" to ctx.userId,
                                "customId" to ctx.customId,
                                "elapsedMs" to ctx.elapsedMs(),
                            ),
                        )
                        onDeferred(ctx)
                    },
                    { exception ->
                        onFailure?.invoke(ctx, exception) ?: safeFailureReply(
                            ctx = ctx,
                            alternativeCommandGuide = "`/홈 설치` 또는 `/회의 종료`를 다시 시도해 주세요.",
                        )
                    },
                )
                return true
            }

            else -> return false
        }
    }

    private fun deferReplyEphemeral(interaction: IDeferrableCallback): ReplyCallbackAction? {
        return when (interaction) {
            is ButtonInteractionEvent -> interaction.deferReply(true)
            is StringSelectInteractionEvent -> interaction.deferReply(true)
            is EntitySelectInteractionEvent -> interaction.deferReply(true)
            is ModalInteractionEvent -> interaction.deferReply(true)
            is SlashCommandInteractionEvent -> interaction.deferReply(true)
            else -> null
        }
    }

    data class InteractionCtx(
        val interaction: IDeferrableCallback,
        val interactionId: Long,
        val guildId: Long?,
        val userId: Long?,
        val customId: String?,
        private val startedAtMillis: Long,
    ) {
        fun elapsedMs(): Long = System.currentTimeMillis() - startedAtMillis

        companion object {
            fun from(interaction: IDeferrableCallback): InteractionCtx {
                val customId = when (interaction) {
                    is ButtonInteractionEvent -> interaction.componentId
                    is StringSelectInteractionEvent -> interaction.componentId
                    is EntitySelectInteractionEvent -> interaction.componentId
                    is ModalInteractionEvent -> interaction.modalId
                    is SlashCommandInteractionEvent -> {
                        val sub = interaction.subcommandName?.let { ":$it" }.orEmpty()
                        "${interaction.name}$sub"
                    }
                    else -> null
                }
                return InteractionCtx(
                    interaction = interaction,
                    interactionId = interaction.idLong,
                    guildId = interaction.guild?.idLong,
                    userId = interaction.user.idLong,
                    customId = customId,
                    startedAtMillis = System.currentTimeMillis(),
                )
            }
        }
    }
}
