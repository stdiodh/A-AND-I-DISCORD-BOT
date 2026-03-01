package com.aandi.A_AND_I_DISCORD_BOT.common.discord

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback

class InteractionReliabilityGuardTest : FunSpec({

    test("safeDefer는 이미 acknowledge된 상호작용이면 즉시 onDeferred를 호출한다") {
        val guard = InteractionReliabilityGuard()
        val user = mockk<User>()
        val interaction = mockk<IDeferrableCallback>()

        every { user.idLong } returns 42L
        every { interaction.idLong } returns 99L
        every { interaction.user } returns user
        every { interaction.guild } returns null
        every { interaction.isAcknowledged } returns true

        var deferredCalled = false
        var failureCalled = false

        guard.safeDefer(
            interaction = interaction,
            onDeferred = { deferredCalled = true },
            onFailure = { _, _ -> failureCalled = true },
        )

        deferredCalled shouldBe true
        failureCalled shouldBe false
    }

    test("safeDefer는 지원하지 않는 타입이면 onFailure를 호출한다") {
        val guard = InteractionReliabilityGuard()
        val user = mockk<User>()
        val interaction = mockk<IDeferrableCallback>()

        every { user.idLong } returns 7L
        every { interaction.idLong } returns 11L
        every { interaction.user } returns user
        every { interaction.guild } returns null
        every { interaction.isAcknowledged } returns false

        var deferredCalled = false
        var failure: Throwable? = null

        guard.safeDefer(
            interaction = interaction,
            onDeferred = { deferredCalled = true },
            onFailure = { _, exception -> failure = exception },
        )

        deferredCalled shouldBe false
        val exception = failure.shouldBeInstanceOf<IllegalStateException>()
        exception.message shouldBe "Unsupported interaction type for deferReply"
    }
})
