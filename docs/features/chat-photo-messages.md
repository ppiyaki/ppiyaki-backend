---
feature: 채팅 사진 첨부 — 약 식별·자유질의
slug: chat-photo-messages
status: draft
owner: @goohong
scope: chat
related_issues: []
related_prs: []
last_reviewed: 2026-05-07
---

# 채팅 사진 첨부 — 약 식별·자유질의

## 1) 개요 (What / Why)
시니어가 채팅으로 사진을 보내면 LLM이 **약/비약을 자동 분기**해 답한다.
- **약 사진**: 식별 + 기존 MCP 도구(`searchMedicine`, `getDrugInfo`, `checkDur`)로 정보 보강
- **약 외 사진**: 도구 호출 없이 시각 질의에 자유롭게 답변 (의료 외 영역엔 한계 명시)

이미 prod에서 사용 중인 vision 모델(`gpt-5.4-mini`)을 단일 호출 + (필요 시) 도구 chain으로 결합. 분기는 별도 분류 모델/코드 없이 **시스템 프롬프트 규칙으로 LLM이 자율 결정** (Spring AI tool calling이 LLM 자율 호출 구조라 가능).

대상 사용자: 시니어(약 봉투를 잃었거나 색·각인이 평소와 달라 확인하고 싶을 때, 또는 일상 사물에 대한 가벼운 질의), 보호자(시니어의 약 사진 받아 확인).

**왜 채팅 안에서 처리하는가?**
- 처방전(`POST /prescriptions`)·복약 인증(`PUT /medication-logs`)은 정해진 흐름의 입구 — "내 약 잔여량 알려줘", "이 약 뭐야?" 같은 자유질의는 그 흐름 밖.
- 시니어가 이미 익숙한 채팅 UX에서 이미지 첨부만 추가하면 진입 장벽 낮음.

**Out — 본 spec 외 시나리오** (§4 비범위)
- "이 약 색이 평소랑 달라" 같은 의료적 시각 질의 (B): hallucination 위험 + 면책 정책 별도 검토
- 처방전을 채팅에서 바로 confirm 흐름 시작 (C): 기존 `/prescriptions`와 중복
- 복약 인증 사진을 채팅에서 등록 (D): 기존 `/medication-logs`와 중복

## 2) 사용자 시나리오
- **약 식별 흐름**: 시니어가 손바닥에 알약 한 알 올리고 사진 + "이거 뭐야?" 텍스트 첨부 → 단발 채팅 호출. 백엔드가 vision으로 사진 분석 + 도구(`searchMedicine`/`getDrugInfo`)로 정보 보강 → "흰색 원형, 한쪽 'T' 각인 — 타이레놀정 500mg으로 보입니다. 식후 30분 복용을 권장합니다." 응답.
- **세션 후속 질의**: 같은 세션에서 "이 약 부작용 뭐야?" 질의 → 이전 ASSISTANT 메시지에 약 묘사가 포함되어 있어 컨텍스트로 활용. 동일 이미지 재해석 없음.
- **약 외 사진 자유질의**: 시니어가 정원의 꽃 사진 + "이 꽃 이름 알려줘" 보냄 → LLM이 약이 아님을 판단, 약 도구 호출하지 않고 "라벤더로 보입니다. 향이 좋아 …" 같은 자유 답변.
- **건강·의료 영역 사진**: 약은 아니지만 건강 관련 사진(예: 발진 사진) → "사진만으로는 정확한 진단이 어렵습니다. 의료기관·약사 상담을 권합니다." 한계 명시.
- **식별 불가 약 사진**: 흐릿하거나 각인 안 보이는 약 → "사진에서 식별이 어렵습니다. 약 봉투/처방전과 함께 보여주시거나 약사에게 문의해 주세요." fallback.

## 3) 요구사항
### 기능 요구사항
- [ ] `POST /api/v1/chat/photo-messages` (multipart) — 단발 사진 메시지 (임시 세션 자동 생성)
- [ ] `POST /api/v1/chat/sessions/{sessionId}/photo-messages` (multipart) — 세션 사진 메시지
- [ ] 두 엔드포인트 모두 `file` (image, required) + `message` (text, optional) multipart
  - `message` 미지정 시 default prompt: `"이 사진 속 약을 식별하고 알려줘"`
