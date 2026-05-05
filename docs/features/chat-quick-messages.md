---
feature: 단발 채팅 API (텍스트/음성)
slug: chat-quick-messages
status: draft
owner: @goohong
scope: chat
related_issues: [120]
related_prs: []
last_reviewed: 2026-05-05
---

# 단발 채팅 API (텍스트/음성)

## 1) 개요 (What / Why)
- 세션 관리 없이 단발(single-turn)로 LLM과 대화하는 엔드포인트.
- 시니어가 1회성 짧은 질문을 던질 때 세션 생성·유지 부담을 없앤다.
- **Primary Why**: "1회성 단발 질문" 사용 패턴에 맞는 가벼운 흐름. 세션 만료·이전 맥락 의존 없음.
- 내부적으로 임시 세션이 자동 생성되며 메시지 1쌍이 저장된 뒤 그대로 둔다 (이전 대화 맥락 미사용).

## 2) 사용자 시나리오
- 시니어가 "타이레놀 부작용 알려줘"라고 묻고 답을 받음. 그 다음 질문은 다른 주제 — 이전 맥락이 필요 없음.
- 시니어가 음성으로 "지금 약 먹어도 돼?"라고 물으면 STT → LLM → TTS 음성 응답을 SSE로 받음.
- 두 흐름 모두 클라이언트는 sessionId 신경 쓰지 않음.

## 3) 요구사항

### 기능 요구사항
- [ ] `POST /api/v1/chat/messages` — 단발 텍스트 대화. SSE 토큰 chunk + `[DONE]` 종료 (chat-streaming.md §5-1과 동일).
- [ ] `POST /api/v1/chat/voice-messages` — 단발 음성 대화. multipart(`file`, `language`) → STT → LLM → TTS, SSE chunk(`{text, audio}`) + `[DONE]` (§5-2).
- [ ] 두 엔드포인트 모두 내부적으로 `chat_sessions` row 자동 생성 후 `chat_messages`에 USER+ASSISTANT 메시지 저장 (이력 추적용).
- [ ] 인증 필수 (JWT). 빈 음성 파일은 `CHAT_VOICE_FILE_EMPTY`(400) 반환.

### 비기능 요구사항
- 응답 시간: 텍스트 토큰은 즉시 스트림. 음성은 첫 문장 단위 청크 도달 후 클라이언트가 재생 가능.
- SSE 타임아웃: 기존 60초 정책 유지.
- 보안: JWT 검증 통과한 user_id로 `chat_sessions.user_id` 채움. 다른 사용자의 세션 접근 불가능 (세션 없이 단발이라 자연 격리).

## 4) 범위 / 비범위

### 포함
- 두 엔드포인트 추가 (`ChatController` 신설).
- 기존 `ChatSessionPersistenceService.createSession` + `ChatSessionService.sendMessageStream` / `sendVoiceMessageStream` 재사용.
- 단위 테스트 (Controller wiring 검증).
- E2E 테스트 (성공 케이스 1건 + 빈 음성 파일 400 케이스).

### 제외 (Out of Scope)
- 단발 세션의 자동 정리(TTL/cron 삭제). 세션 row 누적은 별도 정리 이슈.
- 인증 우회 모드 — 세션 채팅과 동일하게 JWT 필수.
- 세션 채팅과의 통합 (단일 엔드포인트로 합치기) — 의도적으로 분리.

## 5) 설계

### 5-1) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | `/api/v1/chat/messages` | 단발 텍스트 대화 | 필수 | `ChatMessageRequest` (`{message}`) | SSE 스트림 (chat-streaming.md §5-1) |
| POST | `/api/v1/chat/voice-messages` | 단발 음성 대화 | 필수 | multipart (`file`, `language`) | SSE 스트림 (chat-streaming.md §5-2) |

### 5-2) 데이터 흐름 (텍스트)
```text
Client → POST /api/v1/chat/messages {message}
       → ChatController.quickMessage
       → persistenceService.createSession(userId)  ── 임시 세션 생성
       → chatSessionService.sendMessageStream(userId, sessionId, message)
       → SSE 스트림 (토큰 chunk + [DONE])
       → 백그라운드: persistenceService.saveMessages 로 USER+ASSISTANT 저장
```

### 5-3) 데이터 흐름 (음성)
```text
Client → POST /api/v1/chat/voice-messages (multipart: file, language)
       → ChatController.quickVoiceMessage
       → file.isEmpty() → 400 CHAT_VOICE_FILE_EMPTY
       → sttService.transcribe(file, language) → text
       → persistenceService.createSession(userId)
       → chatSessionService.sendVoiceMessageStream(userId, sessionId, text, ttsService)
       → SSE 스트림 ({text, audio: base64} chunk + [DONE])
```

### 5-4) DB 마이그레이션
- 없음. 기존 `chat_sessions` / `chat_messages` 테이블 그대로 사용.

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1 (본 PR): spec + 컨트롤러 + 테스트 통합 — 작업 분량 작음 (`type:feat`, `scope:chat`).

## 7) 테스트 전략

### E2E
- 성공: 텍스트 단발 PUT → SSE 스트림 수신 → DB에 USER+ASSISTANT row 1쌍 저장 확인.
- 실패: 음성 단발 빈 파일 → 400 `CHAT_VOICE_FILE_EMPTY`.
- 인증: 토큰 없이 호출 → 401 `AUTH_001`.

## 8) 오픈 질문
| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| — | 임시 세션 row 정리 정책 | TTL/cron 삭제 vs 영구 보존. 본 spec은 영구 보존 (이력 추적용). 누적 부담 발생 시 별도 정리 이슈 | 운영 데이터 보고 결정 |

## 9) 결정 로그
- 2026-05-05: 초안 작성. URI는 `/api/v1/chat/messages` / `/api/v1/chat/voice-messages`로 결정 (`/quick` 제거 — 형용사 도메인 용어 부적절).
- 2026-05-05: 임시 세션 row 자동 생성 방식 채택. 이전 spec 초안의 stateless 모델보다 이력 추적 가능성 + 기존 service 재사용 측면에서 유리.
- 2026-05-05: SSE 응답 형식은 chat-streaming.md (§5-1 텍스트, §5-2 음성) 그대로 따름.
