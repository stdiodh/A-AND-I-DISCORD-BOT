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
- `HomeDashboard`: 홈 상태판 설치 및 버튼/셀렉트 진입점
- `Agenda`: 오늘 회의 안건(구글 문서 링크) 설정/조회
- `Meeting`: 회의 시작/종료/기록/항목 및 스레드 요약
- `Mogakco`: 모각코 통계 조회(오늘/내정보/랭킹)
- `Assignment`: 과제 등록/조회/완료 및 알림
- `MeetingVoiceSummary`: 회의 음성요약 스켈레톤 제어(기본 비활성)
- `AdminSetting`: 설정 마법사/상태
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
- `MEETING_OPENER_ROLE`: 길드 설정(`guild_config.meeting_opener_role_id`)에 등록된 역할을 가진 사용자
- `ANY`: 누구나 사용 가능
- **과제 권한 정책(적용)**:
  - 과제 등록/완료: `ADMIN_ROLE` 또는 (admin_role_id 미설정 시) Manage Server/Administrator
  - 과제 목록/상세: `ANY`
- **설정 권한 정책(적용)**:
  - `/설정 마법사`: `ADMIN_ROLE` 또는 Manage Server/Administrator 허용
  - `/설정 상태`: `ANY` (권장: ephemeral 응답)
  - 레거시 세부 설정 명령은 내부 호환 목적으로 유지할 수 있으나, 공개 표면에서는 숨김을 기본으로 한다.

### 응답 정책
- 민감/설정 변경: 기본 `ephemeral`
- 랭킹: 공개 채널 출력(선호) 또는 서버 정책에 따라 ephemeral

### 기능 플래그
- `FEATURE_HOME_V2` (default: false)
  - 홈 대시보드 V2 UI(3개 주요 버튼 + 더보기 셀렉트) 활성화
- `FEATURE_MEETING_SUMMARY_V2` (default: true)
  - 회의 요약 파이프라인 V2(수집 윈도우, 요약 산출물 저장, 재생성/수동보강 UI) 활성화
- `FEATURE_TASK_QUICKREGISTER_V2` (default: true)
  - 과제 빠른등록 V2(모달+2단계 선택) 활성화

### 명령어 표기 정책
- 기본 슬래시 커맨드는 한글 명칭을 사용한다. (`/핑`, `/안건`, `/모각코`, `/과제`, `/설정`)
- 전환 기간 호환을 위해 기존 영문 명령(`ping`, `agenda`, `mogakco`)도 라우팅에서 허용한다.

### 레거시 호환 정책
- 회의 구조화 입력은 `/결정`, `/액션`, `/투두`, `/회의 항목조회`, `/회의 항목취소`를 내부 어댑터로 병행 지원한다.
- 안건은 `/안건 생성` 입력을 `/안건 설정`으로 병행 처리한다.
- 과제/모각코/회의음성은 레거시 옵션명을 병행 허용한다.
- 신규 명령 우선 정책을 유지하며, 레거시 입력은 점진적으로 신규 명칭으로 통합한다.

### 공개 명령 표면(압축)
- `/홈 설치`
- `/회의 시작|종료|기록|항목`
- `/안건 설정|오늘|최근`
- `/과제 목록|상세|등록|완료`
- `/모각코 오늘|내정보|랭킹`
- `/설정 마법사|상태`

---

## SystemCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/핑` | 봇 동작 확인 | 없음 | `pong` 메시지 | ANY |
| SLASH | `/도움말` | 명령어 설명/레거시 매핑 안내 | **Options:**<br>- `카테고리` (String, optional: `전체/회의/안건/과제/모각코/설정/홈`) | 카테고리별 사용 예시와 주의사항(ephemeral) | ANY |

---

## HomeDashboardCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/홈 설치` | 홈 메시지를 보장 생성/복구하고 핀 상태를 점검 | **Options:**<br>- `채널` (TextChannel, optional, 기본: 현재 채널) | HomeMessageManager로 단일 홈 보장(재사용/복구/신규 생성) + 핀 시도 결과를 홈 임베드에 반영 | ADMIN_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |

### 홈 상태판 UI 계약
- HOME_V2(`FEATURE_HOME_V2=true`) 기준:
  - 임베드 필드
    - `오늘 상태`: `안건 유무 · 진행 중 회의 유무 · 미완료 과제 수 · 오늘 모각코 참여 인원`
    - `지금 해야 할 일`: 가장 가까운 마감 1건 + 진행 중 회의 상태 + 안건 설정 상태
    - 설정 미완료 상태에서는 `설정이 더 필요해요` 필드(예: 회의 채널, 과제 공지 채널)를 추가한다.
  - 홈 상태 3종
    - `normal`: `[회의 시작] [빠른 과제] [내 기록]`
    - `meeting-active`: `[진행 중 회의 열기] [빠른 과제] [내 기록]`
    - `setup-incomplete`: `[설정 시작] [내 기록] [도움말]`
  - 다음 행 셀렉트: `[더보기]`
    - 안건
    - 과제목록
    - 모각코
    - 설정(시작/상태)
    - 도움말
  - 마지막 행 버튼(안건 존재 시): `[오늘 안건 링크]`
  - `내 기록`은 ephemeral 응답으로 채널 스팸을 방지한다.
- HOME_V2 비활성 시 기존 버튼 구성을 유지한다.
- 목표: 홈은 메뉴판이 아니라 상태판이며, 실제 공개 실행은 기능별 전용 채널에서 수행한다.

### 홈 버튼 인터랙션
- `custom_id` 규칙:
  - 대시보드 고정 버튼: `dash:*`
  - 회의 관련 모달/동작: `meeting:*`
  - 과제 관련 모달/동작: `assign:*`
  - 모각코 셀렉트: `mogakco:*`
  - 홈 더보기 셀렉트: `home:more:menu`
  - 홈 설정 CTA 버튼: `home:setup:start*`
  - 홈 빠른 도움말 버튼: `home:quick:help`
- 이벤트 라우팅은 prefix 기반 `InteractionRouter`가 처리한다.

### 홈 채널 가드 규칙
- 홈 채널(`guild_config.dashboard_channel_id`)에서는 기능 실행 명령을 제한한다.
- 대상 명령:
  - `/안건 설정|오늘|최근`
  - `/회의 시작`, `/회의 기록`, `/회의 항목` (레거시: `/결정`, `/액션`, `/투두`, `/회의 항목조회`, `/회의 항목취소`)
  - `/모각코 랭킹`, `/모각코 내정보`, `/모각코 오늘`
  - `/과제` 하위 명령 전체
- 동작:
  1. 기능별 전용 채널이 설정되어 있으면 해당 채널 mention과 예시 명령을 안내한다.
  2. 기능별 전용 채널이 없으면 `/설정 마법사`로 설정을 유도한다.
  3. 홈 채널과 전용 채널이 동일하면 가드를 우회하고 실행을 허용한다.

### 홈 메시지 보장 규칙
- 길드별 홈 메시지는 단일 레퍼런스를 유지한다. (`guild_config.dashboard_channel_id`, `dashboard_message_id`)
- `ensureHomeMessage` 호출 시:
  1. 저장된 메시지가 존재하면 재사용
  2. 저장 레퍼런스 메시지가 없으면 최근 메시지에서 홈 마커 검색 후 복구
  3. 둘 다 실패 시 신규 생성 후 레퍼런스 저장
- 홈 갱신은 기존 메시지 `edit` 기반으로 수행한다.
- 홈 갱신 payload가 기존 메시지와 동일하면 `edit`를 생략(no-op skip)한다.

---

## AgendaCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/안건 설정` | 오늘(Asia/Seoul)의 안건 문서 링크를 등록/수정(Upsert) | **Options:**<br>- `링크` (String, required): http/https 링크<br>- `제목` (String, optional): 표시용 제목 | 등록 완료 메시지 + 링크 버튼 | ADMIN_ROLE |
| SLASH | `/안건 오늘` | 오늘(Asia/Seoul)의 안건 링크 조회 | 없음 | 등록된 링크 임베드/버튼 출력(없으면 안내 메시지) | ANY |
| SLASH | `/안건 최근` | 최근 N일 안건 링크 목록 조회 | **Options:**<br>- `일수` (Int, optional, default: 7) | 최근 링크 목록 출력 | ANY |

### 안건 레거시 호환
- `/안건 생성`은 `/안건 설정`과 동일하게 처리한다.

---

## MeetingCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/회의 시작` | 회의 시작 메시지 생성 후 스레드 생성 | **Options:**<br>- `채널` (TextChannel, optional, 미입력 시 설정 채널 또는 현재 채널) | 대상 채널에 회의 시작 임베드 게시 후 `YYYY-MM-DD 회의` 스레드 생성 | ADMIN_ROLE 또는 MEETING_OPENER_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |
| SLASH | `/회의 종료` | 회의 ID 기준으로 요약 생성 후 세션 종료/스레드 아카이브 | **Options:**<br>- `회의아이디` (Integer, required) | 지정 세션의 요약 임베드 게시 후 종료 임베드/아카이브 처리 | ADMIN_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |
| SLASH | `/회의 기록` | 진행 중 회의에 결정/액션/TODO 항목을 구조화 기록 | **Options:**<br>- `유형` (String, required: decision/action/todo)<br>- `내용` (String, required)<br>- `담당자` (User, optional, 유형=action)<br>- `기한` (String, optional, YYYY-MM-DD, 유형=action)<br>- `회의아이디` (Integer, optional, 스레드 밖에서는 필수) | 회의 세션/스레드/항목ID와 함께 저장 완료 메시지 | ANY |
| SLASH | `/회의 항목` | 구조화 항목 조회/취소 | **Options:**<br>- `동작` (String, required: list/cancel)<br>- `아이디` (Integer, optional, 동작=cancel 시 필수)<br>- `회의아이디` (Integer, optional, 스레드 밖에서는 필수) | 조회 목록 또는 취소 완료 메시지 | ANY |
| SLASH | `/결정` | (레거시 호환) 진행 중 회의에 결정 항목을 구조화 기록 | **Options:**<br>- `내용` (String, required)<br>- `회의아이디` (Integer, optional, 스레드 밖에서는 필수) | 회의 세션/스레드/항목ID와 함께 저장 완료 메시지 | ANY |
| SLASH | `/액션` | (레거시 호환) 진행 중 회의에 액션 항목을 구조화 기록 | **Options:**<br>- `내용` (String, required)<br>- `담당자` (User, optional)<br>- `기한` (String, optional, YYYY-MM-DD)<br>- `회의아이디` (Integer, optional, 스레드 밖에서는 필수) | 회의 세션/스레드/항목ID와 함께 저장 완료 메시지 | ANY |
| SLASH | `/투두` | (레거시 호환) 진행 중 회의에 TODO 항목을 구조화 기록 | **Options:**<br>- `내용` (String, required)<br>- `회의아이디` (Integer, optional, 스레드 밖에서는 필수) | 회의 세션/스레드/항목ID와 함께 저장 완료 메시지 | ANY |
| SLASH | `/회의 항목조회` | (레거시 호환) 진행 중 회의의 구조화 항목 목록 조회 | **Options:**<br>- `회의아이디` (Integer, optional, 스레드 밖에서는 필수) | 항목 ID 포함 목록(결정/액션/투두) | ANY |
| SLASH | `/회의 항목취소` | (레거시 호환) 구조화 항목을 ID 기준 취소(소프트 삭제) | **Options:**<br>- `아이디` (Integer, required)<br>- `회의아이디` (Integer, optional, 스레드 밖에서는 필수) | 취소 완료 메시지 + 대상 항목 요약 | ANY |

