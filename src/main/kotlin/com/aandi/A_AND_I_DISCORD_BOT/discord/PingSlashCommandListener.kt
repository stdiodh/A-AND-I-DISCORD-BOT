package com.aandi.A_AND_I_DISCORD_BOT.discord

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class PingSlashCommandListener : ListenerAdapter() {
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (isPingCommand(event.name)) {
            event.reply("pong")
                .setEphemeral(true)
                .queue()
            return
        }
        if (!isHelpCommand(event.name)) {
            return
        }
        val category = event.getOption(OPTION_CATEGORY_KO)?.asString ?: HELP_CATEGORY_ALL
        val helpText = helpByCategory[category] ?: helpByCategory[HELP_CATEGORY_ALL]
        val embed = EmbedBuilder()
            .setTitle("A&I 봇 명령어 도움말")
            .setDescription(helpText)
            .setColor(Color(0x5865F2))
            .setFooter("레거시 명령은 안내 문구와 함께 신규 명령으로 유도됩니다.")
            .build()
        event.replyEmbeds(embed)
            .setEphemeral(true)
            .queue()
    }

    private fun isPingCommand(name: String): Boolean {
        return name == COMMAND_PING_KO || name == COMMAND_PING_EN
    }

    private fun isHelpCommand(name: String): Boolean {
        return name == COMMAND_HELP_KO || name == COMMAND_HELP_EN
    }

    companion object {
        private const val COMMAND_PING_KO = "핑"
        private const val COMMAND_PING_EN = "ping"
        private const val COMMAND_HELP_KO = "도움말"
        private const val COMMAND_HELP_EN = "help"
        private const val OPTION_CATEGORY_KO = "카테고리"

        private const val HELP_CATEGORY_ALL = "all"
        private const val HELP_CATEGORY_MEETING = "meeting"
        private const val HELP_CATEGORY_AGENDA = "agenda"
        private const val HELP_CATEGORY_ASSIGNMENT = "assignment"
        private const val HELP_CATEGORY_MOGAKCO = "mogakco"
        private const val HELP_CATEGORY_ADMIN_HOME = "admin_home"

        private val helpByCategory = mapOf(
            HELP_CATEGORY_ALL to """
                **핵심 명령**
                - 홈: `/홈 설치`
                - 회의: `/회의 시작|종료|기록|항목`
                - 안건: `/안건 오늘|최근|설정`
                - 과제: `/과제 목록|상세|등록|완료`
                - 모각코: `/모각코 오늘|내정보|랭킹`
                - 설정: `/설정 마법사|상태`
            """.trimIndent(),
            HELP_CATEGORY_MEETING to """
                **회의**
                - `/회의 시작 [채널:#회의채널]`
                - `/회의 종료 회의아이디:<id>`
                - `/회의 기록 유형:<결정|액션|투두> 내용:... [담당자] [기한] [회의아이디:<id>]`
                - `/회의 항목 동작:<조회|취소> [아이디] [회의아이디:<id>]`
            """.trimIndent(),
            HELP_CATEGORY_AGENDA to """
                **안건**
                - `/안건 설정 링크:<URL> [제목]`
                - `/안건 오늘`
                - `/안건 최근 [일수:<기본 7>]`
            """.trimIndent(),
            HELP_CATEGORY_ASSIGNMENT to """
                **과제**
                - `/과제 등록` (기본: 모달 3필드 - 제목/마감일/링크)
                - `/과제 목록 [상태]`
                - `/과제 상세 아이디:<id>`
                - `/과제 완료 아이디:<id>`
            """.trimIndent(),
            HELP_CATEGORY_MOGAKCO to """
                **모각코**
                - `/모각코 랭킹 기간:<일간|주간|월간> [인원]`
                - `/모각코 내정보 기간:<일간|주간|월간>`
                - `/모각코 오늘`
            """.trimIndent(),
            HELP_CATEGORY_ADMIN_HOME to """
                **설정/홈**
                - `/설정 마법사 [회의채널] [모각코채널] [과제공지채널] [과제알림역할] [회의열기역할]`
                - `/설정 상태`
                - `/홈 설치 [채널]`
            """.trimIndent(),
        )
    }
}
