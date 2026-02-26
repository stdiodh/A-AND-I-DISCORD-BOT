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
            log.info("Discord bot logged in and /핑, /안건, /모각코 registered globally")
            return jda
        }

        registerGuildCommands(jda, guildId)
        log.info("Discord bot logged in and /핑, /안건, /모각코 registered for guild {}", guildId)
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
        Commands.slash("핑", "봇 동작 확인"),
        Commands.slash("안건", "오늘 안건 링크 관리")
            .addSubcommands(
                SubcommandData("생성", "오늘 안건 링크 등록/수정")
                    .addOption(OptionType.STRING, "링크", "http/https 링크", true)
                    .addOption(OptionType.STRING, "제목", "안건 제목", false),
                SubcommandData("오늘", "오늘 안건 링크 조회"),
                SubcommandData("최근", "최근 안건 링크 조회")
                    .addOption(OptionType.INTEGER, "일수", "조회할 최근 일수(기본 7)", false),
            ),
        Commands.slash("모각코", "모각코 관리")
            .addSubcommandGroups(
                SubcommandGroupData("채널", "모각코 채널 설정")
                    .addSubcommands(
                        SubcommandData("등록", "모각코 채널 등록")
                            .addOptions(
                                OptionData(OptionType.CHANNEL, "음성채널", "모각코 집계 음성채널", true)
                                    .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE),
                            ),
                        SubcommandData("해제", "모각코 채널 등록 해제")
                            .addOptions(
                                OptionData(OptionType.CHANNEL, "음성채널", "등록 해제할 음성채널", true)
                                    .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE),
                            ),
                        SubcommandData("목록", "등록된 모각코 채널 목록 조회"),
                    ),
            )
            .addSubcommands(
                SubcommandData("랭킹", "모각코 랭킹 조회")
                    .addOptions(
                        OptionData(OptionType.STRING, "기간", "조회 기간", true)
                            .addChoice("주간", "week")
                            .addChoice("월간", "month"),
                        OptionData(OptionType.INTEGER, "인원", "조회 인원 수(기본 10)", false),
                    ),
                SubcommandData("내정보", "내 모각코 통계 조회")
                    .addOptions(
                        OptionData(OptionType.STRING, "기간", "조회 기간", true)
                            .addChoice("주간", "week")
                            .addChoice("월간", "month"),
                    ),
            ),
    )
}
