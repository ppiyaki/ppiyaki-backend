---
feature: 채팅 응답 스트리밍
slug: chat-streaming
status: draft
owner: @qkrehgus02
scope: chat
related_issues: [110]
related_prs: []
last_reviewed: 2026-04-14
---

# 채팅 응답 스트리밍

## 1) 개요 (What / Why)
- 기존 비스트리밍 채팅 응답을 스트리밍으로 전환한다.
- 텍스트 대화: SSE로 토큰 단위 실시간 전송
- 음성 대화: LLM 응답을 문장 단위로 TTS 변환, SSE로 음성 청크 순차 전송
- 시스템 프롬프트를 시니어 맞춤으로 보강한다.

## 2) 사용자 시나리오
- 시니어가 텍스트로 질문하면 답변이 한 글자씩 실시간으로 나타난다.
- 시니어가 음성으로 질문하면 첫 문장 답변 음성이 전체 응답 완료 전에 먼저 재생된다.

## 3) 요구사항
### 기능 요구사항
- [ ] `POST /api/v1/chat/sessions/{id}/messages` 스트리밍(SSE) 전환
- [ ] `POST /api/v1/chat/sessions/{id}/voice-messages` 스트리밍(SSE) 전환
- [ ] 문장 단위 TTS 변환 로직 (. ? ! 기준 문장 분리)
- [ ] 스트림 완료 후 전체 응답 DB 저장
- [ ] 시스템 프롬프트 시니어 맞춤 보강

### 비기능 요구사항
- WebFlux 의존성 추가 (Flux 사용)
- 기존 JWT 인증 유지

## 4) 범위 / 비범위

### 포함
- 기존 두 엔드포인트 스트리밍 전환
- 문장 분리 유틸리티
- 시스템 프롬프트 보강
- 테스트

### 제외 (Out of Scope)
- RAG (약 정보) — 별도 이슈
- WebSocket 방식 — SSE 사용

## 5) 설계

### 5-1) 텍스트 스트리밍 응답 형식

```text
Content-Type: text/event-stream

data: 아스피린은
data: 공복에
data: 복용을
data: 피하세요.
data: [DONE]
```

### 5-2) 음성 스트리밍 응답 형식

```text
Content-Type: text/event-stream

data: {"text": "아스피린은 공복에 복용을 피하세요.", "audio": "<base64 mp3>"}
data: {"text": "위장장애가 발생할 수 있습니다.", "audio": "<base64 mp3>"}
data: [DONE]
```

### 5-3) 데이터 흐름

```text
텍스트:
Client → message → ChatClient.stream().content()
       → Flux<String> → SSE 토큰 전송
       → 완료 후 DB 저장

음성:
Client → audio file → STT → 텍스트
       → ChatClient.stream().content()
       → 토큰 누적 → 문장 완성 시 TTS 호출
       → SSE 음성 청크 전송
       → 완료 후 DB 저장
```

## 6) 작업 분할
- [ ] PR 1: 스트리밍 전환 + 시스템 프롬프트 보강 + 테스트 (#110)

## 7) 테스트 전략
- **단위 테스트**: 문장 분리 로직
- **E2E**: 스트리밍 응답 SSE 수신 확인

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| — | 현재 오픈 질문 없음 | — | — |

## 9) 결정 로그
- 2026-04-14: 초안 작성 (status=draft).
- 2026-04-14: 기존 엔드포인트를 스트리밍으로 전환 (별도 /stream 엔드포인트 분리 안 함).
- 2026-04-14: 음성 스트리밍은 문장 단위 TTS + SSE base64 전송.
- 2026-04-14: 시스템 프롬프트에 시니어 맞춤 지침 추가.
