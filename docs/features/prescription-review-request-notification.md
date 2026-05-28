---
feature: 처방전 검토 요청 알림 (시니어 등록 → MANAGED 보호자 푸시)
slug: prescription-review-request-notification
status: draft
owner: @goohong
scope: prescription
related_issues: [423]
related_prs: []
last_reviewed: 2026-05-28
---

# 처방전 검토 요청 알림

> 발단: 디스코드 백로그 2번 (5/13) — 시니어가 처방전을 올렸을 때 careMode=MANAGED 시니어의 보호자에게 "검토해 주세요" 알림. 메모리에는 "완료"로 잘못 적혀 있었으나 본 세션 검증 결과 코드 미구현. `DUR_WARNING`(위험 약물 검출 시)과는 분리된 카테고리.

## 1) 개요 (What / Why)

careMode가 `MANAGED`인 시니어는 보호자의 처방전 검증 윈도우(0~72h)가 필수다. 현재는 시니어가 처방전을 등록해도 보호자에게 별도 알림이 가지 않아, 보호자가 앱을 직접 열어 확인해야만 한다. 본 기능은 **시니어가 처방전을 등록한 직후 MANAGED 보호자에게 "검토해 주세요" 푸시 + 알림함 row**를 발송한다.

대상 액터: MANAGED 시니어의 보호자.
해결 문제: 보호자가 처방전 검토 윈도우(0~72h) 진입을 놓치는 경우 방지.

## 2) 사용자 시나리오

- 시니어 김장군(careMode=MANAGED)이 처방전 사진을 등록(`POST /api/v1/prescriptions`)한다. OCR + candidate 생성이 끝난 직후 김장군의 보호자 모두에게 "김장군 어르신의 새 처방전이 도착했어요. 검토해 주세요." 푸시가 발송된다.
- AUTONOMOUS 시니어 박여사가 처방전을 등록하면 → 알림 발송 없음 (보호자 검증 강제 정책 없으므로).
- 보호자가 폰 알림을 탭하면 deep link로 해당 처방전 상세 화면으로 이동한다 (payload의 `prescriptionId`).
- 보호자 A, B 두 명이 연결된 시니어가 처방전을 등록하면 두 보호자 모두 알림을 받는다.

## 3) 요구사항

### 기능 요구사항
- [ ] **careMode 분기**: `MANAGED` 시니어의 처방전만 알림 발송. `AUTONOMOUS` 시니어는 무시.
- [ ] **트리거 시점**: `PrescriptionService.processAndCreate` 트랜잭션 commit 이후 (`@TransactionalEventListener(AFTER_COMMIT)`). OCR + candidate 영속화가 모두 끝난 뒤 발송.
- [ ] **다중 보호자**: `care_relations.deleted_at IS NULL`인 활성 보호자 전원에게 발송.
- [ ] **NotificationCategory 추가**: `PRESCRIPTION_REVIEW_REQUEST` enum 값 추가. 알림함 row 영속화 카테고리.
- [ ] **알림함 row 생성**: `Notification` 테이블에 row 1건/보호자 (다른 카테고리와 동일 패턴).
- [ ] **푸시 본문**: 제목 `"처방전 검토 요청"`, 본문 `"{시니어 닉네임} 어르신의 새 처방전이 도착했어요. 검토해 주세요."`. payload data: `{ "category": "PRESCRIPTION_REVIEW_REQUEST", "seniorId": "<id>", "prescriptionId": "<id>" }`.
- [ ] **NotificationSettings 토글 (옵션)**: §8 Q1에서 결정. 1차 단순화로 무조건 발송 권장.
- [ ] **`DUR_WARNING`과 독립**: 같은 처방전이라도 DUR 위험이 검출되면 별도 푸시 1건이 추가로 발송됨 (기존 흐름). 본 카테고리는 등록 사실 자체 알림.

### 비기능 요구사항
- **트랜잭션 안전성**: AFTER_COMMIT으로 발송 — DB 롤백 시 푸시 안 나감.
- **관측성**: `PRESCRIPTION_REVIEW_REQUEST dispatched (seniorId={}, prescriptionId={}, recipients={})` INFO 로그 1줄.
- **인증**: 본 기능 자체는 endpoint 없음. 기존 처방전 등록 endpoint(시니어 JWT) 위에 hook 추가.

## 4) 범위 / 비범위

### 포함
- `NotificationCategory.PRESCRIPTION_REVIEW_REQUEST` enum 추가
- `PrescriptionReviewRequestedEvent` 도메인 이벤트 + listener
- `PrescriptionReviewRequestDispatcher` 신규 빈
- `Notification.createForPrescriptionReviewRequest` 정적 팩토리
- 도메인 모델 문서(`docs/ai-harness/06-domain-model.md` §4 유비쿼터스 랭귀지) 등재
- E2E 성공 케이스 + AUTONOMOUS 무발송 케이스

### 제외 (Out of Scope)
- 보호자 미검토 시 reminder (예: 24h 뒤 재발송) — 후속 spec
- 보호자가 처방전 거부(`/reject`) 시 시니어에게 알림 — 별도 spec
- 보호자가 검토 완료(`/confirm`) 시 시니어에게 알림 — 별도 spec
- deep link 라우팅 자체 — 프론트엔드 작업
- 처방전 외 다른 도메인(약 추가/수정)의 검토 요청 알림

