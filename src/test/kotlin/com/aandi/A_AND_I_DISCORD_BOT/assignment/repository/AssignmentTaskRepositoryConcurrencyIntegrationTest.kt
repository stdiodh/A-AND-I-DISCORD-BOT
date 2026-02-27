package com.aandi.A_AND_I_DISCORD_BOT.assignment.repository

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AssignmentTaskRepositoryConcurrencyIntegrationTest(
    @Autowired private val assignmentTaskRepository: AssignmentTaskRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionTemplate: TransactionTemplate,
) {

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE assignment_tasks, guild_config RESTART IDENTITY CASCADE")
        jdbcTemplate.update(
            "INSERT INTO guild_config (guild_id, timezone, mogakco_active_minutes) VALUES (?, ?, ?)",
            1001L,
            "Asia/Seoul",
            30,
        )
        val now = Instant.parse("2026-03-01T12:30:00Z")
        jdbcTemplate.update(
            """
            INSERT INTO assignment_tasks (
              guild_id, channel_id, title, verify_url, remind_at,
              status, created_by, created_at, updated_at, notified_at
            ) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, NULL)
            """.trimIndent(),
            1001L,
            2002L,
            "동시성 테스트 과제",
            "https://example.com/task",
            Timestamp.from(now.minusSeconds(30)),
            3003L,
            Timestamp.from(now.minusSeconds(90)),
            Timestamp.from(now.minusSeconds(90)),
        )
    }

    @Test
    fun `findDueTasksForUpdate-SKIP LOCKED로 동시 실행 시 한 트랜잭션만 row를 점유한다`() {
        val nowUtc = Instant.parse("2026-03-01T12:30:00Z")
        val graceStartUtc = nowUtc.minus(24, ChronoUnit.HOURS)
        val firstLocked = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val firstFuture = executor.submit<Long?> {
            transactionTemplate.execute<Long?> {
                val locked = assignmentTaskRepository.findDueTasksForUpdate(nowUtc, graceStartUtc, 1).firstOrNull()?.id
                firstLocked.countDown()
                if (locked != null) {
                    releaseFirst.await(3, TimeUnit.SECONDS)
                }
                locked
            }
        }
        val secondFuture = executor.submit<Long?> {
            firstLocked.await(3, TimeUnit.SECONDS)
            transactionTemplate.execute<Long?> {
                assignmentTaskRepository.findDueTasksForUpdate(nowUtc, graceStartUtc, 1).firstOrNull()?.id
            }
        }

        val secondLockedId = secondFuture.get(5, TimeUnit.SECONDS)
        releaseFirst.countDown()
        val firstLockedId = firstFuture.get(5, TimeUnit.SECONDS)
        executor.shutdownNow()

        assertNotNull(firstLockedId)
        assertEquals(1L, firstLockedId)
        assertNull(secondLockedId)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("postgres:15")

        @JvmStatic
        @DynamicPropertySource
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.task.scheduling.enabled") { "false" }
            registry.add("discord.enabled") { "false" }
        }
    }
}
