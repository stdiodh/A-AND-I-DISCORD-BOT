# Docs Index

## 문서 목록
- API 명세서: `DOCS/API_SPECIFICATION.md`
- 클린 코드 원칙: `DOCS/CLEAN_CODE_PRINCIPLES.md`
- 커밋 컨벤션: `DOCS/COMMIT_CONVENTION.md`
- PR 가이드: `DOCS/PR_GUIDE.md`

## 운영 규칙
- 구현 전에 관련 문서를 먼저 읽고, 코딩/리뷰 시 문서 규칙을 우선 적용한다.
- 커맨드/이벤트/권한/응답/스키마가 바뀌면 문서 동기화를 PR 범위에 반드시 포함한다.
- PR 작성은 `.github/pull_request_template.md` 템플릿을 따른다.

## 문서 동기화 규칙(필수)
- 슬래시 커맨드/이벤트/권한/응답 변경 시 `DOCS/API_SPECIFICATION.md` 업데이트
- DB 스키마 변경 시 Flyway 마이그레이션 + `DOCS/API_SPECIFICATION.md`의 스키마 섹션 업데이트

## PR 전 체크리스트
- [ ] else 미사용
- [ ] indent depth 1 준수
- [ ] 변경 사항이 명세서에 반영됨
- [ ] 커밋 메시지가 컨벤션을 따름