- [ ] 응답: SSE stream (기존 `/messages`와 동일 패턴)
- [ ] `ChatSessionService.sendPhotoMessageStream(userId, sessionId, file, message)` 신설
- [ ] 시스템 프롬프트에 "응답 시 사진 속 약을 짧게 묘사한 다음 답하라" 지시 → ASSISTANT 메시지에 묘사 자연 포함 → 후속 질의에서 컨텍스트 재참조 가능
- [ ] **약/비약 자동 분기**: 시스템 프롬프트로 LLM이 사진 속이 약인지 자체 판단. 약이면 묘사 + 약 관련 도구 chain, 약이 아니면 도구 호출 없이 일반 시각 질의 답변
- [ ] vision 모델 = `OpenAiProperties.visionModel` (`gpt-5.4-mini`) 그대로 사용
- [ ] 메시지 히스토리 저장
  - USER: `"[이미지 첨부] {입력 텍스트 or default}"` (이미지 자체 X, placeholder만)
  - ASSISTANT: LLM 응답 텍스트 (묘사 + 답변)
- [ ] 도구 호출 chain: vision LLM이 응답하며 필요 시 `searchMedicine` / `getDrugInfo` / `checkDur` 등 호출 가능 (PR #232 ToolContext 패턴 그대로 — `userId`는 `prompt.toolContext()`로 명시 전달)
- [ ] 면책 처리: 시스템 프롬프트에 "확실치 않으면 추측 금지, 보호자/약사 상담 권장 명시" 규칙
- [ ] 인증: 기존 채팅과 동일 (JWT 필수)
- [ ] 입력 검증: 빈 파일 거절(`CHAT_PHOTO_FILE_EMPTY` or 기존 `CHAT_VOICE_FILE_EMPTY` 패턴 참조), MIME type whitelist (`image/jpeg`, `image/png`, `image/webp`), 최대 사이즈 10MB

### 비기능 요구사항
- **응답 시간**: 실측 기반(prod medication-log gpt-5.4-mini ~1.7s) + 도구 호출 1~2회 포함 시 평균 3~5s, p95 ~10s. SSE 스트리밍이라 첫 토큰까지의 TTFB는 ~2~3s.
- **비용**: vision 호출당 약 2원 추정 (medication-log Phase 2와 동일). 이미지 detail "low"로 제한해 토큰 절약 검토.
- **보안**: 이미지는 **메모리에서만 처리**. S3 저장 없음. base64 인코딩만 일시 보유.
- **관측성**: 별도 메트릭 본 spec 외. 운영 데이터 보고 추후.

## 4) 범위 / 비범위 (중요)

### 포함
- 단발(`/chat/photo-messages`) + 세션(`/chat/sessions/{id}/photo-messages`) 두 엔드포인트
- multipart 단일 호출 입력 방식
- vision LLM 호출 + 기존 MCP 도구 chain
- 메시지 히스토리에 텍스트 placeholder + ASSISTANT 묘사 포함 응답
- E2E 테스트 (mock vision)
- 시스템 프롬프트 규칙 정의

### 제외 (Out of Scope)
- **B/C/D use case** (§1 참조): 별도 spec
- **이미지 영구 보존 / 후속 질의에서 동일 이미지 재해석**: presigned URL + objectKey 패턴(B 옵션). 보존 가치 명확해지면 후속 spec
- **면책 정책 정밀화** — 본 spec은 시스템 프롬프트 한 문장만. 의료 면책 문구 표준화는 별도 검토
- **음성 + 이미지 동시 첨부** — 음성으로 `"이거 뭐야"` 말하면서 사진 첨부. 복합 multipart, 본 spec 외
- **여러 사진 동시 첨부** — 단일 사진만. 다중은 후속
- **보호자 대리 호출 흐름의 권한 체크 강화** — 시니어 자신만 호출하든 보호자 대리든 본 spec에선 token userId 그대로 사용
- **이미지 사이즈 자동 압축** — 클라이언트 책임. 서버는 10MB 제한만
- **이미지 detail "high" 모드** — 비용 ↑, 본 spec은 default(`auto`)

## 5) 설계

### 5-1) 도메인 모델
- 컨텍스트: `chat`
- DB 변경 **없음**. 메시지 히스토리는 기존 `chat_messages` 테이블 그대로(USER 메시지 본문에 placeholder 텍스트만 저장).
- 외부 도메인(medicine/medication 등)에 영향 없음.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | `/api/v1/chat/photo-messages` | 단발 사진 메시지 (multipart) | 필수 | `file` + `message?` | SSE stream |
| POST | `/api/v1/chat/sessions/{sessionId}/photo-messages` | 세션 사진 메시지 (multipart) | 필수 | `file` + `message?` | SSE stream |

#### multipart 형식
```
Content-Type: multipart/form-data; boundary=...

--boundary
Content-Disposition: form-data; name="file"; filename="pill.jpg"
Content-Type: image/jpeg

<binary>
--boundary
Content-Disposition: form-data; name="message"

이 약 뭐야?
--boundary--
```

#### 에러 응답
| 상황 | HTTP | code |
|---|---|---|
| 파일 누락/빈 파일 | 400 | `CHAT_PHOTO_FILE_EMPTY` (신설, `CHAT_005`) |
| 잘못된 MIME type | 400 | `INVALID_INPUT` (`COMMON_001`) |
| 파일 크기 초과(>10MB) | 413 | `INVALID_INPUT` |
| 인증 누락/만료 | 401 | `AUTH_001` / `AUTH_002` |
| 세션 없음/만료/소유 아님 | 404/410/403 | `CHAT_001`/`CHAT_002`/`CHAT_003` |

### 5-3) 외부 연동 — OpenAI Vision
- 모델: `gpt-5.4-mini` (`OpenAiProperties.visionModel`, 환경변수 `OPENAI_VISION_MODEL`)
- 입력: messages 배열에 system prompt + user 메시지(텍스트 + image_url(base64 data URL))
- 시스템 프롬프트 초안:
  ```
  당신은 시니어를 돕는 복약 관리 비서입니다.
  사용자가 사진을 보내면 다음 규칙을 따르세요:

  1. 사진을 보고 약(알약/캡슐/약병/약 봉투/처방전 등)인지 먼저 판단하세요.

  2. 약이면:
     - 첫 문장에 사진 속 약을 짧게 묘사하세요 (색·모양·각인 등). 후속 질의에서 컨텍스트로 활용됩니다.
     - 식별이 명확하면 약 이름을 알려주고, 필요한 경우 도구(searchMedicine, getDrugInfo, checkDur)를 사용해 정보를 보강하세요.
     - 식별이 불확실하면 추측 금지 — "사진에서 식별이 어렵습니다. 약 봉투/처방전과 함께 보여주시거나 약사에게 문의해 주세요."로 안내하세요.

  3. 약이 아니면:
     - 약 관련 도구는 호출하지 마세요.
     - 사진 속 대상을 짧게 묘사하고, 사용자 질문에 자유롭게 답하세요.
     - 의료·건강 조언이 필요한 영역(예: 발진·상처 등)이면 "사진만으로는 정확한 판단이 어렵습니다. 의료기관·약사 상담을 권합니다." 한계를 명시하세요.

  4. 진단·처방 같은 의료 결정은 하지 마세요.
  5. 한국어 존댓말, 시니어가 이해하기 쉬운 짧은 문장으로.
  ```
- 응답 streaming: 기존 `chatClient.prompt(...).stream().content()` 그대로
- 도구 chain: `prompt.toolContext(Map.of("userId", userId))` 주입(PR #232 패턴 동일). vision LLM이 응답 도중 도구 호출 결정 가능

### 5-4) 데이터 흐름
```
클라이언트 (multipart: file=image, message="이거 뭐야?")
  ↓
ChatController.quickPhotoMessage  /  ChatSessionController.sendPhotoMessage
  ↓ MultipartFile → byte[] 변환 + MIME 검증 + 사이즈 검증
  ↓ ChatSessionPersistenceService.createSession  (단발) / 기존 세션 검증 (세션)
  ↓
ChatSessionService.sendPhotoMessageStream(userId, sessionId, imageBytes, mediaType, message)
  ├─ 시스템 프롬프트 + 기존 세션 히스토리 + 새 user message(텍스트 + image_url base64)
  ├─ chatClient.prompt(prompt).toolContext(Map.of("userId", userId)).stream().content()
  ├─ 토큰 stream → SseEmitter.send
  └─ doOnComplete:
       persistenceService.saveMessages(sessionId,
           "[이미지 첨부] " + (message != null ? message : "이 사진 속 약을 식별해줘"),
           fullResponse.toString())
```

### 5-5) DB 마이그레이션
**없음**.

### 5-6) 코드 영향 범위
| 파일 | 변경 |
|---|---|
| `chat/controller/ChatController.java` | `quickPhotoMessage` 엔드포인트 추가 (multipart) |
| `chat/controller/ChatSessionController.java` | `sendPhotoMessage` 엔드포인트 추가 (multipart) |
| `chat/service/ChatSessionService.java` | `sendPhotoMessageStream` 메서드 신설 |
| `chat/service/ChatSessionPersistenceService.java` | photo placeholder 메시지 저장 헬퍼 (필요 시) |
| `common/exception/ErrorCode.java` | `CHAT_PHOTO_FILE_EMPTY` 추가 |
| `chat/PhotoMessageValidator` (or inline) | MIME/사이즈 검증 |
| `test/.../ChatPhotoE2ETest.java` | 신규 단발 + 세션 E2E |
| `test/.../ChatSessionServiceTest.java` | sendPhotoMessageStream mock chain |
| `docs/ai-harness/06-domain-model.md` | §4 유비쿼터스 랭귀지에 "사진 메시지" 등재(선택) |
| Notion API 명세 | 신규 엔드포인트 2개 등록 (사용자 사이클) |

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1 (`docs(chat)`): 본 spec
- [ ] PR 2 (`feat(chat)`): 두 엔드포인트 + ChatSessionService.sendPhotoMessageStream + 시스템 프롬프트 + ErrorCode + E2E

## 7) 테스트 전략
- **단위 테스트**:
  - MIME/사이즈 검증 분기 (image/jpeg, image/png, image/webp 통과 / image/gif·application/pdf 거절)
  - default prompt fallback (message null/blank → 기본 prompt 사용)
- **E2E (RestAssured + mock ChatClient)**:
  - 단발 사진 메시지 → SSE 응답 토큰 스트리밍, USER+ASSISTANT 메시지 DB 저장 확인
  - 세션 사진 메시지 → 동일 흐름 + 세션 컨텍스트 검증
  - 빈 파일 → 400 CHAT_PHOTO_FILE_EMPTY
  - 잘못된 MIME → 400 COMMON_001
  - 인증 누락 → 401
  - 세션 만료 → 410 CHAT_002
- vision API는 `ChatClient` mock으로 처리 (실제 OpenAI 호출 없음). 단발/세션에서 도구 호출 chain은 별도 통합 테스트 외 — 본 PR 범위는 이미지 입력 흐름 검증.

## 8) 오픈 질문
| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q1 | 이미지 detail 수준 | (a) auto (default, Recommended) / (b) low (비용↓ 정확도↓) / (c) high (비용↑↑) | (a) — MVP. 운영 데이터 보고 후속 |
| Q2 | 입력 사이즈 상한 | 10MB Recommended | (a) 10MB. 시니어 폰 사진 평균 3-5MB 여유 |
| Q3 | message 미지정 시 default prompt 문구 | "이 사진 속 약을 식별해줘" / 다른 안 | 결정 — 위 문구로 잠정 |
| Q4 | MIME whitelist | jpeg/png/webp / + heic | 결정 — jpeg/png/webp. heic는 클라이언트가 변환 |
| Q5 | 음성 + 사진 결합 | 본 spec 외 | 결정 — 후속 |
| Q6 | 약 외 사진 응답 범위 | (a) 자유롭게 답변 (Recommended, 의료 외 영역엔 한계 명시) / (b) "약 식별 위주 채팅입니다" 정중 거절 | (a) — 시니어 UX 우호적. 의료·건강 영역만 한계 명시 |

## 9) 결정 로그
- 2026-05-07: 초안 작성 (status=draft). 채팅 사진 첨부 use case A(약 식별)에 한정.
- 2026-05-07: **multipart 단일 호출** 채택. presigned URL + objectKey 2-step 미채택. 이유: 시니어 단발 질의 UX 단순성 우선, 보존 가치 작음(후속 질의 시 ASSISTANT 응답에 포함된 묘사로 충분).
- 2026-05-07: **이미지는 메모리에서만 처리, S3 저장 X**. 보존 가치 명확해지면 별도 spec으로 objectKey 도입.
- 2026-05-07: **메시지 히스토리에 텍스트 placeholder만 저장**. ASSISTANT 응답 안에 약 묘사 포함되도록 시스템 프롬프트 유도 → 후속 질의에서 컨텍스트 재참조.
- 2026-05-07: **단발 + 세션 두 엔드포인트 모두 지원**. 음성 채팅과 일관 (`/voice-messages` 패턴).
- 2026-05-07: **vision 모델 = `gpt-5.4-mini` 그대로**. 비용·정확도 검증된 prod 운영 모델 재사용.
- 2026-05-07: **도구 chain은 PR #232 ToolContext 패턴 그대로**. SecurityContextHolder 의존 0.
- 2026-05-07: **면책 정책은 시스템 프롬프트 한 문장**. 정밀화는 별도 spec.
- 2026-05-07: **약/비약 자동 분기 — 시스템 프롬프트 규칙으로 LLM 자율 결정**. 별도 분류 모델/코드 X. Spring AI tool calling이 LLM 자율 호출 구조라 가능. 약 사진이면 도구 chain, 약 아니면 도구 호출 없이 자유 답변. 의료·건강 영역은 한계 명시.
