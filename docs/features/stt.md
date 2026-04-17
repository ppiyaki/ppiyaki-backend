---
feature: OpenAI Whisper 기반 STT API
slug: stt
status: deprecated
owner: @qkrehgus02
scope: chat
related_issues: [75]
related_prs: []
last_reviewed: 2026-04-10
---

> **⚠️ Deprecated**: 독립 `/api/v1/stt` 엔드포인트는 음성 채팅 통합 API로 대체되었습니다. 현재 구현은 [`docs/features/voice-chat.md`](voice-chat.md)를 참조하세요.

# OpenAI Whisper 기반 STT API

## 1) 개요 (What / Why)
- 모바일(iOS/Android)에서 녹음한 음성 파일을 받아 OpenAI Whisper로 텍스트를 변환하는 독립 STT API.
- 시니어가 음성으로 복약 관련 질문을 할 수 있도록 하는 음성 채팅 파이프라인(STT → LLM → TTS)의 입력 단계.
- LLM 텍스트 채팅(#36)이 이미 구현되어 있으므로, STT를 독립 API로 제공해 추후 파이프라인 통합에 대비한다.

## 2) 사용자 시나리오
- 시니어는 앱에서 마이크 버튼을 누르고 "아스피린은 언제 먹어야 해?"라고 말한다. 앱이 녹음 파일을 서버에 전송하면, 서버는 텍스트로 변환해 반환한다.
- 보호자는 음성으로 약물 정보를 질문하기 위해 마이크 버튼을 사용한다. 변환된 텍스트는 이후 LLM 채팅 API에 전달된다.

## 3) 요구사항
### 기능 요구사항
- [ ] `POST /api/v1/stt` 엔드포인트 구현
- [ ] multipart/form-data로 음성 파일 수신
- [ ] OpenAI Whisper API로 음성 → 텍스트 변환
- [ ] 변환된 텍스트를 JSON 응답으로 반환
- [ ] 지원 포맷: mp3, wav, m4a, webm (Whisper 지원 포맷)

### 비기능 요구사항
- API 키는 환경변수(`${OPENAI_API_KEY}`)로 주입, 코드 하드코딩 금지 (기존 설정 재사용)
- 인증 없이 호출 가능 (추후 인증 일괄 적용 예정)
- 음성 파일 크기 제한: 25MB (Whisper API 제한)

## 4) 범위 / 비범위

### 포함
- `POST /api/v1/stt` 단일 엔드포인트
- 요청/응답 DTO
- OpenAI Whisper 연동 서비스
- SttService 단위 테스트
- E2E 성공 케이스 테스트 (RestAssured)

### 제외 (Out of Scope)
- TTS (→ 별도 이슈)
- 실시간 스트리밍 STT (WebSocket 등)
- 음성 파일 서버 저장/보관
- 세션/히스토리 관리 (→ 별도 이슈)
- 로그인 인증 (→ 추후 일괄 적용)
- STT + LLM 파이프라인 통합 (→ 세션 관리 이슈에서)

## 5) 설계

### 5-1) 도메인 모델
- 기존 `chat` 컨텍스트에 추가. 이번 이슈에서는 엔티티 없음 (stateless).
- 음성 파일은 저장하지 않고 변환 후 폐기.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/stt | 음성 파일 → 텍스트 변환 | 없음 | `MultipartFile` (form-data) | `SttResponse` |

**Request**
- Content-Type: `multipart/form-data`
- 파라미터: `file` (음성 파일), `language` (선택, 기본값 `ko`)

**SttResponse**
```json
{
  "text": "아스피린 복용 시 주의사항이 뭔가요?"
}
```

### 5-3) 외부 연동
- **OpenAI Whisper**: Spring AI 또는 OpenAI REST API 직접 호출
- API 키: 기존 `${OPENAI_API_KEY}` 재사용
- 모델: `whisper-1`
- 언어: 기본값 `ko` (한국어). 요청 파라미터 `language`로 변경 가능
- 실패 처리: Whisper 호출 실패 시 500 반환

### 5-4) 데이터 흐름
```text
Mobile App → 마이크 녹음 → 음성 파일 생성
           → POST /api/v1/stt (multipart/form-data)
           → SttController
           → SttService
           → OpenAI Whisper API
           → 텍스트 추출
           → SttResponse 반환
```

### 5-5) DB 마이그레이션
- 없음 (stateless, 엔티티 미사용)

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: Feature Spec 작성 (`docs/features/stt.md`) — `type:docs`
- [ ] PR 2: SttController/SttService/DTO + Whisper 연동 + 테스트 (#75)

## 7) 테스트 전략
- **단위 테스트**: `SttService` — Whisper 클라이언트 mock, 정상 변환/실패 케이스
- **E2E**: `POST /api/v1/stt` 성공 케이스 (RestAssured, Whisper API mock)

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| — | 현재 오픈 질문 없음 | — | — |

## 9) 결정 로그
- 2026-04-10: 초안 작성 (status=draft). OpenAI Whisper 사용, stateless 독립 API로 범위 한정.
- 2026-04-10: 지원 포맷은 Whisper API 기본 지원 포맷(mp3, wav, m4a, webm)을 그대로 수용.
- 2026-04-10: Q1 해소 — Spring AI `OpenAiAudioTranscriptionModel` 사용. 기존 ChatClient와 추상화 일관성 유지.
- 2026-04-10: Q2 해소 — 언어 기본값 `ko`, 요청 파라미터 `language`로 변경 가능하게.