## 5) 설계

### 5-1) 도메인 모델

**`NotificationCategory` enum 변경**:
- `PRESCRIPTION_REVIEW_REQUEST` 추가.

**`Notification` 엔티티 변경**:
- 정적 팩토리 `createForPrescriptionReviewRequest(caregiverId, seniorId, prescriptionId, title, body)` 추가.
- 알림함 row 영속화 — `target_date`/`meal_slot` 등 복약 관련 필드는 NULL, `senior_id` + `prescription_id`(또는 `schedule_id` 컬럼 재활용?) 사용. §8 Q2.

**도메인 이벤트**:
- `PrescriptionReviewRequestedEvent(prescriptionId, seniorId)` 신설 (`com.ppiyaki.prescription.event` 패키지).
- `PrescriptionService.processAndCreate`에서 트랜잭션 안에서 `applicationEventPublisher.publishEvent(...)` 호출.
- `PrescriptionReviewRequestDispatcher`가 `@TransactionalEventListener(phase = AFTER_COMMIT)` 으로 구독.

**`docs/ai-harness/06-domain-model.md` §4 유비쿼터스 랭귀지** 등재:
- "처방전 검토 요청 알림 / Prescription Review Request Notification — careMode=MANAGED 시니어가 처방전 등록 시 보호자 전원에게 발송하는 푸시 + 알림함 row".

### 5-2) API 엔드포인트

신규 endpoint 없음. 기존 `POST /api/v1/prescriptions` 응답에는 영향 없음.

### 5-3) 외부 연동

- **FCM (PushSender)**: 기존 인터페이스 재사용. `PushPayload(title, body, data)`.

### 5-4) 데이터 흐름

```
[시니어] POST /api/v1/prescriptions { objectKey }
   ↓
[PrescriptionService.processAndCreate]
   1. OCR + candidate 생성 + Prescription row 저장
   2. applicationEventPublisher.publishEvent(new PrescriptionReviewRequestedEvent(...))
   3. 트랜잭션 commit
   ↓ AFTER_COMMIT
[PrescriptionReviewRequestDispatcher]
   1. Prescription owner(시니어) 조회
   2. senior.careMode == MANAGED 확인 (AUTONOMOUS면 return)
   3. CareRelationRepository.findBySeniorIdAndDeletedAtIsNull
   4. 각 보호자에 대해:
      - NotificationSettings 체크 (옵션, §8 Q1)
      - Notification row INSERT (PRESCRIPTION_REVIEW_REQUEST)
      - 활성 DeviceToken 조회 → FCM 발송 → tokenInvalid 시 deactivate
   5. INFO 로그 1줄
```

### 5-5) DB 마이그레이션

- **컬럼 변경 없음**: `notifications.category` 컬럼은 `varchar(32)` STRING enum이라 신규 값 추가 시 마이그레이션 불필요.
- 다만 `Notification`이 `prescription_id`를 저장할 컬럼이 필요하면 §8 Q2 결정에 따라 마이그레이션 추가 가능.

## 6) 작업 분할 (예상 PR 리스트)

- [ ] PR 1: spec 초안 (본 문서)
- [ ] PR 2: 구현 (enum + 이벤트 + listener + dispatcher + Notification 팩토리 + E2E + 도메인 모델 문서 + Notion 명세)

## 7) 테스트 전략

- **단위 테스트**:
  - `PrescriptionReviewRequestDispatcher`: MANAGED → 보호자 N명에 발송 / AUTONOMOUS → 발송 없음 / 보호자 0명 → 발송 없음 / `tokenInvalid` → deactivate.
- **E2E (RestAssured) 필수**:
  - MANAGED 시니어 처방전 등록 → Notification row 1건/보호자 + 푸시 호출 검증.
  - AUTONOMOUS 시니어 처방전 등록 → Notification row 미생성 검증.
  - 활성 보호자 0명 → row 미생성 + 푸시 미호출.

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q1 | `NotificationSettings`에 `prescriptionReviewRequestEnabled` 토글 추가 | (a) 1차 무조건 발송, 토글 없음 (추천) / (b) 토글 추가, default ON | @goohong / spec 합의 시점 |
| Q2 | `Notification` row에 `prescription_id`를 저장할지 | (a) 기존 `schedule_id` 컬럼 재활용 (이름 혼란) / (b) 신규 `prescription_id` 컬럼 추가 (마이그레이션) / (c) payload에만 담고 row에는 미저장 (추천 — `senior_id` + `created_at`으로 조회 가능) | @goohong / spec 합의 시점 |
| Q3 | 보호자 본인이 시니어 대리로 처방전 등록 시 알림 | (a) 등록한 보호자 본인에게는 미발송, 나머지 보호자에게만 (추천) / (b) 전원 발송 / (c) careMode 무관 등록자가 시니어일 때만 알림 (보호자 등록은 자체 알림 X) | @goohong / spec 합의 시점 |

## 9) 결정 로그

- 2026-05-28: 초안 작성 (status=draft). 디스코드 백로그 2번 의도 재확인 — careMode=MANAGED 시니어 처방전 등록 시 보호자 알림.
- 2026-05-28: 합의 4건 — 발송 조건 MANAGED, 트리거 OCR 완료 직후 자동, 알림함 row 생성, 보호자 전원 발송.