### 회의 시작 처리 규칙
- 동시성 정책: **채널별 ACTIVE 1개** (`guild_id + board_channel_id`)를 기본으로 한다.
- 서로 다른 채널에서는 동시 회의 시작을 허용한다.
- `/회의 시작`은 지정 채널에 "회의 시작" 임베드를 먼저 게시하고, 그 메시지에서 스레드를 생성한다.
- 홈 버튼(`dash/home`)에서 시작할 때는 `meeting_board_channel_id`를 우선 사용한다.
- 새 세션 생성 시 `meeting_sessions.board_channel_id`에 시작 대상 채널 ID를 저장한다.
- 회의 세션 생성 시점에 오늘 안건이 존재하면 `meeting_sessions.agenda_link_id`로 연결 저장한다.
- 안건 등록/조회는 `/안건 설정|오늘|최근`을 기본으로 사용하고, `/회의 안건등록|안건조회|안건최근`은 내부 호환 경로로 유지한다.
- 대시보드 버튼 `dash:meeting_start`는 동일한 회의 시작 로직을 호출한다.
- `dash:meeting_start` 경로는 대시보드 채널(설정 시) 또는 현재 채널을 사용한다.
- 오늘 안건이 있으면 스레드 첫 메시지에 링크 버튼(`오늘 안건 링크`)을 포함한다.
- 회의 스레드 생성 시 템플릿 메시지를 함께 게시한다.
- 스레드 템플릿 메시지에는 액션 패널을 포함한다.
  - `[결정 추가] [액션 추가] [할 일 추가] [회의 종료]`

### 회의 종료 요약 규칙
- `/회의 종료`는 `회의아이디`를 반드시 요구한다. 컨텍스트 추론으로 세션을 자동 종료하지 않는다.
- 기본은 **스레드 텍스트 기반 요약**이며, `FEATURE_MEETING_SUMMARY_V2` 플래그로 V2 동작을 제어한다.
- V2 활성 시:
  - 수집 범위: `meetingStart ~ meetingEnd + buffer(기본 3초)` 윈도우
  - 메시지 수집은 배치/페이지 기반으로 수행하고, 결과 메시지 수(`messageCount`)를 기록한다.
  - 구조화 항목(`/결정`, `/액션`, `/투두`)과 추출 항목을 정규화 후 병합/중복제거한다.
  - 요약 임베드에 통계(`결정/액션/TODO/참여자`) 및 원문 수집 범위를 포함한다.
  - 요약 액션 버튼:
    - `요약 재생성`
    - `결정 추가`
    - `액션 추가`
    - `할 일 추가`
    - `과제로 전환`
    - `원문 보기/메시지 수 N개`
  - 요약 결과는 가능하면 기존 요약 메시지를 `edit`하여 중복 게시를 방지한다.
  - 요약 산출물은 `meeting_summary_artifacts`에 버전/수집범위/카운트와 함께 저장한다.
  - `/회의 종료` 이후의 `요약 재생성`은 최신 산출물의 체크포인트(`source_last_message_id`)가 있으면 증분 수집을 우선 사용한다.
- V2 비활성 시 기존 MVP0(패턴 기반 추출) 동작을 유지한다.
- 종료 시 `meeting_sessions` 상태를 `ACTIVE -> ENDED`로 전이하고, 종료 임베드를 게시한 뒤 대상 스레드를 아카이브한다.
- 구조화 항목(`/회의 기록`, `/회의 항목`)은 스레드 내 실행 시 스레드-세션 매핑을 우선 사용하고, 스레드 밖 실행 시 `회의아이디` 입력을 요구한다.
- 레거시 호환(`/결정`, `/액션`, `/투두`, `/회의 항목조회`, `/회의 항목취소`)도 동일한 매핑/검증 규칙을 적용한다.

