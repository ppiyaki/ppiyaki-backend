# AI Harness Docs Index (ppiyaki-backend)

## 1) 목적
- 이 문서 묶음은 `ppiyaki-backend`에서 AI 코딩 하네스를 안전하고 일관되게 운영하기 위한 기준이다.
- 1차 목표는 품질 안정성(우선) + 개발 속도(보조)이며, 객체지향 설계와 DDD 규칙 준수를 포함한다.

## 2) 적용 범위
- 적용: 이 레포(`ppiyaki-backend`)에서 수행하는 AI 기반 코드/문서 작업
- 비적용: 타 레포 공통 정책, 조직 전사 정책

## 3) 문서 목록 (P0)
- `docs/ai-harness/01-harness-spec.md`: 하네스 실행 규격과 결정 규칙
- `docs/ai-harness/02-agent-workflow.md`: 브랜치/PR/커밋/핸드오프 워크플로우
- `docs/ai-harness/03-quality-gates.md`: 빌드/테스트 게이트와 실패 처리
- `docs/ai-harness/04-security-policy.md`: 민감정보/시크릿/금지행위 정책
- `docs/ai-harness/05-prompt-ops.md`: 프롬프트 버전관리/승인/롤백 운영
- `docs/ai-harness/06-domain-model.md`: 도메인 모델/유비쿼터스 랭귀지/ERD (기능 구현의 공통 참조 기준)
- `docs/ai-harness/07-testing-guide.md`: 레이어별 테스트 전략 및 필수 기준
- `docs/ai-harness/08-code-conventions.md`: 코드 컨벤션 (final/어노테이션/DTO/엔티티/Lombok/null 검증 등)
- `docs/ai-harness/09-notion-api-spec.md`: Notion API 명세 연동 가이드 (DB 구조, 갱신 규칙, NOTION_API_KEY 관리)

## 3-1) 관련 자산
- `prompts/`: 재사용 프롬프트 저장소 (운영 규칙은 `05-prompt-ops.md`)
- `scripts/setup-labels.sh`: GitHub 라벨 일괄 생성 스크립트 (`gh` 필요)
- `.github/PULL_REQUEST_TEMPLATE.md`, `.github/ISSUE_TEMPLATE/task.md`: PR/이슈 템플릿
- `docs/features/`: 기능 단위 living 명세서 (Feature Spec). 프로세스는 `02-agent-workflow.md §9`
  - `docs/features/llm-chat.md`: LLM 텍스트 채팅 (superseded by chat-session)
  - `docs/features/chat-session.md`: 멀티턴 채팅 세션 관리
  - `docs/features/chat-streaming.md`: SSE 스트리밍 응답
  - `docs/features/chat-auth.md`: Chat API JWT 인증
  - `docs/features/voice-chat.md`: 음성 채팅 통합
  - `docs/features/stt.md`: OpenAI Whisper STT
  - `docs/features/tts.md`: OpenAI TTS 음성 합성
  - `docs/features/medication-log.md`: 복약 기록 API (인증 사진 첨부 포함, Phase 1)
- `docs/decisions/`: Architecture Decision Records (ADR). 횡단 결정의 영속 이력
- `docs/error-codes.md`: API 에러 코드 목록 (프론트엔드 참고용, ErrorCode enum과 동기화)
- `CLAUDE.md`: Claude Code 세션 자동 로드 룰 요약

## 4) 정책 우선순위
1. 법/규제 및 보안 정책
2. 이 문서 세트
3. 팀 합의(이슈/PR 코멘트)
4. 개인 선호

## 5) 운영 원칙
- 모든 변경은 PR로 수행한다. (`main` 직접 푸시 금지)
- 최소 1명 리뷰 승인 후 머지한다.
- 머지는 기본적으로 Squash merge를 사용한다.
- 불명확한 사항은 이 문서를 먼저 업데이트하고 구현한다.
- 위 규칙들은 GitHub Branch Protection으로 기술적으로 강제하지 않으며, 팀 합의(사회적 규약)로 운영한다.

## 6) 문서 유지관리
- 문서 오너: 백엔드 팀
- 갱신 트리거: 브랜치/PR 규칙 변경, 품질게이트 변경, 보안 사고/정책 변경
- 권장 점검 주기: 스프린트 1회

