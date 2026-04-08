---
purpose: 도메인 기능 신규 구현용 기본 프롬프트 (샘플)
scope: prescription
owner: goohong
last_reviewed: 2026-04-08
---

# 목표
- 무엇을 만들거나 수정할지 한 줄로 명시

# 제약
- 아키텍처: DDD 경계 준수, Controller → Service → Repository 계층 유지
- 보안: 시크릿 하드코딩 금지, 의료정보 원문 로그 금지
- 스타일: checkstyle / spotless 통과
- 변경 금지 영역: `docs/ai-harness/01-harness-spec.md` §6 보호 영역 참조

# 입력
- 관련 이슈: #
- 참고 파일/스키마:
- 요구사항:

# 출력
- 변경 코드 (파일 단위)
- 신규/수정 테스트
- 변경 요약 (AS-IS / TO-BE)
- 검증 방법 (`./gradlew checkstyleMain spotlessCheck test`)

# 금지
- 시크릿/토큰 출력
- 민감 의료정보 원문 노출
- 근거 없는 가정 (가정값 사용 시 `가정/영향/검증방법` 3요소 필수 명시)