### 모각코 채널 설정
- 공개 표면에서는 `/모각코 채널 ...` 명령을 노출하지 않는다.
- 채널 설정은 `/설정 마법사` 경로를 기본으로 사용한다.
  - 공지 채널: `모각코채널:#모각코`
  - 집계 채널 추가: `모각코음성추가:#모각코-1`
  - 집계 채널 해제: `모각코음성해제:#모각코-1`
- 레거시 채널 명령은 내부 호환 목적으로만 유지할 수 있다.

---

## MogakcoCommand

### 기간(period) 정의
- `day`: 오늘 00:00 ~ 내일 00:00 (Asia/Seoul), 측정은 현재 시각까지
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
| SLASH | `/모각코 랭킹` | 기간별 누적시간 TOP N 랭킹 조회 | **Options:**<br>- `기간` (Enum, required): day/week/month<br>- `인원` (Int, optional, default: 10) | 순위(멘션) + 누적시간(HH:MM), 데이터 없으면 "기록이 없습니다." | ANY |
| SLASH | `/모각코 내정보` | 내 기간별 누적시간/참여일/참여율 조회 | **Options:**<br>- `기간` (Enum, required): day/week/month | 내 통계 출력(권장: ephemeral) | ANY |
| SLASH | `/모각코 오늘` | 오늘 모각코 출석/1시간 목표 진행률 빠른 조회 | 없음 | 오늘 누적시간, 출석 기준 잔여시간, 1시간 목표 잔여시간 출력(ephemeral 권장) | ANY |

### 모각코 공지 채널 규칙
- `mogakco_board_channel_id`가 설정된 경우, `/모각코 랭킹` 결과는 해당 채널에 게시한다.
- 실행 채널에는 게시 완료 안내(ephemeral)를 반환한다.

---

## AssignmentCommand

### 과제 입력 정책
- `remind_at`은 과거 시각을 허용하지 않는다. (`remind_at <= nowUtc` 입력은 거부)
- `due_at`은 현재 시각보다 미래여야 하며, `remind_at`보다 같거나 늦어야 한다.
- 과제 확인 링크(`verify_url`)는 optional이다.
- 링크를 입력한 경우에만 `http/https`를 허용한다.
- `채널` 미입력 시 `default_task_channel_id`를 우선 사용하고, 없으면 명령 실행 채널을 사용한다.
- 빠른 등록(V2)에서는 알림 시각/임박 알림을 입력받지 않고 기본값으로 자동 채운다.
  - 마감일 미입력: `내일 23:59 KST`
  - 기본 알림 시각: `마감 24시간 전` (불가능하면 `현재+5분`으로 보정)
  - 임박 알림 프리셋: `24,3,1`
