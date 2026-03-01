# A&I Discord Bot API 명세서

이 문서는 A&I 통합 Discord Bot의 모든 인터랙션(슬래시 커맨드/이벤트)을 정리한 명세서입니다.
회의/안건은 Google Drive 문서로 관리하며, Bot은 "오늘 안건 링크 등록/조회"만 제공합니다.
모각코는 Discord 음성채널 접속 시간을 기반으로 누적시간/순위/참여율을 계산합니다.

## 목차
- [Command Category 기준](#command-category-기준)
- [공통 사항](#공통-사항)
- [SystemCommand](#systemcommand)
- [HomeDashboardCommand](#homedashboardcommand)
- [AgendaCommand](#agendacommand)
- [MeetingCommand](#meetingcommand)
- [MogakcoConfigCommand](#mogakcoconfigcommand)
- [MogakcoCommand](#mogakcocommand)
- [AssignmentCommand](#assignmentcommand)
- [MeetingVoiceSummaryCommand](#meetingvoicesummarycommand)
- [AdminSettingCommand](#adminsettingcommand)
- [VoiceEventIngestion](#voiceeventingestion)
- [데이터 스키마](#데이터-스키마)
- [에러 응답 형식](#에러-응답-형식)

---

## Command Category 기준

- `System`: 봇 상태/헬스체크
- `HomeDashboard`: 홈 대시보드 생성/갱신 및 버튼 진입점
- `Agenda`: 오늘 회의 안건(구글 문서 링크) 등록/조회
- `Meeting`: 회의 시작/종료 및 스레드 텍스트 기반 요약
- `Mogakco`: 모각코 통계 조회(누적/랭킹/참여율)
- `MogakcoConfig`: 모각코로 집계할 음성채널 등록/삭제
- `Assignment`: 과제 등록/조회/완료/삭제 및 알림
- `MeetingVoiceSummary`: 회의 음성요약 스켈레톤 제어(기본 비활성)
- `AdminSetting`: 운영진 역할 설정/조회
- `Ingestion`: 음성 이벤트 수집/세션 저장(슬래시 커맨드가 아닌 내부 이벤트)

---

## 공통 사항

### 시간/타임존
- 사용자 입력 시간(예: `2026-03-01 21:30`)은 `Asia/Seoul` 기준으로 해석한다.
- 사용자 표시/알림 시간은 `Asia/Seoul`(KST)로 출력한다.
- DB 저장 시간은 `TIMESTAMPTZ`(UTC)로 저장한다.
- DB에서 `+00`(UTC)로 보이는 값은 정상이며, 애플리케이션이 KST로 변환해 표시한다.
- 스케줄러 비교/조회 기준 시각은 UTC를 사용한다.
- "오늘"의 기준과 주간/월간 통계 범위 계산은 `Asia/Seoul` 기준으로 처리한다.
- 모각코/과제/회의 음성요약 모두 동일한 정책(`입력/표시 KST`, `저장 UTC`)을 따른다.

### 권한(Auth)
- `ADMIN_ROLE`: 길드 설정(`guild_config.admin_role_id`)에 등록된 역할을 가진 사용자
- `ANY`: 누구나 사용 가능
- **과제 권한 정책(적용)**:
  - 과제 등록/완료/삭제: `ADMIN_ROLE`
  - 과제 목록/상세: `ANY`
- **설정 권한 정책(적용)**:
  - `/설정 운영진역할`, `/설정 운영진해제`: `ADMIN_ROLE` 또는 Manage Server/Administrator 허용 (복구용 break-glass)
  - `/설정 운영진조회`: `ANY` (권장: ephemeral 응답)

### 응답 정책
- 민감/설정 변경: 기본 `ephemeral`
- 랭킹: 공개 채널 출력(선호) 또는 서버 정책에 따라 ephemeral

### 기능 플래그
- `FEATURE_HOME_V2` (default: false)
  - 홈 대시보드 V2 UI(2개 주요 버튼 + 더보기 셀렉트) 활성화
- `FEATURE_MEETING_SUMMARY_V2` (default: false)
  - 회의 요약 파이프라인 V2(수집 윈도우, 요약 산출물 저장, 재생성/수동보강 UI) 활성화
- `FEATURE_TASK_QUICKREGISTER_V2` (default: false)
  - 과제 빠른등록 V2(모달+2단계 선택) 활성화

### 명령어 표기 정책
- 기본 슬래시 커맨드는 한글 명칭을 사용한다. (`/핑`, `/안건`, `/모각코`, `/과제`, `/설정`)
- 전환 기간 호환을 위해 기존 영문 명령(`ping`, `agenda`, `mogakco`)도 라우팅에서 허용한다.

---

## SystemCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/핑` | 봇 동작 확인 | 없음 | `pong` 메시지 | ANY |

---

## HomeDashboardCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/홈 생성` | 홈 대시보드 메시지 생성 | **Options:**<br>- `채널` (TextChannel, required) | 임베드+버튼 홈 메시지 생성 후 `guild_config.dashboard_channel_id`/`dashboard_message_id` 저장 | ADMIN_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |
| SLASH | `/홈 갱신` | 기존 홈 대시보드 메시지 갱신 | 없음 | 저장된 홈 메시지 내용/버튼 갱신 | ADMIN_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |
| SLASH | `/홈 설치` | 홈 메시지를 보장 생성/복구하고 핀 상태를 점검 | **Options:**<br>- `채널` (TextChannel, optional, 기본: 현재 채널) | HomeMessageManager로 단일 홈 보장(재사용/복구/신규 생성) + 핀 시도 결과를 홈 임베드에 반영 | ADMIN_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |

### 홈 버튼 인터랙션
- HOME_V2(`FEATURE_HOME_V2=true`) 기준:
  - 1행 버튼: `[회의 시작/회의 종료] [과제 등록]`
  - 2행 셀렉트: `[더보기]`
    - 안건 설정
    - 과제 전체 보기
    - 모각코 전체 보기
    - 내 기록(개인)
    - 설정/도움말
  - `내 기록(개인)`은 ephemeral 응답으로 채널 스팸을 방지한다.
- HOME_V2 비활성 시 기존 버튼 구성을 유지한다.
- 목표: 명령어 타이핑 최소화. 버튼/셀렉트 → 모달/후속 액션 흐름 제공
- `custom_id` 규칙:
  - 대시보드 고정 버튼: `dash:*`
  - 회의 관련 모달/동작: `meeting:*`
  - 과제 관련 모달/동작: `assign:*`
  - 모각코 셀렉트: `mogakco:*`
  - 홈 더보기 셀렉트: `home:more_select`
- 이벤트 라우팅은 prefix 기반 `InteractionRouter`가 처리한다.

### 홈 메시지 보장 규칙
- 길드별 홈 메시지는 단일 레퍼런스를 유지한다. (`guild_config.dashboard_channel_id`, `dashboard_message_id`)
- `ensureHomeMessage` 호출 시:
  1. 저장된 메시지가 존재하면 재사용
  2. 저장 레퍼런스 메시지가 없으면 최근 메시지에서 홈 마커 검색 후 복구
  3. 둘 다 실패 시 신규 생성 후 레퍼런스 저장
- 홈 갱신은 기존 메시지 `edit` 기반으로 수행한다.

---

## AgendaCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/안건 생성` | 오늘(Asia/Seoul)의 안건 문서 링크를 등록/수정(Upsert) | **Options:**<br>- `링크` (String, required): http/https 링크<br>- `제목` (String, optional): 표시용 제목 | 등록 완료 메시지 + 링크 버튼 | ADMIN_ROLE |
| SLASH | `/안건 오늘` | 오늘(Asia/Seoul)의 안건 링크 조회 | 없음 | 등록된 링크 임베드/버튼 출력(없으면 안내 메시지) | ANY |
| SLASH | `/안건 최근` | 최근 N일 안건 링크 목록 조회 | **Options:**<br>- `일수` (Int, optional, default: 7) | 최근 링크 목록 출력 | ANY |

---

## MeetingCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/회의 시작` | 회의 시작 메시지 생성 후 스레드 생성 | **Options:**<br>- `채널` (TextChannel, required) | 지정 채널에 회의 시작 임베드 게시 후 `YYYY-MM-DD 회의` 스레드 생성 | ADMIN_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |
| SLASH | `/회의 종료` | 회의 요약 생성 후 세션 종료/스레드 아카이브 | 없음 | 결정/액션아이템/핵심문장 요약 임베드 게시 후 종료 임베드/아카이브 처리 | ADMIN_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |
| SLASH | `/회의 안건등록` | 오늘 회의 안건 링크 등록/수정 | **Options:**<br>- `링크` (String, required): http/https 링크<br>- `제목` (String, optional) | 등록 성공 메시지 + 링크 버튼 | ADMIN_ROLE |
| SLASH | `/회의 안건조회` | 오늘 회의 안건 링크 조회 | 없음 | 등록된 링크 안내 + 링크 버튼 (없으면 미등록 안내) | ANY |
| SLASH | `/결정` | 진행 중 회의에 결정 항목을 구조화 기록 | **Options:**<br>- `내용` (String, required) | 회의 세션/스레드/항목ID와 함께 저장 완료 메시지 | ADMIN_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |
| SLASH | `/액션` | 진행 중 회의에 액션 항목을 구조화 기록 | **Options:**<br>- `내용` (String, required)<br>- `담당자` (User, optional)<br>- `기한` (String, optional, YYYY-MM-DD) | 회의 세션/스레드/항목ID와 함께 저장 완료 메시지 | ADMIN_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |

### 회의 시작 처리 규칙
- `/회의 시작`은 지정 채널에 "회의 시작" 임베드를 먼저 게시하고, 그 메시지에서 스레드를 생성한다.
- 회의 세션 생성 시점에 오늘 안건이 존재하면 `meeting_sessions.agenda_link_id`로 연결 저장한다.
- 대시보드 버튼 `dash:meeting_start`는 동일한 회의 시작 로직을 호출한다.
- `dash:meeting_start` 경로는 대시보드 채널(설정 시) 또는 현재 채널을 사용한다.
- 오늘 안건이 있으면 스레드 첫 메시지에 링크 버튼(`오늘 안건 링크`)을 포함한다.
- 회의 스레드 생성 시 템플릿 메시지를 함께 게시한다.

### 회의 종료 요약 규칙
- 기본은 **스레드 텍스트 기반 요약**이며, `FEATURE_MEETING_SUMMARY_V2` 플래그로 V2 동작을 제어한다.
- V2 활성 시:
  - 수집 범위: `meetingStart ~ meetingEnd + buffer(기본 3초)` 윈도우
  - 메시지 수집은 배치/페이지 기반으로 수행하고, 결과 메시지 수(`messageCount`)를 기록한다.
  - 구조화 항목(`/결정`, `/액션`)과 추출 항목을 정규화 후 병합/중복제거한다.
  - 요약 임베드에 통계(`결정/액션/TODO/참여자`) 및 원문 수집 범위를 포함한다.
  - 요약 액션 버튼:
    - `요약 재생성`
    - `결정 추가`
    - `액션 추가`
    - `원문 보기/메시지 수 N개`
  - 요약 결과는 가능하면 기존 요약 메시지를 `edit`하여 중복 게시를 방지한다.
  - 요약 산출물은 `meeting_summary_artifacts`에 버전/수집범위/카운트와 함께 저장한다.
- V2 비활성 시 기존 MVP0(패턴 기반 추출) 동작을 유지한다.
- 종료 시 `meeting_sessions` 상태를 `ACTIVE -> ENDED`로 전이하고, 종료 임베드를 게시한 뒤 대상 스레드를 아카이브한다.

---

## MogakcoConfigCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/모각코 채널 등록` | 모각코로 집계할 음성채널을 등록 | **Options:**<br>- `음성채널` (VoiceChannel, required) | 등록 완료 메시지 (이미 등록된 경우 안내 후 종료) | ADMIN_ROLE |
| SLASH | `/모각코 채널 해제` | 모각코 집계 대상 음성채널 제거 | **Options:**<br>- `음성채널` (VoiceChannel, required) | 제거 완료 메시지 | ADMIN_ROLE |
| SLASH | `/모각코 채널 목록` | 현재 등록된 모각코 채널 목록 조회 | 없음 | 채널 목록 출력 | ADMIN_ROLE |

---

## MogakcoCommand

### 기간(period) 정의
- `week`: 이번 주 월요일 00:00 ~ 다음 주 월요일 00:00 (Asia/Seoul)
- `month`: 이번 달 1일 00:00 ~ 다음 달 1일 00:00 (Asia/Seoul)

### 참여율 정의
- 하루 누적 모각코 시간이 `guild_config.mogakco_active_minutes` 이상이면 **참여일(Active Day)** 1일로 인정
- 참여율 = 참여일수 / 기간일수

### 통계 집계 규칙
- 집계 대상은 기간과 겹치는 `voice_sessions`이며, 세션은 겹치는 구간만 잘라서 합산
- 참여일 계산 시 세션을 `Asia/Seoul` 날짜 경계로 분할해 일자별 누적시간을 계산

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/모각코 랭킹` | 기간별 누적시간 TOP N 랭킹 조회 | **Options:**<br>- `기간` (Enum, required): week/month<br>- `인원` (Int, optional, default: 10) | 순위(멘션) + 누적시간(HH:MM), 데이터 없으면 "기록이 없습니다." | ANY |
| SLASH | `/모각코 내정보` | 내 기간별 누적시간/참여일/참여율 조회 | **Options:**<br>- `기간` (Enum, required): week/month | 내 통계 출력(권장: ephemeral) | ANY |

---

## AssignmentCommand

### 과제 입력 정책
- `remind_at`은 과거 시각을 허용하지 않는다. (`remind_at <= nowUtc` 입력은 거부)
- `due_at`은 현재 시각보다 미래여야 하며, `remind_at`보다 같거나 늦어야 한다.
- 과제 확인 링크(`verify_url`)는 `http/https`만 허용한다.
- 임박 알림 시간(`임박알림`)은 쉼표 구분 정수(`24,3,1`) 형식으로 입력한다. 비우면 기본값 `24,3,1`을 사용한다.
- 종료 메시지(`종료메시지`)는 500자 이하여야 한다.

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/과제 등록` | 과제 등록 | **Options:**<br>- `제목` (String, required)<br>- `링크` (String, required, http/https)<br>- `알림시각` (String, required, KST)<br>- `마감시각` (String, required, KST)<br>- `채널` (TextChannel, optional)<br>- `알림역할` (Role, optional)<br>- `임박알림` (String, optional, 예: 24,3,1)<br>- `종료메시지` (String, optional) | 등록 결과(과제ID/알림·마감시각/역할) 출력 | ADMIN_ROLE |
| SLASH | `/과제 목록` | 과제 목록 조회 | **Options:**<br>- `상태` (optional: `대기/완료/종료` 또는 `PENDING/DONE/CLOSED`) | 과제 목록 출력 | ANY |
| SLASH | `/과제 상세` | 과제 상세 조회 | **Options:**<br>- `과제아이디` (Long, required) | 과제 상세 출력(ephemeral 권장) | ANY |
| SLASH | `/과제 완료` | 과제 완료 처리 | **Options:**<br>- `과제아이디` (Long, required) | 완료 처리 결과 출력 | ADMIN_ROLE |
| SLASH | `/과제 삭제` | 과제 삭제(또는 취소) 처리 | **Options:**<br>- `과제아이디` (Long, required) | 삭제 처리 결과 출력 | ADMIN_ROLE |

### 과제 목록 노출 정책
- `/과제 삭제`는 물리 삭제가 아닌 `CANCELED` 상태 전환이다.
- `CANCELED` 과제는 `/과제 목록`에서 기본적으로 노출하지 않는다.
- 상태 필터로도 `취소/CANCELED` 조회는 허용하지 않는다.

### 과제 빠른 등록 (V2, `FEATURE_TASK_QUICKREGISTER_V2=true`)
- 1단계 모달 입력(최소화)
  - `제목` (required)
  - `링크` (optional)
  - `마감일` (optional, 미입력 시 내일 23:59 KST)
  - `알림 시간/옵션` (optional, 절대시각/상대시간/`24,3,1` 형식)
- 2단계 선택 UI (ephemeral)
  - 채널 선택(EntitySelect)
  - 역할 선택(EntitySelect, optional)
  - 멘션 여부(StringSelect)
  - 미리보기 임베드 + `[등록 확정] [취소]`
- 기본값 정책
  - 길드 기본값: `guild_config.default_task_channel_id`, `default_notify_role_id`
  - 사용자 최근값: `guild_user_task_preferences`
- 확정 처리
  - 선택 채널 접근/전송 불가 시 재선택 유도
  - 역할 멘션 불가 시 멘션 없이 등록(degrade)
  - 성공 시 과제 생성 후 목록 진입 경로 안내

---

## MeetingVoiceSummaryCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/회의음성 시작` | 회의 음성요약 시작 요청 (Skeleton) | **Options:**<br>- `회의아이디` (Long, required: 회의 세션 ID 또는 스레드 ID)<br>- `보이스채널` (Voice/Stage, required) | enabled=false 기본값에서는 비활성 안내(ephemeral). enabled=true일 때만 job 생성/상태전이 수행 | ADMIN_ROLE |
| SLASH | `/회의음성 종료` | 회의 음성요약 종료 요청 (Skeleton) | **Options:**<br>- `회의아이디` (Long, required: 회의 세션 ID 또는 스레드 ID) | enabled=false 기본값에서는 비활성 안내(ephemeral). enabled=true일 때만 상태전이 수행 | ADMIN_ROLE |
| SLASH | `/회의음성 상태` | 회의 음성요약 설정/상태 조회 | **Options:**<br>- `회의아이디` (Long, required: 회의 세션 ID 또는 스레드 ID) | enabled=false 기본값에서는 비활성 안내(ephemeral). enabled=true일 때만 job 상태 조회 | ADMIN_ROLE |

### 비활성 정책(고정)
- `VOICE_SUMMARY_ENABLED=false`이면 실제 음성 연결을 수행하지 않는다.
- `start/stop/status` 모두 동일한 비활성 안내 메시지를 ephemeral로 응답한다.
- 비활성 상태에서는 `voice_summary_jobs` 생성 및 상태전이를 수행하지 않는다.
- 네이티브 의존성(ffmpeg/whisper/JDAVE)은 현재 빌드에 포함하지 않는다.

### 향후 활성화 TODO
- Java 25 전환
- JDA 6.3+ 및 Discord DAVE 검토/적용
- 음성 캡처/전사 파이프라인(ffmpeg/whisper 등) 운영 검증 후 활성화
- 현재 `enabled=true` 경로는 job 생성/상태전이(READY→RECORDING→RECORDED)까지만 skeleton으로 제공한다.

---

## AdminSettingCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/설정 운영진역할` | 운영진 역할 ID를 길드 설정에 저장 | **Options:**<br>- `대상역할` (Role, required) | 설정 완료 메시지 (ephemeral) | ADMIN_ROLE 또는 Manage Server/Administrator |
| SLASH | `/설정 운영진해제` | 운영진 역할 설정을 해제(`admin_role_id = null`) | 없음 | 해제 완료 메시지 (ephemeral) | ADMIN_ROLE 또는 Manage Server/Administrator |
| SLASH | `/설정 운영진조회` | 현재 운영진 역할 설정 조회 | 없음 | 현재 운영진 역할 정보 출력. 미설정 시 설정 가이드 안내(권장: ephemeral) | ANY |

---

## VoiceEventIngestion

모각코 시간 계산을 위해 음성 상태 변경 이벤트를 수집합니다.

### 이벤트 변환 규칙
- JDA 이벤트는 내부 입력 모델 `VoiceTransition(guildId, userId, oldChannelId, newChannelId, occurredAt)`로 변환 후 서비스에 전달

### 수집 대상
- 길드 내 `mogakco_channels`에 등록된 VoiceChannel에 대해서만 기록

### 저장 규칙(요약)
- 유저가 모각코 채널에 **입장**하면 `voice_sessions`에 세션을 생성(`joined_at`, `left_at=null`)
- 유저가 모각코 채널에서 **퇴장/이동**하면 열린 세션을 종료(`left_at`, `duration_sec`)
- 유저가 채널 **이동**한 경우, 이전 채널이 모각코면 종료하고 새 채널이 모각코면 새 세션을 생성
- 동일 유저에 `left_at=null` 열린 세션이 이미 있으면, 새 세션 생성 전 기존 세션을 현재 시각으로 종료
- 앱 시작 시 `left_at=null`인 세션은 **앱 시작 시각으로 종료 처리**하여 정합성을 보장

---

## 데이터 스키마

### guild_config
- `guild_id` (PK)
- `timezone` (VARCHAR(64), 기본: Asia/Seoul)
- `admin_role_id` (BIGINT, nullable)
- `mogakco_active_minutes` (기본: 30)
- `dashboard_channel_id` (BIGINT, nullable)
- `dashboard_message_id` (BIGINT, nullable)
- `default_task_channel_id` (BIGINT, nullable)
- `default_notify_role_id` (BIGINT, nullable)

### agenda_links
- `id` (PK)
- `guild_id`
- `date_local` (DATE, Asia/Seoul 기준 “오늘”)
- `title` (nullable)
- `url`
- `created_by`
- `created_at`
- `updated_at`
- UNIQUE(`guild_id`, `date_local`)
- INDEX(`guild_id`, `date_local`)

### mogakco_channels
- PK(`guild_id`, `channel_id`)

### voice_sessions
- `id` (PK)
- `guild_id`
- `user_id`
- `channel_id`
- `joined_at`
- `left_at` (nullable)
- `duration_sec` (nullable)
- INDEX(`guild_id`, `joined_at`)
- INDEX(`guild_id`, `user_id`, `joined_at`)

### assignment_tasks
- `id` (BIGSERIAL PK)
- `guild_id` (BIGINT NOT NULL, FK -> `guild_config.guild_id`)
- `channel_id` (BIGINT NOT NULL)
- `title` (VARCHAR(200) NOT NULL)
- `verify_url` (TEXT NOT NULL)
- `remind_at` (TIMESTAMPTZ NOT NULL, UTC 저장)
- `due_at` (TIMESTAMPTZ NOT NULL, UTC 저장)
- `notify_role_id` (BIGINT NULL)
- `pre_remind_hours_json` (TEXT NULL, 예: `[24,3,1]`)
- `pre_notified_json` (TEXT NULL, 이미 발송한 임박알림 시간 기록)
- `closing_message` (TEXT NULL)
- `closed_at` (TIMESTAMPTZ NULL)
- `status` (VARCHAR(16) NOT NULL, `PENDING`/`DONE`/`CANCELED`/`CLOSED`)
- `created_by` (BIGINT NOT NULL)
- `created_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `notified_at` (TIMESTAMPTZ NULL)
- CHECK(`status IN ('PENDING','DONE','CANCELED','CLOSED')`)
- CHECK(`BTRIM(title) <> ''`)
- CHECK(`BTRIM(verify_url) <> ''`)
- INDEX(`status`, `remind_at`, `notified_at`)
- INDEX(`guild_id`, `status`, `remind_at`)
- INDEX(`status`, `due_at`, `closed_at`)

### meeting_sessions
- `id` (BIGSERIAL PK)
- `guild_id` (BIGINT NOT NULL, FK -> `guild_config.guild_id`)
- `thread_id` (BIGINT NOT NULL, UNIQUE)
- `agenda_link_id` (BIGINT NULL, FK -> `agenda_links.id`)
- `status` (VARCHAR(16) NOT NULL, `ACTIVE`/`ENDED`)
- `started_by` (BIGINT NOT NULL)
- `started_at` (TIMESTAMPTZ NOT NULL)
- `ended_by` (BIGINT NULL)
- `ended_at` (TIMESTAMPTZ NULL)
- `summary_message_id` (BIGINT NULL)
- `created_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- CHECK(`status IN ('ACTIVE','ENDED')`)
- INDEX(`guild_id`, `status`, `started_at DESC`)
- UNIQUE PARTIAL INDEX(`guild_id`) WHERE `status='ACTIVE'`

### meeting_summary_artifacts
- `id` (BIGSERIAL PK)
- `meeting_session_id` (BIGINT NOT NULL, FK -> `meeting_sessions.id`, ON DELETE CASCADE)
- `guild_id` (BIGINT NOT NULL)
- `thread_id` (BIGINT NOT NULL)
- `summary_message_id` (BIGINT NULL)
- `message_count` (INT NOT NULL)
- `participant_count` (INT NOT NULL)
- `decision_count` (INT NOT NULL)
- `action_count` (INT NOT NULL)
- `todo_count` (INT NOT NULL)
- `generated_at` (TIMESTAMPTZ NOT NULL)
- `version` (VARCHAR(16) NOT NULL)
- `source_window_start` (TIMESTAMPTZ NOT NULL)
- `source_window_end` (TIMESTAMPTZ NOT NULL)
- `source_buffer_seconds` (INT NOT NULL)
- `decisions_text` (TEXT NULL)
- `actions_text` (TEXT NULL)
- `todos_text` (TEXT NULL)
- `created_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- INDEX(`meeting_session_id`, `generated_at DESC`)
- INDEX(`guild_id`, `thread_id`, `generated_at DESC`)

### meeting_structured_items
- `id` (BIGSERIAL PK)
- `meeting_session_id` (BIGINT NOT NULL, FK -> `meeting_sessions.id`, ON DELETE CASCADE)
- `guild_id` (BIGINT NOT NULL)
- `thread_id` (BIGINT NOT NULL)
- `item_type` (VARCHAR(16) NOT NULL, `DECISION`/`ACTION`)
- `content` (TEXT NOT NULL)
- `assignee_user_id` (BIGINT NULL)
- `due_date_local` (DATE NULL)
- `source` (VARCHAR(16) NOT NULL, 기본 `SLASH`)
- `source_message_id` (BIGINT NULL)
- `created_by` (BIGINT NOT NULL)
- `created_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- CHECK(`item_type IN ('DECISION','ACTION')`)
- INDEX(`meeting_session_id`, `created_at ASC`)
- INDEX(`guild_id`, `thread_id`, `created_at ASC`)

### guild_user_task_preferences
- PK(`guild_id`, `user_id`)
- `guild_id` (BIGINT NOT NULL, FK -> `guild_config.guild_id`, ON DELETE CASCADE)
- `user_id` (BIGINT NOT NULL)
- `last_task_channel_id` (BIGINT NULL)
- `last_notify_role_id` (BIGINT NULL)
- `last_mention_enabled` (BOOLEAN NOT NULL DEFAULT TRUE)
- `created_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- INDEX(`guild_id`, `updated_at DESC`)

### voice_summary_jobs
- `id` (BIGSERIAL PK)
- `guild_id` (BIGINT NOT NULL)
- `meeting_thread_id` (BIGINT NULL)
- `voice_channel_id` (BIGINT NOT NULL)
- `status` (VARCHAR(24) NOT NULL, `DISABLED`/`READY`/`RECORDING`/`RECORDED`/`TRANSCRIBING`/`DONE`/`FAILED`)
- `data_dir` (TEXT NOT NULL)
- `max_minutes` (INT NOT NULL DEFAULT 120)
- `started_at` (TIMESTAMPTZ NULL)
- `ended_at` (TIMESTAMPTZ NULL)
- `created_by` (BIGINT NOT NULL)
- `created_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `last_error` (TEXT NULL)
- CHECK(`status IN ('DISABLED','READY','RECORDING','RECORDED','TRANSCRIBING','DONE','FAILED')`)
- INDEX(`guild_id`, `status`)
- INDEX(`status`, `updated_at DESC`)

### 과제 스케줄러 조회 규칙
- `SELECT ... FOR UPDATE SKIP LOCKED` 기반으로 단계별 후보를 잠금 조회한다.
- 처리 단계:
  - 초기 알림: `notified_at IS NULL` AND `remind_at <= nowUtc`
  - 임박 알림: `due_at > nowUtc` AND `due_at <= nowUtc + pre-scan-hours` AND `pre_remind_hours_json IS NOT NULL`
  - 마감 알림: `closed_at IS NULL` AND `due_at <= nowUtc`
- `fixedDelay` 폴링으로 1건씩 잠금/전송/갱신을 반복 처리한다.
- 기본값:
  - `poll-delay-ms = 30000` (30초)
  - `grace-hours = 24` (최근 24시간 누락 알림 지연 발송)
  - `max-per-tick = 20` (tick당 최대 20건)
  - `pre-scan-hours = 24` (임박 알림 후보 조회 범위)

### 과제 알림 실패 정책
- 전송 성공 시에만 `notified_at`을 갱신한다.
- 임박 알림 성공 시 `pre_notified_json`에 발송 시간(`hoursBeforeDue`)을 기록한다.
- 마감 알림 성공 시 `status = CLOSED`, `closed_at`을 갱신한다.
- 일시 실패(retryable): 상태 갱신 없이 다음 tick에서 재시도한다.
- 비복구 실패(non-retryable, 예: 채널 없음/권한 없음): 로그를 남기고 `status = CANCELED`로 전환한다.

---

## 에러 응답 형식

Discord 응답에는 HTTP status가 없으므로, 아래 정보를 메시지(임베드/텍스트)로 통일합니다.

- `code` (String): 에러 코드
- `message` (String): 사용자 안내 메시지
- `retryable` (Boolean, optional): 재시도 권장 여부

### 예시
```json
{
  "code": "ACCESS_DENIED",
  "message": "이 명령은 운영진만 사용할 수 있습니다.",
  "retryable": false
}
```

### 주요 에러 코드(예시)

- `COMMON_INVALID_INPUT`: 입력값 오류(url 형식 등)
- `ACCESS_DENIED`: 권한 부족
- `RESOURCE_NOT_FOUND`: 오늘 안건 링크 없음 등
- `INTERNAL_ERROR`: 처리 중 예외 발생(로그 확인 필요)
