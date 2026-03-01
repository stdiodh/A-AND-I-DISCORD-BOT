package com.aandi.A_AND_I_DISCORD_BOT.discord.interaction

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent

interface InteractionPrefixHandler {

    fun supports(prefix: String): Boolean

    fun onButton(event: ButtonInteractionEvent): Boolean = false

    fun onStringSelect(event: StringSelectInteractionEvent): Boolean = false

    fun onEntitySelect(event: EntitySelectInteractionEvent): Boolean = false

    fun onModal(event: ModalInteractionEvent): Boolean = false
}
