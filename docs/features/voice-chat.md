---
feature: 음성 채팅 통합 API
slug: voice-chat
status: draft
owner: @qkrehgus02
scope: chat
related_issues: [108]
related_prs: []
last_reviewed: 2026-04-14
---

# 음성 채팅 통합 API

## 1) 개요 (What / Why)
- 음성 입력을 받아 STT → LLM → TTS를 한번에 처리하고 음성으로 응답하는 통합 엔드포인트를 추가한다.
- 기존 독립 STT/TTS 컨트롤러를 제거하고, ChatSessionController에 통합한다.
- 같은 세션에서 텍스트/음성 모드를 혼용할 수 있다.

## 2) 사용자 시나리오
- 시니어가 앱에서 마이크 버튼을 눌러 "아스피린 부작용이 뭐야?"라고 말하면, 서버가 음성을 텍스트로 변환 → LLM 답변 생성 → 음성으로 변환해 mp3로 반환한다.
- 같은 세션에서 텍스트로 질문하다가 음성으로 전환해도 대화 맥락이 유지된다.

## 3) 요구사항
### 기능 요구사항
- [ ] `POST /api/v1/chat/sessions/{sessionId}/voice-messages` 엔드포인트 추가
- [ ] multipart/form-data로 음성 파일 수신 → STT → 세션 Chat → TTS → mp3 바이너리 응답
- [ ] 기존 텍스트 메시지 엔드포인트 유지
- [ ] SttController, TtsController 제거
- [ ] SttResponse, TtsRequest DTO 제거

### 비기능 요구사항
- JWT 인증 필수 (기존 인증 적용 유지)
- 같은 세션/히스토리 공유 (텍스트/음성 혼용 가능)

## 4) 범위 / 비범위

### 포함
- voice-messages 엔드포인트 구현
- SttController, TtsController, SttResponse, TtsRequest 제거
- 관련 테스트 정리 및 신규 테스트
- SecurityConfig에서 /api/v1/stt, /api/v1/tts 경로 제거

### 제외 (Out of Scope)
- RAG (약 정보 연동) — 별도 이슈
- 실시간 스트리밍 (WebSocket)

## 5) 설계

### 5-1) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/chat/sessions/{sessionId}/messages | 텍스트 대화 (기존) | 필수 | `ChatMessageRequest` (JSON) | `ChatMessageResponse` (JSON) |
| POST | /api/v1/chat/sessions/{sessionId}/voice-messages | 음성 대화 (신규) | 필수 | `MultipartFile` (form-data) | mp3 바이너리 (audio/mpeg) |

### 5-2) 데이터 흐름

```text
Client → POST /api/v1/chat/sessions/{id}/voice-messages (multipart audio)
       → ChatSessionController
       → SttService.transcribe(audio) → 텍스트 변환
       → ChatSessionService.sendMessage(userId, sessionId, 텍스트) → LLM 응답
       → TtsService.synthesize(LLM 응답) → mp3 바이너리
       → Response (Content-Type: audio/mpeg)
```

### 5-3) 삭제 대상
- `SttController.java`
- `TtsController.java`
- `SttResponse.java`
- `TtsRequest.java`
- `SttControllerTest.java`
- `TtsControllerTest.java`
- `SttE2ETest.java`
- `TtsE2ETest.java`

## 6) 작업 분할
- [ ] PR 1: voice-messages 구현 + 컨트롤러/DTO 제거 + 테스트 (#108)

## 7) 테스트 전략
- **단위 테스트**: voice-messages 흐름 (SttService → ChatSessionService → TtsService)
- **컨트롤러 테스트**: voice-messages 정상/빈 파일/만료 세션
- **E2E**: JWT 토큰 포함 voice-messages 성공 케이스

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| — | 현재 오픈 질문 없음 | — | — |

## 9) 결정 로그
- 2026-04-14: 초안 작성 (status=draft).
- 2026-04-14: 텍스트/음성 엔드포인트를 같은 컨트롤러에 두고, 같은 세션 공유.
- 2026-04-14: 독립 STT/TTS 컨트롤러 제거. 서비스는 내부 호출용으로 유지.
