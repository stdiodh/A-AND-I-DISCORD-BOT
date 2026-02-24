# Contributing Guide

## 개발/리뷰 규칙 문서
- 커밋 컨벤션: `DOCS/COMMIT_CONVENTION.md`
- PR 규칙: `DOCS/PR_GUIDE.md`
- 클린 코드 원칙: `DOCS/CLEAN_CODE_PRINCIPLES.md`
- API 명세: `DOCS/API_SPECIFICATION.md`

## 문서 동기화(필수)
- 기능(슬래시 커맨드/이벤트/권한/응답) 변경 시 `DOCS/API_SPECIFICATION.md`를 함께 수정한다.
- DB 스키마 변경 시 Flyway 마이그레이션과 `DOCS/API_SPECIFICATION.md`의 데이터 스키마 섹션을 함께 수정한다.

## PR 작성
- `.github/pull_request_template.md`를 사용한다.
- PR 설명에는 What/Why, 영향 범위, 테스트 결과를 포함한다.
