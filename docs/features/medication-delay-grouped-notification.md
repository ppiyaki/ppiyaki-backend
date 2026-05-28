---
feature: 복약 지연 알림 슬롯 단위 묶기
slug: medication-delay-grouped-notification
status: draft
owner: @goohong
scope: medication
related_issues: [409]
related_prs: []
last_reviewed: 2026-05-28
---

# 복약 지연 알림 슬롯 단위 묶기

> 부모 spec: `docs/features/medication-notification.md` (§5-4 보호자 미복약/지연 알림 흐름의 개선).
> 본 spec은 `MEDICATION_DELAY` 카테고리만 다룬다. 다른 카테고리(REMINDER/DUR/COMPLETE/FAMILY_SAFETY)는 변경 없음.

## 1) 개요 (What / Why)

현재 `MedicationDelayDispatcher.dispatchForSenior`는 한 슬롯(BREAKFAST/LUNCH/DINNER)에 미인증된 schedule이 N개면 보호자에게 푸시 N건을 발송한다. 시니어가 아침에 약 3개를 안 먹으면 보호자 폰 알림 영역에 같은 슬롯·같은 시니어 알림이 3건 쌓여 시각적 노이즈가 발생한다.

본 기능은 **슬롯·시니어·날짜 단위로 1건 묶어 발송**한다. 본문에 미복용 약 목록을 줄바꿈으로 나열하고, 데이터 페이로드에 `scheduleIds` 배열을 포함한다.

대상 액터: 보호자.
해결 문제: 슬롯당 N건의 중복 알림 → 1건 묶음 알림.

## 2) 사용자 시나리오

- 시니어 김장군이 아침에 타이레놀500mg / 비타민C / 위장약 3개를 60분 넘게 인증하지 않는다. 보호자는 **1건의 알림**을 받아 "김장군 어르신이 아침에 약 3개를 아직 복용하지 않았어요. (60분 경과)\n• 타이레놀500mg\n• 비타민C\n• 위장약"을 본다.
- 시니어가 약 1개만 안 먹은 경우도 동일 흐름으로 1건. 본문은 약 개수 표현 없이 단일 약 형식으로 (§8 Q1).
- 같은 슬롯에 추후 약을 일부만 인증해도 이미 발송된 알림은 갱신하지 않는다 (slot+date 단위 멱등 1회).

## 3) 요구사항

### 기능 요구사항
- [ ] **슬롯 단위 1건 발송**: 한 슬롯의 미인증 schedule이 N개여도 보호자에게 푸시 1건 + 알림함 row 1건만 생성한다.
- [ ] **본문 약 목록 나열**: N≥1 모두 동일한 묶음 포맷. 본문에 미복용 약 라벨을 `\n• `로 구분해 **전부 나열**한다. 라벨 형식은 기존 `resolveMedicineLabel`과 동일 (`{약이름} {복용량}`).
- [ ] **payload `scheduleIds` JSON 배열 문자열**: FCM data에 `scheduleIds`를 JSON 배열 문자열로 포함 (예: `"[123,456,789]"`). FCM data는 String 값만 허용하므로 프론트가 `JSON.parse` 적용.
- [ ] **slot+date 단위 멱등**: 같은 `(caregiver_id, senior_id, target_date, meal_slot)`에 대해 1회만 발송. 이후 추가 미복용이 생겨도 재발송하지 않는다. 코드 레벨 `existsBy` 체크만 사용 (DB UNIQUE 인덱스 변경 없음 — §5-5 옵션 A 확정).
- [ ] **다른 카테고리 미변경**: REMINDER/DUR/COMPLETE/FAMILY_SAFETY 코드 경로와 메시지 포맷 무변경.

### 비기능 요구사항
- **호환성**: FCM data payload key 변경 (`scheduleId` 단수 → `scheduleIds` JSON 배열 문자열). 프론트엔드 cut-over 필요 — Notion API 명세 갱신 + 진영에게 디스코드 안내.
- **관측성**: 기존 `MEDICATION_DELAY notification created` INFO 로그 유지. schedule 개수를 로그에 포함.
- **메시지 길이**: FCM body 4KB 제한. 운영상 슬롯당 미인증 약 ≤10개로 추정, 상한 두지 않음.

## 4) 범위 / 비범위

