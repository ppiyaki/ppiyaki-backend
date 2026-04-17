---
feature: OpenAI TTS 기반 음성 합성 API
slug: tts
status: deprecated
owner: @qkrehgus02
scope: chat
related_issues: [77]
related_prs: []
last_reviewed: 2026-04-10
---

> **⚠️ Deprecated**: 독립 `/api/v1/tts` 엔드포인트는 음성 채팅 통합 API로 대체되었습니다. 현재 구현은 [`docs/features/voice-chat.md`](voice-chat.md)를 참조하세요.

# OpenAI TTS 기반 음성 합성 API

## 1) 개요 (What / Why)
- 텍스트를 OpenAI TTS API로 음성(mp3)으로 변환해 반환하는 독립 TTS API.
- 음성 채팅 파이프라인(STT → LLM → TTS)의 출력 단계.
- LLM 텍스트 채팅(#36), STT(#75)가 이미 구현되어 있으므로, TTS를 독립 API로 제공해 파이프라인 완성에 대비한다.

## 2) 사용자 시나리오
- 시니어가 복약 관련 질문을 하면 LLM이 생성한 텍스트 답변을 음성으로 변환해 앱에서 재생한다.
- 보호자가 약물 정보 질문에 대한 답변을 음성으로 들을 수 있다.

## 3) 요구사항
### 기능 요구사항
- [ ] `POST /api/v1/tts` 엔드포인트 구현
- [ ] 텍스트 입력 → mp3 바이너리 응답 (Content-Type: audio/mpeg)
- [ ] OpenAI TTS API 연동 (Spring AI `OpenAiAudioSpeechModel`)

### 비기능 요구사항
- API 키는 환경변수(`${OPENAI_API_KEY}`)로 주입 (기존 설정 재사용)
- 인증 없이 호출 가능 (추후 인증 일괄 적용 예정)

## 4) 범위 / 비범위

### 포함
- `POST /api/v1/tts` 단일 엔드포인트
- 요청 DTO (`TtsRequest`)
- mp3 바이너리 직접 반환 (Content-Type: audio/mpeg)
- OpenAI TTS 연동 서비스
- TtsService 단위 테스트
- E2E 성공 케이스 테스트 (RestAssured)

### 제외 (Out of Scope)
- STT + LLM + TTS 파이프라인 통합 (→ 세션 관리 이슈에서)
- 실시간 스트리밍 TTS (WebSocket 등)
- 음성 파일 서버 저장
- 세션/히스토리 관리 (→ 별도 이슈)
- 로그인 인증 (→ 추후 일괄 적용)

## 5) 설계

### 5-1) 도메인 모델
- 기존 `chat` 컨텍스트에 추가. 이번 이슈에서는 엔티티 없음 (stateless).

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/tts | 텍스트 → 음성(mp3) 변환 | 없음 | `TtsRequest` (JSON) | mp3 바이너리 (audio/mpeg) |

**TtsRequest**
```json
{
  "text": "아스피린은 공복에 복용을 피하세요."
}
```

**Response**
- Content-Type: `audio/mpeg`
- Body: mp3 바이너리 데이터

### 5-3) 외부 연동
- **OpenAI TTS**: Spring AI `OpenAiAudioSpeechModel` 사용
- API 키: 기존 `${OPENAI_API_KEY}` 재사용
- 모델: `tts-1`
- 음성: `alloy` (기본값)
- 응답 포맷: mp3
- 실패 처리: TTS 호출 실패 시 500 반환

### 5-4) 데이터 흐름
```text
Client → POST /api/v1/tts (JSON)
       → TtsController
       → TtsService
       → OpenAI TTS API
       → mp3 바이너리
       → Response (Content-Type: audio/mpeg)
```

### 5-5) DB 마이그레이션
- 없음 (stateless, 엔티티 미사용)

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: Feature Spec 작성 (`docs/features/tts.md`) — `type:docs`
- [ ] PR 2: TtsController/TtsService/DTO + TTS 연동 + 테스트 (#77)

## 7) 테스트 전략
- **단위 테스트**: `TtsService` — `OpenAiAudioSpeechModel` mock, 정상 변환/실패 케이스
- **E2E**: `POST /api/v1/tts` 성공 케이스 (RestAssured, TTS API mock)

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| — | 현재 오픈 질문 없음 | — | — |

## 9) 결정 로그
- 2026-04-10: 초안 작성 (status=draft). OpenAI TTS 사용, stateless 독립 API로 범위 한정.
- 2026-04-10: 응답 형태 — mp3 바이너리 직접 반환 (Content-Type: audio/mpeg). Base64 JSON 대비 단순하고 크기 효율적.
- 2026-04-10: 모델 tts-1, 음성 alloy로 결정.
