---
feature: LLM 텍스트 채팅 API
slug: llm-chat
status: deprecated
owner: @qkrehgus02
scope: chat
related_issues: [36]
related_prs: []
last_reviewed: 2026-04-09
---

> **⚠️ Superseded**: 이 문서의 stateless `/api/v1/chat` 엔드포인트는 세션 기반 multi-turn 채팅으로 대체되었습니다. 현재 구현은 [`docs/features/chat-session.md`](chat-session.md)를 참조하세요.

# LLM 텍스트 채팅 API

## 1) 개요 (What / Why)
- 사용자가 텍스트 메시지를 입력하면 OpenAI GPT가 응답을 반환하는 채팅 API.
- 향후 STT/TTS와 연결되는 AI 대화 파이프라인의 핵심 컴포넌트.
- 복약 관련 질문에 답하는 챗봇의 기반이 되며, 이번 이슈에서는 stateless 단일 턴으로 구현한다.

## 2) 사용자 시나리오
- 시니어는 복약 관련 궁금한 점을 텍스트로 입력하면 GPT로부터 답변을 받는다.
- 보호자는 약물 정보나 복약 방법을 질문하고 응답을 받는다.

## 3) 요구사항
### 기능 요구사항
- [ ] `POST /api/v1/chat` 엔드포인트 구현
- [ ] 텍스트 메시지 입력 → GPT 텍스트 응답 반환
- [ ] Spring AI + OpenAI ChatClient 연동
- [ ] stateless 단일 턴 (세션 없음)

### 비기능 요구사항
- API 키는 환경변수(`${OPENAI_API_KEY}`)로 주입, 코드 하드코딩 금지
- 인증 없이 호출 가능 (추후 인증 추가 예정)

## 4) 범위 / 비범위

### 포함
- Spring AI OpenAI 의존성 추가
- `POST /api/v1/chat` 단일 엔드포인트
- 요청/응답 DTO
- ChatService 단위 테스트
- E2E 성공 케이스 테스트 (RestAssured)

### 제외 (Out of Scope)
- 세션/히스토리 관리 (→ 다음 이슈)
- STT/TTS 연동 (→ 별도 이슈)
- RAG / 약학 정보 API 연동 (→ 별도 이슈)
- 로그인 인증 (→ 추후 수정)
- 스트리밍 응답

## 5) 설계

### 5-1) 도메인 모델
- 신규 `chat` 컨텍스트. 이번 이슈에서는 엔티티 없음 (stateless).
- 엔티티는 세션 관리 이슈에서 추가 예정.

### 5-2) API 엔드포인트
| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/chat | 텍스트 메시지 → GPT 응답 | 없음 | `ChatRequest` | `ChatResponse` |

**ChatRequest**
```json
{
  "message": "아스피린 복용 시 주의사항이 뭔가요?"
}
```

**ChatResponse**
```json
{
  "message": "아스피린은 ..."
}
```

### 5-3) 외부 연동
- **OpenAI GPT**: Spring AI `spring-ai-openai-spring-boot-starter` 사용
- API 키: 환경변수 `OPENAI_API_KEY` → `application.yml`에서 `${OPENAI_API_KEY}`로 참조
- 모델: `gpt-4o-mini` (기본값, 설정으로 변경 가능)
- 실패 처리: OpenAI 호출 실패 시 500 반환

### 5-4) 데이터 흐름
```
Client → POST /api/v1/chat
       → ChatController
       → ChatService
       → Spring AI ChatClient → OpenAI GPT API
       → ChatResponse 반환
```

### 5-5) DB 마이그레이션
- 없음 (stateless, 엔티티 미사용)

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: Spring AI 의존성 추가 + application.yml 설정 + ChatService/Controller/DTO + 테스트 (#36)

## 7) 테스트 전략
- **단위 테스트**: `ChatService` — `ChatClient` mock
- **E2E**: `POST /api/v1/chat` 성공 케이스 (RestAssured, WireMock으로 OpenAI mock)

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q1 | 시스템 프롬프트 내용 | 복약 도우미 역할 부여 예정, 구체적인 문구 미정 | @qkrehgus02 |

## 9) 결정 로그
- 2026-04-09: 초안 작성 (status=draft). stateless 단일 턴으로 범위 한정, 세션/RAG는 별도 이슈로 분리.
- 2026-04-09: GPT 모델 gpt-4o-mini로 결정. RAG 도입 후 품질 비교해서 재검토 예정.
