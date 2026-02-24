# A&I Discord Bot API 명세서

이 문서는 A&I 통합 Discord Bot의 모든 인터랙션(슬래시 커맨드/이벤트)을 정리한 명세서입니다.
회의/안건은 Google Drive 문서로 관리하며, Bot은 "오늘 안건 링크 등록/조회"만 제공합니다.
모각코는 Discord 음성채널 접속 시간을 기반으로 누적시간/순위/참여율을 계산합니다.

## 목차
- [Command Category 기준](#command-category-기준)
- [공통 사항](#공통-사항)
- [SystemCommand](#systemcommand)
- [AgendaCommand](#agendacommand)
- [MogakcoConfigCommand](#mogakcoconfigcommand)
- [MogakcoCommand](#mogakcocommand)
- [VoiceEventIngestion](#voiceeventingestion)
- [데이터 스키마](#데이터-스키마)
- [에러 응답 형식](#에러-응답-형식)

---

## Command Category 기준

- `System`: 봇 상태/헬스체크
- `Agenda`: 오늘 회의 안건(구글 문서 링크) 등록/조회
- `Mogakco`: 모각코 통계 조회(누적/랭킹/참여율)
- `MogakcoConfig`: 모각코로 집계할 음성채널 등록/삭제
- `Ingestion`: 음성 이벤트 수집/세션 저장(슬래시 커맨드가 아닌 내부 이벤트)

---

## 공통 사항

### 시간/타임존
- DB 저장 시간: `TIMESTAMPTZ`(UTC)로 저장
- "오늘"의 기준: `Asia/Seoul` 로컬 날짜(`date_local`)
- 주간/월간 통계 범위 역시 `Asia/Seoul` 기준으로 계산

### 권한(Auth)
- `ADMIN_ROLE`: 길드 설정(`guild_config.admin_role_id`)에 등록된 역할을 가진 사용자
- `ANY`: 누구나 사용 가능
- **Fallback 정책(적용)**: admin_role_id가 설정되지 않은 경우, 서버 관리 권한(예: Manage Server/Administrator)을 가진 사용자만 ADMIN 커맨드를 실행할 수 있다.

### 응답 정책
- 민감/설정 변경: 기본 `ephemeral`
- 랭킹: 공개 채널 출력(선호) 또는 서버 정책에 따라 ephemeral

---

## SystemCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/ping` | 봇 동작 확인 | 없음 | `pong` 메시지 | ANY |

---

## AgendaCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/agenda set` | 오늘(Asia/Seoul)의 안건 문서 링크를 등록/수정(Upsert) | **Options:**<br>- `url` (String, required): http/https 링크<br>- `title` (String, optional): 표시용 제목 | 등록 완료 메시지 + 링크 버튼 | ADMIN_ROLE |
| SLASH | `/agenda today` | 오늘(Asia/Seoul)의 안건 링크 조회 | 없음 | 등록된 링크 임베드/버튼 출력(없으면 안내 메시지) | ANY |
| SLASH | `/agenda recent` | 최근 N일 안건 링크 목록 조회 | **Options:**<br>- `days` (Int, optional, default: 7) | 최근 링크 목록 출력 | ANY |

---

## MogakcoConfigCommand

| Method | URI | 기능 설명 | Request | Response | Auth |
|--------|-----|----------|---------|----------|------|
| SLASH | `/mogakco channel add` | 모각코로 집계할 음성채널을 등록 | **Options:**<br>- `channel` (VoiceChannel, required) | 등록 완료 메시지 (이미 등록된 경우 안내 후 종료) | ADMIN_ROLE |
| SLASH | `/mogakco channel remove` | 모각코 집계 대상 음성채널 제거 | **Options:**<br>- `channel` (VoiceChannel, required) | 제거 완료 메시지 | ADMIN_ROLE |
| SLASH | `/mogakco channel list` | 현재 등록된 모각코 채널 목록 조회 | 없음 | 채널 목록 출력 | ADMIN_ROLE |

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
| SLASH | `/mogakco leaderboard` | 기간별 누적시간 TOP N 랭킹 조회 | **Options:**<br>- `period` (Enum, required): week/month<br>- `top` (Int, optional, default: 10) | 순위(멘션) + 누적시간(HH:MM), 데이터 없으면 "기록이 없습니다." | ANY |
| SLASH | `/mogakco me` | 내 기간별 누적시간/참여일/참여율 조회 | **Options:**<br>- `period` (Enum, required): week/month | 내 통계 출력(권장: ephemeral) | ANY |

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
- `timezone` (기본: Asia/Seoul)
- `admin_role_id` (nullable)
- `mogakco_active_minutes` (기본: 30)

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
