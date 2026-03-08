package com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu

class HomeDashboardComponentBuilderTest : FunSpec({

    val builder = HomeDashboardComponentBuilder()
    val options = HomeDashboardComponentBuilder.MoreMenuOptions(
        agendaValue = "agenda",
        assignmentListValue = "assignment_list",
        mogakcoValue = "mogakco",
        settingsValue = "settings",
        helpValue = "help",
    )

    test("HOME_V2-상태판 행동 버튼 3개와 더보기 행을 고정 구성한다") {
        val components = builder.buildHomeV2Components(
            guildId = 10L,
            activeMeetingThreadId = null,
            agendaUrl = null,
            channelTargets = HomeDashboardComponentBuilder.ChannelTargets(
                meetingChannelId = 11L,
                assignmentChannelId = 12L,
                mogakcoChannelId = 13L,
            ),
            moreMenuOptions = options,
        )

        components.size shouldBe 2
        val row1Buttons = components[0].components.map { it.shouldBeInstanceOf<Button>() }
        row1Buttons.map { it.label } shouldContainExactly listOf("회의 시작", "빠른 과제", "내 기록")
        row1Buttons.map { it.isDisabled } shouldContainExactly listOf(false, false, false)
    }

    test("HOME_V2-진행 중 회의가 있으면 회의 버튼을 스레드 열기로 전환한다") {
        val components = builder.buildHomeV2Components(
            guildId = 10L,
            activeMeetingThreadId = 77L,
            agendaUrl = null,
            channelTargets = HomeDashboardComponentBuilder.ChannelTargets(
                meetingChannelId = 11L,
                assignmentChannelId = 12L,
                mogakcoChannelId = 13L,
            ),
            moreMenuOptions = options,
        )

        val meetingButton = components[0].components.first().shouldBeInstanceOf<Button>()
        meetingButton.label shouldBe "진행 중 회의 열기"
        meetingButton.url shouldBe "https://discord.com/channels/10/77"
    }

    test("HOME_V2-설정 미완료 상태에서는 설정 시작/내 기록/도움말 버튼을 노출한다") {
        val components = builder.buildHomeV2Components(
            guildId = 10L,
            activeMeetingThreadId = null,
            agendaUrl = null,
            channelTargets = HomeDashboardComponentBuilder.ChannelTargets(
                meetingChannelId = null,
                assignmentChannelId = null,
                mogakcoChannelId = null,
            ),
            moreMenuOptions = options,
        )

        components.size shouldBe 2
        val row1Buttons = components[0].components.map { it.shouldBeInstanceOf<Button>() }
        row1Buttons.map { it.label } shouldContainExactly listOf("설정 시작", "내 기록", "도움말")
        row1Buttons.map { it.isDisabled } shouldContainExactly listOf(false, false, false)
    }

    test("HOME_V2-안건 링크가 있으면 3행에 링크 버튼을 추가한다") {
        val components = builder.buildHomeV2Components(
            guildId = 10L,
            activeMeetingThreadId = null,
            agendaUrl = "https://docs.google.com/agenda",
            channelTargets = HomeDashboardComponentBuilder.ChannelTargets(
                meetingChannelId = 11L,
                assignmentChannelId = 12L,
                mogakcoChannelId = 13L,
            ),
            moreMenuOptions = options,
        )

        components.size shouldBe 3
        val linkButton = components[2].components.first().shouldBeInstanceOf<Button>()
        linkButton.label shouldBe "오늘 안건 링크"
        linkButton.url shouldBe "https://docs.google.com/agenda"
    }

    test("HOME_V2-더보기 메뉴는 안건/과제목록/모각코/설정/도움말을 제공한다") {
        val components = builder.buildHomeV2Components(
            guildId = 10L,
            activeMeetingThreadId = null,
            agendaUrl = null,
            channelTargets = HomeDashboardComponentBuilder.ChannelTargets(
                meetingChannelId = 11L,
                assignmentChannelId = 12L,
                mogakcoChannelId = 13L,
            ),
            moreMenuOptions = options,
        )

        val menu = components[1].components.first().shouldBeInstanceOf<StringSelectMenu>()
        menu.options.map { it.label } shouldContainExactly listOf("안건", "과제목록", "모각코", "설정", "도움말")
    }
})
