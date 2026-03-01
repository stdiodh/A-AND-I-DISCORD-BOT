# A&I Discord Bot 운영 런북

## 1) 필수 봇 권한
- 공통
  - `메시지 보기(View Channel)`
  - `메시지 전송(Send Messages)`
  - `메시지 히스토리 보기(Read Message History)`
  - `스레드 보기/참여(스레드 사용 권한)`
- 홈 대시보드 고정
  - `메시지 관리(Manage Messages)` (`핀 고정/해제`에 필요)
- 과제 알림(역할 멘션 사용 시)
  - 역할 멘션 가능 상태 또는 `모든 멘션 허용(Mention Everyone)` 필요

## 2) 홈 설치 절차 (`/홈 설치`)
1. 홈을 둘 텍스트 채널에서 `/홈 설치` 실행
2. 응답(ephemeral)에서 생성/재사용된 메시지 정보를 확인
3. 홈 메시지에 `홈 고정 상태`가 `✅ 고정됨`인지 확인
4. 이후 갱신은 `/홈 갱신` 또는 내부 업데이트(edit-only)로 반영

## 3) 핀 실패 트러블슈팅
- `❌ 권한 부족`
  - 원인: 봇에 `메시지 관리` 권한 없음
  - 조치: 봇 역할 권한/채널 오버라이드에서 `메시지 관리` 허용 후 재시도
- `❌ 핀 한도 초과`
  - 원인: 채널 핀 50개 초과
  - 조치: 기존 핀 정리 후 `/홈 설치` 또는 재확인 버튼 실행
- `❌ 고정 실패`
  - 원인: 채널 접근 불가, 메시지 삭제, 일시적 Discord API 실패 등
  - 조치: 채널 접근권한/메시지 존재 여부 확인 후 재시도

## 4) 회의 요약이 비어 있을 때
- 증상
  - 결정/액션이 0건으로 표시되고 안내문이 함께 노출됨
- 운영 처리
1. 요약 메시지의 `요약 재생성` 버튼 실행
2. 여전히 비면 `결정 추가` / `액션 추가` 버튼으로 수동 보강
3. 필요 시 회의 중 `/결정`, `/액션` 명령으로 구조화 항목을 먼저 누적

## 5) 관측(로그) 포인트
- 홈
  - `home.ensure.start|done|failed`
  - `home.update.start|done|failed`
  - `home.pin.*` (attempt/no_permission/success/limit_reached/failed)
- 회의 요약
  - `meeting.summary.start`
  - `meeting.summary.end` (messageCount, participantCount, durationMs)
  - `meeting.summary.failed` (errorType, durationMs)
- 과제 빠른등록 V2
  - `task.quick_register.draft_created`
  - `task.quick_register.selection_updated`
  - `task.quick_register.confirm.success|failed`
  - `task.quick_register.canceled`

## 6) 개인정보/민감정보 로그 정책
- 로그에는 메시지 본문/요약 원문을 남기지 않는다.
- 식별자(`guildId`, `threadId`, `messageId`, `taskId`, `userId`)와 개수/상태 코드만 기록한다.
