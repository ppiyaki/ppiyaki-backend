---
feature: Chat API 인증 적용
slug: chat-auth
status: draft
owner: @qkrehgus02
scope: chat
related_issues: [106]
related_prs: []
last_reviewed: 2026-04-13
---

# Chat API 인증 적용

## 1) 개요 (What / Why)
- 채팅 관련 API(세션 생성, 메시지 전송, STT, TTS)에 JWT 인증을 적용한다.
- chat_sessions에 user_id를 연결해 사용자별 세션을 구분한다.
- 기존 permitAll로 열어둔 chat 엔드포인트를 인증 필수로 전환한다.

## 2) 사용자 시나리오
- 로그인한 시니어가 채팅 세션을 생성하면, 해당 세션은 본인에게만 귀속된다.
- 인증 없이 chat API를 호출하면 401 Unauthorized를 반환받는다.

## 3) 요구사항
### 기능 요구사항
- [ ] SecurityConfig에서 chat/stt/tts 엔드포인트의 permitAll 제거
- [ ] chat_sessions에 user_id 컬럼 추가
- [ ] ChatSessionController에서 `@AuthenticationPrincipal Long userId` 활용
- [ ] 세션 생성 시 user_id 저장
- [ ] 메시지 전송 시 세션의 소유자 검증

### 비기능 요구사항
- 기존 JWT 인증 인프라(JwtProvider, JwtAuthenticationFilter) 재사용
- STT/TTS는 인증만 필수, user_id 연결은 불필요 (stateless)

## 4) 범위 / 비범위

### 포함
- SecurityConfig 수정 (permitAll 제거)
- ChatSession 엔티티에 userId 추가
- ChatSessionService/Controller에 userId 파라미터 추가
- 소유자 아닌 세션 접근 시 403 Forbidden
- 테스트에 인증 처리 추가
- 도메인 모델 문서 갱신

### 제외 (Out of Scope)
- 음성 채팅 통합 API — 별도 이슈
- RAG — 별도 이슈

## 5) 설계

### 5-1) 도메인 모델
chat_sessions에 user_id 컬럼 추가:

| 컬럼 | 타입 | 설명 |
|---|---|---|
| user_id | bigint FK | `users.id` 참조. 세션 소유자 |

### 5-2) API 변경
- 기존 엔드포인트 동일, 인증 헤더 필수: `Authorization: Bearer <token>`
- 세션 생성 시 토큰에서 userId 추출해 저장
- 타인 세션 접근 시 403

### 5-3) 데이터 흐름

```text
Client → Authorization: Bearer <token>
       → JwtAuthenticationFilter → userId 추출
       → ChatSessionController (@AuthenticationPrincipal Long userId)
       → ChatSessionService (userId로 소유자 검증)
```

## 6) 작업 분할
- [ ] PR 1: 인증 적용 + 엔티티 수정 + 테스트 (#106)

## 7) 테스트 전략
- 컨트롤러 테스트: 인증 없이 호출 시 401, 타인 세션 접근 시 403
- E2E: JWT 토큰 포함 요청으로 세션 생성/메시지 전송

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| — | 현재 오픈 질문 없음 | — | — |

## 9) 결정 로그
- 2026-04-13: 초안 작성 (status=draft).
- 2026-04-13: 기존 @AuthenticationPrincipal Long userId 패턴 재사용.
- 2026-04-13: STT/TTS는 인증만 필수, user_id 연결 불필요.
