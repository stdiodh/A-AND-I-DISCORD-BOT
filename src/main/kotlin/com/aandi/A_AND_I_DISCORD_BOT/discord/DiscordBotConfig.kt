package com.aandi.A_AND_I_DISCORD_BOT.discord

import com.aandi.A_AND_I_DISCORD_BOT.admin.handler.AdminSettingSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.agenda.handler.AgendaSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.assignment.handler.AssignmentSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.handler.HomeSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.discord.interaction.InteractionRouter
import com.aandi.A_AND_I_DISCORD_BOT.ingestion.VoiceStateIngestionListener
import com.aandi.A_AND_I_DISCORD_BOT.meeting.handler.MeetingSlashCommandHandler
import com.aandi.A_AND_I_DISCORD_BOT.meeting.voice.handler.MeetingVoiceSlashCommandHandler
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
        Commands.slash("설정", "운영 설정")
            .addSubcommands(
                SubcommandData("운영진역할", "운영진 역할 설정")
                    .addOptions(
                        OptionData(OptionType.ROLE, "역할", "운영진으로 사용할 역할", true),
                    ),
                SubcommandData("운영진조회", "현재 운영진 역할 조회"),
            ),
        Commands.slash("과제", "과제 관리")
            .addSubcommands(
                SubcommandData("등록", "과제 등록 모달 열기"),
                SubcommandData("목록", "과제 목록 조회")
                    .addOptions(
                        OptionData(OptionType.STRING, "상태", "대기/완료/취소", false)
                            .addChoice("대기", "대기")
                            .addChoice("완료", "완료")
                            .addChoice("취소", "취소"),
                    ),
                SubcommandData("상세", "과제 상세 조회")
                    .addOptions(
                        OptionData(OptionType.INTEGER, "과제아이디", "조회할 과제 ID", true),
                    ),
                SubcommandData("완료", "과제 완료 처리")
                    .addOptions(
                        OptionData(OptionType.INTEGER, "과제아이디", "완료 처리할 과제 ID", true),
                    ),
                SubcommandData("삭제", "과제 삭제(취소) 처리")
                    .addOptions(
                        OptionData(OptionType.INTEGER, "과제아이디", "삭제할 과제 ID", true),
                    ),
            ),
        Commands.slash("홈", "운영 홈 대시보드")
            .addSubcommands(
                SubcommandData("생성", "홈 메시지를 생성합니다.")
                    .addOptions(
                        OptionData(OptionType.CHANNEL, "채널", "홈 메시지를 게시할 텍스트 채널", true)
                            .setChannelTypes(ChannelType.TEXT),
                    ),
                SubcommandData("갱신", "홈 메시지를 최신 상태로 갱신합니다."),
            ),
        Commands.slash("회의", "회의 진행 도구")
            .addSubcommands(
                SubcommandData("시작", "회의 스레드를 생성합니다.")
                    .addOptions(
                        OptionData(OptionType.CHANNEL, "채널", "회의 시작 메시지를 생성할 텍스트 채널", true)
                            .setChannelTypes(ChannelType.TEXT),
                    ),
                SubcommandData("종료", "최근 회의를 종료하고 스레드를 아카이브합니다."),
            ),
        Commands.slash("회의음성", "회의 음성요약 Skeleton 제어")
            .addSubcommands(
                SubcommandData("시작", "회의 음성요약 시작 (Skeleton)")
                    .addOptions(
                        OptionData(OptionType.INTEGER, "회의아이디", "회의 세션 ID 또는 스레드 ID", true),
                        OptionData(OptionType.CHANNEL, "보이스채널", "대상 보이스 채널", true)
                            .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE),
                    ),
                SubcommandData("종료", "회의 음성요약 종료 (Skeleton)")
                    .addOptions(
                        OptionData(OptionType.INTEGER, "회의아이디", "회의 세션 ID 또는 스레드 ID", true),
                    ),
                SubcommandData("상태", "회의 음성요약 상태 조회")
                    .addOptions(
                        OptionData(OptionType.INTEGER, "회의아이디", "회의 세션 ID 또는 스레드 ID", true),
                    ),
            ),
    )
}