### 포함
- `MedicationDelayDispatcher.dispatchForSenior` 슬롯 단위 집계 로직
- `Notification.createForMedicationDelay` 시그니처 변경 (`scheduleId` 단수 → schedule_id 컬럼 NULL 처리)
- `NotificationRepository` 멱등 체크 메서드 교체 (`existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndScheduleId` → slot+date 단위)
- DB UNIQUE 인덱스 검토 (§5-5)
- 본문 포맷 변경
- 단위 + E2E 테스트 갱신

### 제외 (Out of Scope)
- 시니어 본인의 `MEDICATION_REMINDER`도 마찬가지로 묶을지 — 시니어 측 알림은 이미 슬롯 단위 1건이므로 무변경 (`medication-notification.md` §5-4)
- "추가 미복용 발생 시 갱신 알림" — 첫 발송 1회로 충분, 추가 noise 회피
- 슬롯 외 시간대(임의 schedule time) 처리 — 현재 dispatcher 자체가 mealTime 단위라 N/A
- DUR/COMPLETE 알림 본문/payload 변경

## 5) 설계

### 5-1) 도메인 모델

**`Notification` 엔티티 변경**:
- `createForMedicationDelay` 시그니처에서 `scheduleId` 파라미터 제거. `MEDICATION_DELAY` row의 `schedule_id` 컬럼은 NULL로 저장.
- 미복용 schedule 목록은 본문 텍스트(보호자 가독)와 FCM payload(클라이언트 라우팅)에만 노출. 알림함 row에 schedule ids를 영속할 필요는 현재 사용처 없음.

**`MedicationDelayDispatcher` 변경**:
- 슬롯 루프 내부에서 미복용 schedule 리스트를 먼저 집계 → 빈 리스트면 skip, 비어있지 않으면 1회 `sendDelayNotification(caregiverId, senior, slot, today, schedules, thresholdMinutes)` 호출.
- 보호자 루프 안에서 멱등 체크 → 미발송 시 발송. 멱등 키 = `(caregiverId, MEDICATION_DELAY, seniorId, today, slot)`.

**`NotificationRepository` 변경**:
- `existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndScheduleId(...)` 메서드 제거 (현재 MEDICATION_DELAY 외 사용처 없음 — verify 필요).
- `existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndMealSlot(...)` 신설.

**`docs/ai-harness/06-domain-model.md` 갱신**: 알림 카테고리별 멱등 키 설명을 보유한 섹션이 있다면 갱신. (스펙 갱신 시 확인)

### 5-2) API 엔드포인트

외부 노출 API 명세 무변경. FCM data payload key만 변경:

| Before | After |
|---|---|
| `scheduleId: "123"` (단일 schedule) | `scheduleIds: "[123,456,789]"` (JSON 배열 문자열) |

→ Notion API 명세에서 FCM payload 섹션 갱신 (보호자 미복약 알림 항목).

### 5-3) 외부 연동

FCM payload data 필드:
```json
{
  "category": "MEDICATION_DELAY",
  "seniorId": "16",
  "mealSlot": "BREAKFAST",
  "scheduleIds": "[123,456,789]"
}
```

본문 포맷 — N≥1 동일:
```
{시니어} 어르신이 {슬롯}에 약 {N}개를 아직 복용하지 않았어요. ({임계}분 경과)
• {약1}
• {약2}
• {약3}
```

### 5-4) 데이터 흐름 / 시퀀스

```
slot 루프
  ├─ mealTime 미설정/미경과 → skip
  ├─ slotSchedules 조회 → 빈 set이면 skip
  ├─ takenScheduleIds 집계
  └─ 보호자 루프
       ├─ settings.medicationDelayEnabled=false → skip
       ├─ thresholdMinutes 미경과 → skip
       ├─ unsentSchedules = slotSchedules - takenScheduleIds  ← 신규: 사전 집계
       ├─ unsentSchedules 빈 set → skip                       ← 신규: 전부 인증된 경우
       ├─ 멱등 체크 (slot+date 단위)                          ← 변경
       └─ 미발송 시 sendDelayNotification(unsentSchedules)    ← 변경: 리스트 전달
```

### 5-5) DB 마이그레이션

**없음** — 옵션 A 확정 (§9 결정 로그). `notifications.uk_notifications_dedup` UNIQUE 인덱스 변경하지 않고 코드 레벨 멱등(`existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndMealSlot`)만 적용. MEDICATION_DELAY row의 `schedule_id` 컬럼은 NULL로 저장.

