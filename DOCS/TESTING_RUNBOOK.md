# Testing Runbook

## 테스트 목표
- 유틸 테스트: 시간 계산/포맷터 등 순수 함수 검증
- 도메인 테스트: 값 객체/도메인 규칙 검증
- 서비스 테스트: 비즈니스 로직과 분기 검증
- DB 슬라이스 테스트: JPA 매핑/쿼리 동작 검증
- 통합 테스트: 앱 구동 단위의 핵심 플로우 검증

## 테스트 계층 기준
- 기본 우선순위: 단위 테스트 > 슬라이스 테스트 > 통합 테스트
- 커맨드/핸들러는 서비스 로직을 중심으로 검증하고, 입출력 포맷은 최소 범위로 확인
- 외부 시스템(Discord/AWS/네트워크)은 테스트 더블로 대체

## Kotest 기반 표준
- 스펙 스타일: `DescribeSpec` 또는 `FunSpec`를 기본으로 사용
- 테스트 이름 규칙:
  - 한글 또는 영어로 의도를 명확히 표현
  - 입력/상황/기대결과가 한 줄에 드러나도록 작성
  - 예시: `기간 경계를 걸치는 세션은 겹치는 구간만 합산한다`
- 테스트 구조: Arrange-Act-Assert를 유지
  - Arrange: 입력 데이터/목 객체 준비
  - Act: 대상 함수 1회 호출
  - Assert: 결과/상태 검증

## Discord/JDA 테스트 원칙
- 테스트에서 Discord/JDA 실제 연결은 금지
- 반드시 `discord.enabled=false`로 비활성화
- `src/test/resources/application-test.yml`에 `discord.enabled=false`를 유지
- Spring 테스트는 `@ActiveProfiles("test")`로 test profile을 사용
- 네트워크 I/O는 테스트 더블로 대체하고, 실제 토큰 사용 테스트는 작성하지 않음

## DB 테스트 원칙
- 기본은 단위 테스트(mock repository)로 작성
- DB 동작 검증이 꼭 필요한 경우에만 슬라이스 테스트 또는 통합 테스트 추가
- 통합 DB 테스트가 필요하면 Testcontainers(PostgreSQL) 기반으로 작성

## 새 기능 추가 시 테스트 체크리스트
- [ ] 핵심 유스케이스 정상 경로 테스트가 있다
- [ ] 주요 예외/오류 입력 케이스 테스트가 있다
- [ ] 시간/기간 계산 로직은 경계값 테스트가 있다
- [ ] 권한 분기는 허용/거부 모두 검증한다
- [ ] DB 연동 변경 시 repository 또는 슬라이스 테스트가 있다
- [ ] Discord/JDA는 테스트에서 비활성화되어 있다

## Codex 프롬프트 템플릿
아래 섹션을 다음 ADD 작업 시작 시 그대로 복붙한다.

### 2) 공통 작업 규칙
```text
[작업 규칙]
- DOCS/CLEAN_CODE_PRINCIPLES.md, DOCS/PR_GUIDE.md, DOCS/COMMIT_CONVENTION.md를 최우선 적용한다.
- 기능(슬래시 커맨드/이벤트/권한/응답) 또는 DB 스키마 변경 시 DOCS/API_SPECIFICATION.md를 반드시 먼저/함께 업데이트한다.
- else 금지, indent depth 1 준수(중첩은 함수 분리).
- 작업 완료 시:
  (1) 변경 파일 목록
  (2) API_SPECIFICATION 반영 요약
  (3) 추천 커밋 메시지(컨벤션 준수)
를 반드시 출력한다.
```

### 3) 기능 구현 요청 템플릿
```text
Step X — <기능명>
DOCS/API_SPECIFICATION.md의 관련 섹션을 먼저 확인하고 구현해줘.

요구사항:
1) <요구사항 1>
2) <요구사항 2>
3) <요구사항 3>

제약:
- else 금지, indent depth 1 준수
- 필요 시 함수 분리

작업 후:
- 명세서 동기화
- 추천 커밋 메시지
```

### 4) 테스트 추가 요청 템플릿
```text
Step X — 테스트 추가
아래 항목을 검증하는 최소 테스트를 추가해줘.

요구사항:
1) <유틸 또는 서비스 테스트>
2) <경계값/예외 테스트>

제약:
- 테스트에서 Discord/JDA 실제 연결 금지(discord.enabled=false)
- DB는 기본 mock 단위 테스트, 필요 시에만 Testcontainers 통합 테스트

작업 후:
- 변경 파일 목록
- 테스트 결과 요약
- 추천 커밋 메시지
```

### 5) 리뷰/검증 요청 템플릿
```text
Step X — 검증
다음을 확인해줘.

1) 기능 요구사항 충족 여부
2) 문서 동기화 누락 여부
3) 테스트 누락 또는 회귀 위험

출력:
- 이슈 목록(중요도 순)
- 수정 제안
- 추천 커밋 메시지
```

### 6) 완료 보고 템플릿
```text
완료 보고:
1) 변경 파일 목록
2) 핵심 변경 요약
3) DOCS/API_SPECIFICATION 반영 내용
4) 테스트 실행 결과
5) 추천 커밋 메시지(2~3개)
```