- 마감 메시지(`마감메시지`)는 500자 이하여야 한다.

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/과제 등록` | 과제 등록(V2 기본, 레거시 옵션 호환) | **기본 동작:** 옵션 없이 실행 시 V2 모달 시작<br>**레거시 Options(호환):**<br>- `제목` (String, optional)<br>- `링크` (String, optional, http/https)<br>- `알림/알림시각` (String, legacy 즉시등록 시 필수, KST)<br>- `마감/마감시각` (String, legacy 즉시등록 시 필수, KST)<br>- `채널` (TextChannel, optional)<br>- `역할/알림역할` (Role, optional)<br>- `임박알림옵션` (String, optional: 프리셋 선택)<br>- `임박알림` (String, optional, 예: 24,3,1)<br>- `마감메시지/종료메시지` (String, optional) | 등록 결과(과제ID/알림·마감/역할) 출력 | ADMIN_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |
| SLASH | `/과제 목록` | 과제 목록 조회 | **Options:**<br>- `상태` (optional: `대기/완료/종료` 또는 `PENDING/DONE/CLOSED`) | 과제 목록 출력 | ANY |
| SLASH | `/과제 상세` | 과제 상세 조회 | **Options:**<br>- `아이디/과제아이디` (Long, 둘 중 하나 필수) | 과제 상세 출력(ephemeral 권장) | ANY |
| SLASH | `/과제 완료` | 과제 완료 처리 | **Options:**<br>- `아이디/과제아이디` (Long, 둘 중 하나 필수) | 완료 처리 결과 출력 | ADMIN_ROLE 또는 (admin_role_id 미설정 시) Manage Server/Administrator |

### 과제 목록 노출 정책
- 레거시 `/과제 삭제`는 물리 삭제가 아닌 `CANCELED` 상태 전환이다.
- `CANCELED` 과제는 `/과제 목록`에서 기본적으로 노출하지 않는다.
- 상태 필터로도 `취소/CANCELED` 조회는 허용하지 않는다.

### 과제 빠른 등록 (V2, 기본 활성)
- 1단계 모달 입력(3필드)
  - `제목` (required)
  - `마감일` (optional, 미입력 시 내일 23:59 KST)
  - `링크` (optional)
- 2단계 선택 UI (ephemeral)
  - 채널 선택(EntitySelect)
  - 역할 선택(EntitySelect, optional)
  - 멘션 여부(StringSelect)
  - 알림 프리셋(StringSelect: 기본값/24h/3h/1h/사용자설정)
- 3단계 미리보기
  - 미리보기 임베드 + `[등록 확정] [고급옵션] [취소]`
- 기본값 정책
  - 길드 기본값: `guild_config.default_task_channel_id`, `default_notify_role_id`
  - 사용자 최근값: `guild_user_task_preferences`
- 확정 처리
  - 선택 채널 접근/전송 불가 시 재선택 유도
  - 역할 멘션 불가 시 멘션 없이 등록(degrade)
  - 성공 시 과제 생성 후 CTA 버튼 제공: `[목록 보기] [상세 보기] [수정]`

### 과제 레거시 즉시 등록 (호환)
- `/과제 등록`에 레거시 옵션(`제목/알림/마감` 등)을 함께 입력하면 즉시 등록 경로를 사용한다.
- 단계적 전환을 위해 유지하며, 신규 권장 경로는 V2 모달 + 2단계 선택 UI이다.

---

## MeetingVoiceSummaryCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/회의음성 시작` | 회의 음성요약 시작 요청 (Skeleton) | **Options:**<br>- `아이디/회의아이디` (Long, 둘 중 하나 필수: 회의 세션 ID 또는 스레드 ID)<br>- `채널/보이스채널` (Voice/Stage, 둘 중 하나 필수) | enabled=false 기본값에서는 비활성 안내(ephemeral). enabled=true일 때만 job 생성/상태전이 수행 | ADMIN_ROLE |
| SLASH | `/회의음성 종료` | 회의 음성요약 종료 요청 (Skeleton) | **Options:**<br>- `아이디/회의아이디` (Long, 둘 중 하나 필수: 회의 세션 ID 또는 스레드 ID) | enabled=false 기본값에서는 비활성 안내(ephemeral). enabled=true일 때만 상태전이 수행 | ADMIN_ROLE |
| SLASH | `/회의음성 상태` | 회의 음성요약 설정/상태 조회 | **Options:**<br>- `아이디/회의아이디` (Long, 둘 중 하나 필수: 회의 세션 ID 또는 스레드 ID) | enabled=false 기본값에서는 비활성 안내(ephemeral). enabled=true일 때만 job 상태 조회 | ADMIN_ROLE |

