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
        agendaValue = "agenda_set",
        mogakcoMeValue = "mogakco_me",
        settingsHelpValue = "settings_help",
    )

    test("HOME_V2-채널 이동 버튼 3개와 더보기 행을 고정 구성한다") {
        val components = builder.buildHomeV2Components(
            guildId = 10L,
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
        row1Buttons.map { it.label } shouldContainExactly listOf("회의 이동", "과제 이동", "모각코 이동")
        row1Buttons.map { it.isDisabled } shouldContainExactly listOf(false, false, false)
    }

    test("HOME_V2-채널 미설정 시에도 버튼 행 구조를 유지하고 disabled 처리한다") {
        val components = builder.buildHomeV2Components(
            guildId = 10L,
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
        row1Buttons.map { it.isDisabled } shouldContainExactly listOf(true, true, true)
    }

    test("HOME_V2-안건 링크가 있으면 3행에 링크 버튼을 추가한다") {
        val components = builder.buildHomeV2Components(
            guildId = 10L,
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

    test("HOME_V2-더보기 메뉴는 안건 설정·내 기록·설정 도움말만 제공한다") {
        val components = builder.buildHomeV2Components(
            guildId = 10L,
            agendaUrl = null,
            channelTargets = HomeDashboardComponentBuilder.ChannelTargets(
                meetingChannelId = 11L,
                assignmentChannelId = 12L,
                mogakcoChannelId = 13L,
            ),
            moreMenuOptions = options,
        )

        val menu = components[1].components.first().shouldBeInstanceOf<StringSelectMenu>()
        menu.options.map { it.label } shouldContainExactly listOf("안건 설정", "내 기록(개인)", "설정/도움말")
    }
})
