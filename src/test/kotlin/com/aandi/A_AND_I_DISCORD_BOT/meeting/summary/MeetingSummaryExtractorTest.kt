package com.aandi.A_AND_I_DISCORD_BOT.meeting.summary

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Instant

class MeetingSummaryExtractorTest : FunSpec({

    test("정규식 기반으로 결정과 액션아이템을 추출한다") {
        val extractor = MeetingSummaryExtractor()
        val messages = listOf(
            message("결정: 이번 주 금요일에 배포 진행"),
            message("액션: 민수 - CI 캐시 최적화"),
            message("TODO: 영희 - 배포 체크리스트 업데이트"),
            message("- [ ] 철수: 운영 로그 대시보드 점검"),
        )

        val summary = extractor.extract(messages)

        summary.decisions shouldContainExactly listOf("이번 주 금요일에 배포 진행")
        summary.actionItems shouldContain "민수 - CI 캐시 최적화"
        summary.actionItems shouldContain "영희 - 배포 체크리스트 업데이트"
        summary.actionItems shouldContain "철수: 운영 로그 대시보드 점검"
    }

    test("핵심문장 추출은 명령어/URL 제외 후 긴 문장을 우선한다") {
        val extractor = MeetingSummaryExtractor()
        val messages = listOf(
            message("/회의 시작"),
            message("https://example.com"),
            message("이번 회의에서는 모각코 집계 정확도를 높이기 위해 자정 경계 처리부터 우선 적용한다."),
            message("DB 마이그레이션 순서는 V4 이후 회의요약으로 확정한다."),
        )

        val summary = extractor.extract(messages)

        summary.highlights.size shouldBe 2
        summary.highlights.first().contains("모각코 집계 정확도") shouldBe true
    }
}) {
    companion object {
        private fun message(content: String): MeetingSummaryExtractor.MeetingMessage =
            MeetingSummaryExtractor.MeetingMessage(
                authorId = 1L,
                content = content,
                createdAt = Instant.now(),
            )
    }
}
