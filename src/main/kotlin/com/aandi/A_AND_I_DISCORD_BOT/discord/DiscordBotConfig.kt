package com.aandi.A_AND_I_DISCORD_BOT.discord

import com.aandi.A_AND_I_DISCORD_BOT.agenda.handler.AgendaSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.ingestion.VoiceStateIngestionListener
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.handler.MogakcoSlashCommandHandler
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DiscordBotConfig(
    private val pingSlashCommandListener: PingSlashCommandListener,
    private val agendaSlashCommandHandler: AgendaSlashCommandHandler,
    private val mogakcoSlashCommandHandler: MogakcoSlashCommandHandler,
    private val voiceStateIngestionListener: VoiceStateIngestionListener,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
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
                voiceStateIngestionListener,
            )
            .build()

        jda.awaitReady()
        val guildId = guildIdRaw.trim().toLongOrNull()
        if (guildId == null) {
            registerGlobalCommands(jda)
            log.info("Discord bot logged in and /ping, /agenda, /mogakco registered globally")
            return jda
        }

        registerGuildCommands(jda, guildId)
        log.info("Discord bot logged in and /ping, /agenda, /mogakco registered for guild {}", guildId)
        return jda
    }

    private fun registerGuildCommands(jda: JDA, guildId: Long) {
        val guild = jda.getGuildById(guildId)
            ?: throw IllegalStateException("Bot is not connected to guild $guildId.")
        guild.updateCommands()
            .addCommands(commandDefinitions())
            .complete()
    }

    private fun registerGlobalCommands(jda: JDA) {
        jda.updateCommands()
            .addCommands(commandDefinitions())
            .complete()
    }

    private fun commandDefinitions(): List<CommandData> = listOf(
        Commands.slash("ping", "Health check"),
        Commands.slash("agenda", "오늘 안건 링크 관리")
            .addSubcommands(
                SubcommandData("set", "오늘 안건 링크 등록/수정")
                    .addOption(OptionType.STRING, "url", "http/https 링크", true)
                    .addOption(OptionType.STRING, "title", "안건 제목", false),
                SubcommandData("today", "오늘 안건 링크 조회"),
                SubcommandData("recent", "최근 안건 링크 조회")
                    .addOption(OptionType.INTEGER, "days", "조회할 최근 일수(기본 7)", false),
            ),
        Commands.slash("mogakco", "모각코 관리")
            .addSubcommandGroups(
                SubcommandGroupData("channel", "모각코 채널 설정")
                    .addSubcommands(
                        SubcommandData("add", "모각코 채널 등록")
                            .addOptions(
                                OptionData(OptionType.CHANNEL, "channel", "모각코 집계 음성채널", true)
                                    .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE),
                            ),
                        SubcommandData("remove", "모각코 채널 등록 해제")
                            .addOptions(
                                OptionData(OptionType.CHANNEL, "channel", "등록 해제할 음성채널", true)
                                    .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE),
                            ),
                        SubcommandData("list", "등록된 모각코 채널 목록 조회"),
                    ),
            )
            .addSubcommands(
                SubcommandData("leaderboard", "모각코 랭킹 조회")
                    .addOptions(
                        OptionData(OptionType.STRING, "period", "조회 기간", true)
                            .addChoice("주간", "week")
                            .addChoice("월간", "month"),
                        OptionData(OptionType.INTEGER, "top", "조회 인원 수(기본 10)", false),
                    ),
                SubcommandData("me", "내 모각코 통계 조회")
                    .addOptions(
                        OptionData(OptionType.STRING, "period", "조회 기간", true)
                            .addChoice("주간", "week")
                            .addChoice("월간", "month"),
                    ),
            ),
    )
}
