package com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class HomeCustomIdParserTest : FunSpec({

    test("parse-유효한 customId를 domain action tail로 분해한다") {
        val parsed = HomeCustomIdParser.parse("home:mogakco:select:leaderboard")

        parsed?.domain shouldBe "mogakco"
        parsed?.action shouldBe "select"
        parsed?.tail shouldContainExactly listOf("leaderboard")
        parsed?.tailAt(0) shouldBe "leaderboard"
        parsed?.tailAt(1).shouldBeNull()
    }

    test("parse-prefix가 home이 아니면 null을 반환한다") {
        val parsed = HomeCustomIdParser.parse("task:create")

        parsed.shouldBeNull()
    }

    test("of-domain action tail로 customId를 생성한다") {
        val customId = HomeCustomIdParser.of("task", "modal", "create")

        customId shouldBe "home:task:modal:create"
    }
})
