# Git Commit Convention (AngularJS Style)

본 프로젝트는 AngularJS 커밋 컨벤션을 엄격하게 준수합니다.

## 1. 커밋 메시지 구조

<type>(<scope>): <subject>

<body>

<footer>

## 2. Type (필수)

| Type | 설명 |
| :--- | :--- |
| **feat** | 새로운 기능 추가 |
| **fix** | 버그 수정 |
| **docs** | 문서 수정 (README.md, JavaDoc 등) |
| **style** | 코드 포맷팅, 세미콜론 누락 등 (로직 변경 없음) |
| **refactor** | 코드 리팩토링 (기능 변경 없음) |
| **test** | 테스트 코드 추가/수정 |
| **chore** | 빌드 설정, 패키지 매니저 설정 등 (프로덕션 코드 변경 없음) |

## 3. Subject (필수)

- 50자 이내로 간결하게 작성한다.
- 명령문으로 시작한다. (예: "수정한다" X -> "수정" O)
- 끝에 마침표(.)를 찍지 않는다.
- 영문인 경우 동사 원형으로 시작한다. (예: `feat: Add login function`)

## 4. Body (선택)

- **무엇을(What)**, **왜(Why)** 변경했는지 설명한다.
- 어떻게(How)는 코드에 드러나므로 생략 가능하다.

## 5. Footer (선택)

- **BREAKING CHANGE**: 호환성 문제가 발생하는 큰 변경 사항이 있을 때 기술한다.
- **ISSUES**: 관련된 이슈 번호를 참조한다. (예: `Closes #123`)

## 6. 커밋 단위(Atomic Commit Unit)

한 커밋은 하나의 논리적 변경만 포함한다.

- `docs`: 문서만 변경 (`DOCS/`, `README.md`, `CONTRIBUTING.md`, `.github/pull_request_template.md`)
- `chore(build)`: 빌드/런타임 설정 변경 (`build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradlew*`)
- `chore(ci)`: CI/CD 파이프라인 변경 (`.github/workflows/`)
- `chore(deploy)`: 컨테이너/배포 설정 변경 (`Dockerfile`, `docker-compose*.yml`)
- `feat(discord-core)`: 봇 초기화/공통 이벤트 인프라 변경
- `feat(agenda)`: 안건(agenda) 기능 변경
- `feat(mogakco)`: 모각코 채널/세션/통계 기능 변경
- `test`: 테스트 코드/테스트 설정 변경

커밋 분리 규칙:
- 기능 코드와 문서는 가능하면 분리 커밋한다.
- 스키마 변경(Flyway)과 해당 도메인 코드는 같은 기능 커밋으로 묶는다.
- 리팩터링과 기능 추가는 분리한다.