### 비활성 정책(고정)
- `VOICE_SUMMARY_ENABLED=false`이면 `/회의음성` 명령은 기본 등록 목록에서 숨긴다.
- `VOICE_SUMMARY_ENABLED=false`이면 실제 음성 연결을 수행하지 않는다.
- (레거시 등록이 남아있는 경우) `start/stop/status` 모두 동일한 비활성 안내 메시지를 ephemeral로 응답한다.
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
| SLASH | `/설정 마법사` | 핵심 채널/역할 설정을 한 번에 적용 | **Options:**<br>- `운영진역할` (Role, optional)<br>- `회의열기역할` (Role, optional)<br>- `홈채널` (Text/News, optional)<br>- `회의채널` (Text/News, optional)<br>- `모각코채널` (Text/News, optional)<br>- `모각코음성추가` (Voice/Stage, optional)<br>- `모각코음성해제` (Voice/Stage, optional)<br>- `과제공지채널` (Text/News, optional)<br>- `과제알림역할` (Role, optional) | 적용 결과 + 현재 상태 요약(ephemeral) | ADMIN_ROLE 또는 Manage Server/Administrator |
| SLASH | `/설정 상태` | 길드 설정 상태 조회(운영진/회의열기/채널/과제알림역할/홈채널) | 없음 | 현재 설정 현황 출력(ephemeral 권장) | ANY |

### 설정 마법사 단계
- 1) 권한 설정: 운영진 역할, 회의 시작 역할
- 2) 채널 설정: 홈/회의/모각코 공지/과제 공지 채널, 모각코 집계 음성채널 추가·해제
- 3) 과제 기본값: 기본 알림 역할
- 4) 상태 확인: `/설정 상태`로 최종 확인

### 설정 레거시 호환
- 세부 설정 명령(`/설정 운영진역할`, `/설정 회의채널`, `/설정 과제알림역할` 등)은 내부 호환 목적으로만 유지할 수 있다.
- 공개 표면에서는 `/설정 마법사`, `/설정 상태`를 기본 진입점으로 사용한다.

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
- 세션 종료 시 일자별 누적시간을 `voice_session_daily_rollups`에 upsert 반영한다. (`guild_id`, `user_id`, `date_local`)
- 유저가 채널 **이동**한 경우, 이전 채널이 모각코면 종료하고 새 채널이 모각코면 새 세션을 생성
- 동일 유저에 `left_at=null` 열린 세션이 이미 있으면, 새 세션 생성 전 기존 세션을 현재 시각으로 종료
- 앱 시작 시 `left_at=null`인 세션은 **앱 시작 시각으로 종료 처리**하여 정합성을 보장

---

## 데이터 스키마

