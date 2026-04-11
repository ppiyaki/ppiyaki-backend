---
feature: 텍스트 채팅 세션 관리 API
slug: chat-session
status: draft
owner: @qkrehgus02
scope: chat
related_issues: [79]
related_prs: []
last_reviewed: 2026-04-10
---

# 텍스트 채팅 세션 관리 API

## 1) 개요 (What / Why)
- 기존 stateless 단일 턴 채팅(#36)을 multi-turn 세션 기반으로 확장한다.
- 이전 대화 히스토리를 LLM에 전달해 맥락을 유지하는 대화를 가능하게 한다.
- 향후 음성 채팅 통합 API의 기반이 된다.

## 2) 사용자 시나리오
- 시니어가 "아스피린 부작용이 뭐야?"라고 질문하고, 이어서 "그거 얼마나 먹어야 해?"라고 물으면 LLM이 이전 맥락을 기억해 아스피린 복용량을 답변한다.
- 5분 이상 대화가 없으면 세션이 만료되고, 새 세션을 생성해야 한다.

## 3) 요구사항
### 기능 요구사항
- [ ] `POST /api/v1/chat/sessions` — 세션 생성
- [ ] `POST /api/v1/chat/sessions/{sessionId}/messages` — 세션 내 메시지 전송 + LLM 응답
- [ ] 이전 대화 히스토리(최근 20개)를 LLM 호출 시 함께 전달
- [ ] 마지막 메시지 후 5분 경과 시 세션 만료, 만료된 세션에 메시지 전송 시 에러 반환
- [ ] `chat_sessions`, `chat_messages` 엔티티 신설

### 비기능 요구사항
- API 키는 환경변수(`${OPENAI_API_KEY}`)로 주입 (기존 설정 재사용)
- 인증 없이 호출 가능 (추후 인증 일괄 적용 예정)
- 히스토리 개수 제한(20개)은 설정값으로 관리

## 4) 범위 / 비범위

### 포함
- 세션 생성/만료 로직
- 세션 내 메시지 전송 + LLM 응답 + 히스토리 저장
- `chat_sessions`, `chat_messages` 엔티티/테이블
- 도메인 모델 문서 갱신 (`06-domain-model.md`)
- 단위/컨트롤러/E2E 테스트

### 제외 (Out of Scope)
- 음성 채팅 통합 API (STT+LLM+TTS 파이프라인) — 별도 이슈
- 세션 목록 조회 / 삭제 API
- RAG (약 정보 연동) — 별도 이슈
- 로그인 인증 — 추후 일괄 적용

## 5) 설계

### 5-1) 도메인 모델
기존 `chat` 컨텍스트에 엔티티 2개 신설:

**chat_sessions**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint PK | |
| created_at | timestamp | `CreatedTimeEntity` |
| updated_at | timestamp | `BaseTimeEntity` — 마지막 활동 시각으로 만료 판단에 사용 |

**chat_messages**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint PK | |
| session_id | bigint FK | `chat_sessions.id` 참조 |
| role | varchar | `USER` / `ASSISTANT`. Java는 `MessageRole` enum |
| content | text | 메시지 내용 |
| created_at | timestamp | `CreatedTimeEntity` |

> 인증 도입 전이므로 `user_id`는 보류. 인증 추가 시 `chat_sessions`에 `user_id` 컬럼 추가 예정.

### 5-2) API 엔드포인트
| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/chat/sessions | 세션 생성 | 없음 | 없음 | `ChatSessionResponse` |
| POST | /api/v1/chat/sessions/{sessionId}/messages | 메시지 전송 | 없음 | `ChatMessageRequest` | `ChatMessageResponse` |

**ChatSessionResponse**
```json
{
  "sessionId": 1
}
```

**ChatMessageRequest**
```json
{
  "message": "아스피린 부작용이 뭐야?"
}
```

**ChatMessageResponse**
```json
{
  "message": "아스피린은 위장장애, 출혈 위험이..."
}
```

**만료된 세션에 메시지 전송 시**
- HTTP 410 (Gone) 반환
- `{ "error": "세션이 만료되었습니다. 새 세션을 생성해주세요." }`

### 5-3) 외부 연동
- **OpenAI GPT**: 기존 Spring AI `ChatClient` 재사용
- 히스토리 메시지를 Spring AI의 user/assistant 메시지로 변환해 프롬프트에 포함

### 5-4) 데이터 흐름
```
Client → POST /api/v1/chat/sessions
       → 세션 생성 → ChatSessionResponse 반환

Client → POST /api/v1/chat/sessions/{id}/messages
       → 세션 만료 확인 (updated_at + 5분)
       → 만료 시 에러 반환
       → DB에서 해당 세션의 최근 20개 메시지 조회
       → [시스템 프롬프트 + 히스토리 + 새 메시지] → LLM 호출
       → 사용자 메시지 + LLM 응답을 DB에 저장
       → 세션 updated_at 갱신
       → ChatMessageResponse 반환
```

### 5-5) DB 마이그레이션
- `chat_sessions`, `chat_messages` 테이블 신설
- Hibernate `ddl-auto: update`로 자동 생성 (현재 정책)

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: Feature Spec 작성 — `type:docs`
- [ ] PR 2: 엔티티 + 서비스 + 컨트롤러 + 테스트 (#79)

## 7) 테스트 전략
- **단위 테스트**: `ChatSessionService` — 세션 생성, 메시지 전송, 만료 판단, 히스토리 제한
- **컨트롤러 테스트**: 정상/만료 세션/빈 메시지 케이스
- **E2E**: 세션 생성 → 메시지 전송 성공 케이스 (RestAssured)

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| — | 현재 오픈 질문 없음 | — | — |

## 9) 결정 로그
- 2026-04-10: 초안 작성 (status=draft).
- 2026-04-10: 히스토리 제한 — 최근 20개 메시지. 시니어 복약 질문 특성상 충분.
- 2026-04-10: 세션 만료 — 마지막 메시지 후 5분. 만료 시 에러 반환 + 새 세션 생성 유도.
- 2026-04-10: 인증 전이므로 user_id 보류. 인증 도입 시 추가.
- 2026-04-10: 음성 채팅 통합 API는 별도 이슈로 분리.
