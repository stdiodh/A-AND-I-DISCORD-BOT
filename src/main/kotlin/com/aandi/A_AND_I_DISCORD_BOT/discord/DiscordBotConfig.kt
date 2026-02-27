package com.aandi.A_AND_I_DISCORD_BOT.discord

import com.aandi.A_AND_I_DISCORD_BOT.admin.handler.AdminSettingSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.agenda.handler.AgendaSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.assignment.handler.AssignmentSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler.HomeSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.discord.command.DiscordCommandSpec
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionRouter
import com.aandi.A_AND_I_DISCORD_BOT.ingestion.VoiceStateIngestionListener
import com.aandi.A_AND_I_DISCORD_BOT.meeting.handler.MeetingSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.meeting.voice.handler.MeetingVoiceSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.handler.MogakcoSlashCommandHandler
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class DiscordBotConfig(
    private val pingSlashCommandListener: PingSlashCommandListener,
    private val agendaSlashCommandHandler: AgendaSlashCommandHandler,
    private val mogakcoSlashCommandHandler: MogakcoSlashCommandHandler,
    private val adminSettingSlashCommandHandler: AdminSettingSlashCommandHandler,
    private val assignmentSlashCommandHandler: AssignmentSlashCommandHandler,
    private val homeSlashCommandHandler: HomeSlashCommandHandler,
    private val interactionRouter: InteractionRouter,
    private val meetingSlashCommandHandler: MeetingSlashCommandHandler,
    private val meetingVoiceSlashCommandHandler: MeetingVoiceSlashCommandHandler,
    private val voiceStateIngestionListener: VoiceStateIngestionListener,
    private val commandSpecs: List<DiscordCommandSpec>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean(destroyMethod = "shutdown")
    @DependsOnDatabaseInitialization
    fun jda(
        @Value("\${discord.token}") token: String,
        @Value("\${discord.guild-id:}") guildIdRaw: String,
    ): JDA {
        val jda = JDABuilder.createDefault(token)
            .enableIntents(GatewayIntent.GUILD_VOICE_STATES)
            .enableCache(CacheFlag.VOICE_STATE)
            .addEventListeners(
                pingSlashCommandListener,
                agendaSlashCommandHandler,
                mogakcoSlashCommandHandler,
                adminSettingSlashCommandHandler,
                assignmentSlashCommandHandler,
                homeSlashCommandHandler,
                interactionRouter,
                meetingSlashCommandHandler,
                meetingVoiceSlashCommandHandler,
                voiceStateIngestionListener,
            )
            .build()

        jda.awaitReady()
        val guildId = guildIdRaw.trim().toLongOrNull()
        if (guildId == null) {
            registerGlobalCommands(jda)
            log.info("Discord bot logged in and commands registered globally")
            return jda
        }

        registerGuildCommands(jda, guildId)
        log.info("Discord bot logged in and commands registered for guild {}", guildId)
        return jda
    }

    private fun registerGuildCommands(jda: JDA, guildId: Long) {
        val guild = jda.getGuildById(guildId)
            ?: throw IllegalStateException("Bot is not connected to guild $guildId.")
        guild.updateCommands()
            .addCommands(collectCommandDefinitions())
            .complete()
    }

    private fun registerGlobalCommands(jda: JDA) {
        jda.updateCommands()
            .addCommands(collectCommandDefinitions())
            .complete()
    }

    private fun collectCommandDefinitions() = commandSpecs.flatMap { it.definitions() }
}
