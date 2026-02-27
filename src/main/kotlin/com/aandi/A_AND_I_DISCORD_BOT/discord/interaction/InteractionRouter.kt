package com.aandi.A_AND_I_DISCORD_BOT.discord.interaction

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.Locale

@Component
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class InteractionRouter(
    private val handlers: List<InteractionPrefixHandler>,
) : ListenerAdapter() {

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val prefix = extractPrefix(event.componentId) ?: return
        dispatchButton(prefix, event)
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        val prefix = extractPrefix(event.componentId) ?: return
        dispatchStringSelect(prefix, event)
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        val prefix = extractPrefix(event.modalId) ?: return
        dispatchModal(prefix, event)
    }

    private fun extractPrefix(id: String?): String? {
        if (id.isNullOrBlank()) {
            return null
        }
        val separator = id.indexOf(':')
        if (separator <= 0) {
            return null
        }
        return id.substring(0, separator)
            .lowercase(Locale.ROOT)
    }

    private fun dispatchButton(prefix: String, event: ButtonInteractionEvent) {
        handlers
            .asSequence()
            .filter { it.supports(prefix) }
            .firstOrNull { it.onButton(event) }
    }

    private fun dispatchStringSelect(prefix: String, event: StringSelectInteractionEvent) {
        handlers
            .asSequence()
            .filter { it.supports(prefix) }
            .firstOrNull { it.onStringSelect(event) }
    }

    private fun dispatchModal(prefix: String, event: ModalInteractionEvent) {
        handlers
            .asSequence()
            .filter { it.supports(prefix) }
            .firstOrNull { it.onModal(event) }
    }
}