근거:
- cron tick은 1분 간격, 동일 분 내 race는 ppiyaki 운영 규모(보호자 수십 명)에서 무시 가능.
- 코드 `existsBy` 체크가 사실상 멱등 보장.
- MySQL 8 partial unique index 미지원 → 카테고리별 분리는 over-engineering.

⚠️ 보호 영역 변경 없음 → PR에 `needs-human-review` 라벨 불필요.

## 6) 작업 분할 (예상 PR 리스트)

- [ ] **PR 1: spec 초안 머지** (this PR, `type:docs`, `scope:medication`, `ai-generated`)
- [ ] **PR 2: 구현** — `MedicationDelayDispatcher` 슬롯 단위 집계 + `Notification.createForMedicationDelay` 시그니처 변경 + `NotificationRepository` 메서드 교체 + 단위/E2E 테스트 갱신 + Notion API 명세 갱신. **라벨**: `type:refactor`, `scope:medication`, `ai-generated`. DB 마이그레이션 옵션 A 채택 시 보호 영역 변경 없음.

## 7) 테스트 전략

- **단위 (`MedicationDelayDispatcherTest`)**:
  - 슬롯에 미인증 schedule 3개 → 알림 1건만 생성, 본문에 약 3개 나열, payload `scheduleIds`에 3개 ID 포함.
  - 슬롯에 미인증 schedule 1개 → 알림 1건, 본문은 단일 약 포맷.
  - 슬롯의 모든 schedule이 인증됨 → 알림 0건.
  - 같은 slot+date에 두 번째 tick → 알림 0건 (멱등).
  - 보호자 `medicationDelayEnabled=false` → 알림 0건.
  - 임계 미경과 → 알림 0건.
- **E2E**: 기존 보호자 미복약 알림 E2E 테스트가 있다면 본문/payload 형식 검증 추가. 없으면 신규 1건 (CLAUDE.md §4 — 신규 엔드포인트 E2E 필수이지만 이번엔 신규 엔드포인트 X. 그러나 dispatcher 통합 테스트 권장).
- **FCM mock**: `PushSender` mock으로 `PushPayload.data.scheduleIds`가 콤마 구분 String인지 검증.

## 8) 오픈 질문

> 모든 질문 2026-05-28 해소 (§9 참조).

| # | 질문 | 결정 |
|---|---|---|
| ~~Q1~~ | N==1 본문 포맷 | (b) 묶음 형식 통일 ✅ |
| ~~Q2~~ | payload `scheduleIds` 직렬화 | (b) JSON 배열 문자열 ✅ |
| ~~Q3~~ | 본문 약 목록 표시 개수 상한 | (a) 전부 나열, 상한 없음 ✅ |
| ~~Q4~~ | DB UNIQUE 인덱스 변경 | (a) 옵션 A — 코드 멱등만 ✅ |
| Q5 | FCM data key 변경의 프론트엔드 cut-over 시점 | 백엔드 머지 직후 동시 cut-over (호환 윈도 미제공). 진영에게 디스코드로 별도 안내. — 사용자 합의 보류 |

## 9) 결정 로그

- 2026-05-28: 초안 작성 (status=draft). 이슈 #409, 사용자 백로그 3번 항목. 부모 spec `medication-notification.md`의 §5-4 동작 개선. 단일 PR 예상.
- 2026-05-28: **Q1 해소** — N==1도 묶음 포맷 통일. 이유: 코드 단순(분기 제거), 본문 일관성, N==1은 운영상 빈도 낮음. 본문 예시: "김장군 어르신이 아침에 약 1개를 아직 복용하지 않았어요. (60분 경과)\n• 타이레놀500mg".
- 2026-05-28: **Q2 해소** — payload `scheduleIds`는 JSON 배열 문자열 (`"[123,456,789]"`). 이유: 약 ID에 콤마 없음이 보장됨, 프론트 `JSON.parse` 단일 호출로 깔끔, 확장성(향후 객체 추가 가능).
- 2026-05-28: **Q3 해소** — 약 목록 상한 없음, 전부 나열. 이유: 운영상 슬롯당 미인증 ≤10개 추정 → FCM body 4KB 한도 내, 상한 로직 도입 시 "외 N개" UX 추가 결정 필요해 단순화.
- 2026-05-28: **Q4 해소** — 옵션 A 채택. 인덱스 변경 없이 코드 레벨 멱등(`existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndMealSlot`)만 적용. 이유: §5-5 권장안. 보호 영역 변경 없음.