### guild_config
- `guild_id` (PK)
- `timezone` (VARCHAR(64), 기본: Asia/Seoul)
- `admin_role_id` (BIGINT, nullable)
- `meeting_opener_role_id` (BIGINT, nullable)
- `mogakco_active_minutes` (기본: 30)
- `dashboard_channel_id` (BIGINT, nullable)
- `dashboard_message_id` (BIGINT, nullable)
- `meeting_board_channel_id` (BIGINT, nullable)
- `mogakco_board_channel_id` (BIGINT, nullable)
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
- `verify_url` (TEXT NULL, optional)
- `remind_at` (TIMESTAMPTZ NOT NULL, UTC 저장)
- `due_at` (TIMESTAMPTZ NOT NULL, UTC 저장)
- `notify_role_id` (BIGINT NULL)
- `pre_remind_hours_json` (TEXT NULL, 예: `[24,3,1]`)
- `pre_notified_json` (TEXT NULL, 이미 발송한 임박알림 시간 기록)
- `closing_message` (TEXT NULL)
- `closed_at` (TIMESTAMPTZ NULL)
- `next_fire_at` (TIMESTAMPTZ NULL, 스케줄러 다음 처리 시각)
- `status` (VARCHAR(16) NOT NULL, `PENDING`/`DONE`/`CANCELED`/`CLOSED`)
- `created_by` (BIGINT NOT NULL)
- `created_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `notified_at` (TIMESTAMPTZ NULL)
- CHECK(`status IN ('PENDING','DONE','CANCELED','CLOSED')`)
- CHECK(`BTRIM(title) <> ''`)
- INDEX(`status`, `remind_at`, `notified_at`)
- INDEX(`guild_id`, `status`, `remind_at`)
- INDEX(`status`, `due_at`, `closed_at`)
- INDEX(`status`, `next_fire_at`)

### meeting_sessions
- `id` (BIGSERIAL PK)
- `guild_id` (BIGINT NOT NULL, FK -> `guild_config.guild_id`)
- `thread_id` (BIGINT NOT NULL, UNIQUE)
- `board_channel_id` (BIGINT NULL)
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
- UNIQUE PARTIAL INDEX(`guild_id`, `board_channel_id`) WHERE `status='ACTIVE' AND board_channel_id IS NOT NULL`
- INDEX(`guild_id`, `board_channel_id`, `status`, `started_at DESC`)

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
- `source_last_message_id` (BIGINT NULL, 증분 수집 체크포인트)
- `participant_user_ids_text` (TEXT NULL, 참여자 ID CSV)
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
- `item_type` (VARCHAR(16) NOT NULL, `DECISION`/`ACTION`/`TODO`)
- `content` (TEXT NOT NULL)
- `assignee_user_id` (BIGINT NULL)
- `due_date_local` (DATE NULL)
- `source` (VARCHAR(16) NOT NULL, 기본 `SLASH`)
- `source_message_id` (BIGINT NULL)
- `created_by` (BIGINT NOT NULL)
- `canceled_by` (BIGINT NULL, 소프트 삭제 수행자)
- `canceled_at` (TIMESTAMPTZ NULL, 소프트 삭제 시각)
- `created_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- CHECK(`item_type IN ('DECISION','ACTION','TODO')`)
- INDEX(`meeting_session_id`, `created_at ASC`)
- INDEX(`guild_id`, `thread_id`, `created_at ASC`)
- PARTIAL INDEX(`meeting_session_id`, `created_at ASC`) WHERE `canceled_at IS NULL`

### voice_session_daily_rollups
- `id` (BIGSERIAL PK)
- `guild_id` (BIGINT NOT NULL)
- `user_id` (BIGINT NOT NULL)
- `date_local` (DATE NOT NULL, `app.timezone` 기준)
- `total_seconds` (BIGINT NOT NULL, 누적)
- `created_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT NOW())
- UNIQUE(`guild_id`, `user_id`, `date_local`)
- CHECK(`total_seconds >= 0`)
- INDEX(`guild_id`, `date_local`)
- 마이그레이션 시 기존 `voice_sessions`(종료된 세션) 데이터는 일자 단위로 백필한다.

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
- `SELECT ... FOR UPDATE SKIP LOCKED` 기반으로 `next_fire_at <= nowUtc`인 `PENDING` 과제를 잠금 조회한다.
- 잠금 후 `nextPendingAction`(초기 알림/임박 알림/마감)을 재계산한다.
- 액션이 없으면 `next_fire_at`만 미래 시각으로 재계산하고 종료한다.
- 액션 성공 시 상태 컬럼(`notified_at`, `pre_notified_json`, `status/closed_at`)과 `next_fire_at`를 함께 갱신한다.
- `fixedDelay` 폴링으로 1건씩 잠금/전송/갱신을 반복 처리한다.
- 기본값:
  - `poll-delay-ms = 30000` (30초)
  - `grace-hours = 24` (최근 24시간 누락 알림 지연 발송)
  - `max-per-tick = 20` (tick당 최대 20건)

### 과제 알림 실패 정책
- 전송 성공 시에만 `notified_at`을 갱신한다.
- 임박 알림 성공 시 `pre_notified_json`에 발송 시간(`hoursBeforeDue`)을 기록한다.
- 마감 알림 성공 시 `status = CLOSED`, `closed_at`을 갱신한다.
- 상태 전이 후 `next_fire_at`를 다음 후보 시각으로 재계산한다.
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
