# A&I Discord Bot

Spring Boot + Kotlin + JDA 기반 디스코드 봇입니다.  
애플리케이션 시작 시 디스코드에 로그인하고, 길드 슬래시 커맨드를 등록합니다.  
최신 커맨드/권한/응답 계약은 `DOCS/API_SPECIFICATION.md`를 기준으로 관리합니다.

## Development Rules

- API 명세서: [DOCS/API_SPECIFICATION.md](DOCS/API_SPECIFICATION.md)
- 클린 코드 원칙: [DOCS/CLEAN_CODE_PRINCIPLES.md](DOCS/CLEAN_CODE_PRINCIPLES.md)
- 커밋 컨벤션: [DOCS/COMMIT_CONVENTION.md](DOCS/COMMIT_CONVENTION.md)
- PR 가이드: [DOCS/PR_GUIDE.md](DOCS/PR_GUIDE.md)
- 문서 인덱스: [DOCS/README.md](DOCS/README.md)
- 운영 런북: [DOCS/OPS_RUNBOOK.md](DOCS/OPS_RUNBOOK.md)
- 기여 가이드: [CONTRIBUTING.md](CONTRIBUTING.md)

## 로컬 실행

1. 환경변수 설정

```bash
cp .env.example .env
set -a
source .env
set +a
```

`docker-compose.yml` 기본값은 예시(`postgres/postgres`)입니다.  
운영/개인 환경에 맞게 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`를 변경하고,
아래 Spring 환경변수(`SPRING_DATASOURCE_*`)와 동일하게 맞춰주세요.

환경변수 연결 예시:

```bash
# docker-compose.yml (postgres 컨테이너)
POSTGRES_DB=ai_discord_bot
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Spring Boot 애플리케이션(DB 접속 정보)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ai_discord_bot
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

필수 환경변수:
- `DISCORD_TOKEN`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

선택 환경변수:
- `DISCORD_GUILD_ID` (있으면 길드 커맨드, 없으면 글로벌 커맨드 등록)

2. PostgreSQL 실행

```bash
docker compose up -d postgres
```

3. 애플리케이션 실행

```bash
./gradlew bootRun
```

## 품질 검증 명령

```bash
./gradlew check
./gradlew test
```

## Discord/JDA 구성 개요

- `DiscordBotConfig`
  - `DISCORD_TOKEN`/`DISCORD_GUILD_ID`로 JDA 생성 및 로그인
  - `GUILD_VOICE_STATES` intent 활성화
  - 시작 시 명령 스펙(`DiscordCommandSpec`) 기반으로 길드 커맨드 등록
- `AgendaSlashCommandHandler`
  - `/안건 생성 링크:<필수> 제목:<선택>`
  - `/안건 오늘`, `/안건 최근`
  - `guild_config.admin_role_id` 권한 체크 후 오늘(Asia/Seoul) 안건 링크 upsert
- `MogakcoSlashCommandHandler`
  - `/모각코 채널 등록 채널:<voice>`
  - `/모각코 채널 해제 채널:<voice>`
  - `/모각코 랭킹 기간:<week|month> [인원]`
  - `/모각코 내정보 기간:<week|month>`
- `HomeSlashCommandHandler`
  - `/홈 생성 채널:<text>`
  - `/홈 갱신`
  - `/홈 설치 [채널]`
- `MeetingSlashCommandHandler`
  - `/회의 시작 채널:<text>`, `/회의 종료`
  - `/결정 내용:<text>`, `/액션 내용:<text> [담당자] [기한]`, `/투두 내용:<text>`
  - `/회의 항목조회`, `/회의 항목취소 아이디:<id>`
- `AssignmentSlashCommandHandler`
  - `/과제 등록 제목/링크/알림/마감 ...`
  - `/과제 목록`, `/과제 상세 아이디:<id>`, `/과제 완료 아이디:<id>`, `/과제 삭제 아이디:<id>`
- `PingSlashCommandListener`
  - `/핑` 입력 시 `pong`을 ephemeral로 응답
- `VoiceStateIngestionListener`
  - 음성 채널 입장/퇴장 이벤트 수신 지점 (`GuildVoiceUpdateEvent`)
  - 모각코 등록 채널의 `voice_sessions`를 자동 기록

## 슬래시 커맨드 등록 예시

`updateCommands` 방식:

```kotlin
guild.updateCommands()
    .addCommands(Commands.slash("핑", "봇 동작 확인"))
    .queue()
```

`upsertCommand` 방식:

```kotlin
guild.upsertCommand("핑", "봇 동작 확인").queue()
```

## Agenda 명령 사용 전 준비

`/안건 생성`은 운영진 role 권한이 필요합니다.  
`guild_config`에 대상 길드의 `admin_role_id`가 있어야 합니다.

## Mogakco 집계 방식

- 모각코로 등록된 음성채널만 집계합니다.
- 입장 시 `voice_sessions.joined_at` 생성, 퇴장/이동 시 `left_at`, `duration_sec`를 저장합니다.
- 앱 시작 시 `left_at`이 `null`인 열린 세션은 시작 시각으로 자동 종료해 정합성을 맞춥니다.
- 참여일 기준: 하루 누적 시간이 `guild_config.mogakco_active_minutes` 이상이면 1일 참여로 계산합니다.

## Docker 배포 파일

- 로컬 개발용 DB: `docker-compose.yml` (`postgres:15`)
- 운영 이미지 빌드: `Dockerfile` (multi-stage, `bootJar`)
- EC2 운영 compose: `docker-compose.prod.yml` (`app + postgres`, `restart: always`)

EC2 실행 예시:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

RDS 사용 시:
- `docker-compose.prod.yml`의 `postgres` 서비스를 제거
- `app`의 `depends_on`에서 `postgres` 제거
- `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`를 RDS 값으로 설정

## 실행 체크리스트 (/핑, /안건, /모각코)

- [ ] Discord Developer Portal에서 봇 초대 링크 생성 시 scopes에 `bot`, `applications.commands` 포함
- [ ] `DISCORD_TOKEN` 환경변수 설정
- [ ] `/핑` 동작 확인
- [ ] `/안건 생성` -> `/안건 오늘` 동작 확인
- [ ] `/모각코 채널 등록`으로 모각코 채널 등록
- [ ] 해당 음성채널 입장/퇴장 후 `/모각코 내정보`, `/모각코 랭킹` 동작 확인

## Branch and CI/CD Flow

1. 기능 개발은 `develop` 기준으로 브랜치를 만들어 작업한다.
2. `develop -> main` PR 시 `.github/workflows/ci-pr.yml`에서 `./gradlew test`를 실행한다.
3. `main` 반영 후 릴리스 태그(`v1.0.0` 등)를 푸시한다.
4. 태그 푸시 시 `.github/workflows/release-deploy.yml`이 실행되어:
   - 태그 커밋이 `main`에 포함됐는지 확인
   - 테스트 실행
   - GHCR 이미지 빌드/푸시 (`ghcr.io/<owner>/<repo>:<tag>`)
   - EC2에 SSH 배포(`docker compose -f docker-compose.prod.yml pull/up`)

필수 GitHub Secrets:
- `EC2_HOST`
- `EC2_PORT` (선택, 기본 22)
- `EC2_USER`
- `EC2_SSH_KEY`
- `EC2_APP_DIR`
- `GHCR_USERNAME`
- `GHCR_TOKEN` (GHCR pull 권한)
