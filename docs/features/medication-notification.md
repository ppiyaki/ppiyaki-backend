---
feature: 복약 알림
slug: medication-notification
status: approved
owner: @goohong
scope: medication
related_issues: []
related_prs: []
last_reviewed: 2026-05-10
---

# 복약 알림

## 1) 개요 (What / Why)

시니어가 정해진 시간에 약을 복용하도록 시니어 본인에게는 복약 시간 푸시를 보내고,
보호자에게는 미복약·DUR 위험·앱 미접속·복약 완료 등의 모니터링용 알림을 보낸다.
인앱 알림함에 동일 내용을 영속화하여 과거 알림을 다시 확인할 수 있게 한다.

대상 액터: 시니어, 보호자.
해결하려는 문제:
- 시니어의 복약 시간 누락 — 복약 시간 직접 알림으로 즉시 인지 유도
- 보호자의 비대면 케어 — 미복약/위험 약물/앱 미접속을 실시간으로 인지
- 시간이 지난 알림을 다시 확인할 방법 — 알림함 누적

## 2) 사용자 시나리오

- **시니어**는 아침 8시(자기 mealTime)에 "삐~약드실 시간이에요~" 푸시를 받고 앱을 열어 복약을 인증한다.
- **보호자**는 시니어가 아침 약을 8시 30분이 지나도 인증하지 않으면 "김장군 어르신이 아침 약 복용 시간을 30분 넘겼습니다" 푸시를 받는다 (임계는 보호자가 시니어별로 설정 가능).
- **보호자**는 새 처방전이 등록되어 DUR 위험(연령 금기/병용 금기)이 검출되면 "새로 등록된 처방전에 연령 금기 주의 약물이 포함되어 있습니다" 푸시를 받는다.
- **보호자**는 시니어가 그 날 모든 복약을 완료하면 "축하합니다! 김장군 어르신이 모든 복약을 완료하셨습니다!" 푸시를 받는다.
- **보호자**는 시니어가 24시간(설정 가능) 동안 앱에 접속하지 않으면 "앱 미접속" 알림을 받는다.
- **보호자**는 알림함에서 "전체 / 긴급 경고 / 복약 완료" 탭으로 과거 알림을 분류 조회할 수 있다.
- **보호자**는 알림 설정 페이지에서 시니어별로 항목 on/off 및 임계값을 조정하거나, **일반/집중 프리셋**을 선택해 일괄 적용한다.

## 3) 요구사항

### 기능 요구사항
- [ ] **시니어 복약 시간 알림** (`MEDICATION_REMINDER`): 시니어 mealTime(아침/점심/저녁) 도래 시 시니어 본인에게 푸시 + 알림함 row.
- [ ] **보호자 미복약/지연 알림** (`MEDICATION_DELAY`): mealTime + 임계 분 경과 후 미인증 schedule이 있으면 보호자에게 푸시 + 알림함 row.
- [ ] **보호자 DUR 긴급 경고** (`DUR_WARNING`): 처방전 등록 후 DUR 검사 결과 연령 금기/병용 금기가 있으면 보호자에게 즉시 푸시 + 알림함 row.
- [ ] **보호자 복약 완료 알림** (`MEDICATION_COMPLETE`): 시니어가 그 날 등록된 모든 복약 schedule을 인증하면 보호자에게 푸시 + 알림함 row. (멱등 — 하루 1회만)
- [ ] **보호자 가족 안전망 알림** (`FAMILY_SAFETY`): 시니어 `last_active_at` + 임계 시간 경과 시 보호자에게 푸시 + 알림함 row. (멱등 — 임계 도달 시 1회, 시니어 재접속까지 재발송 X)
- [ ] **알림함 조회 API**: 페이징, 카테고리 필터(전체/긴급 경고/복약 완료), 읽음 상태.
- [ ] **알림 읽음 처리 API**: 단건 + 모두 읽음.
- [ ] **device token 등록/해제 API**: 클라이언트가 FCM 토큰을 등록·해제.
- [ ] **알림 설정 조회/갱신 API**: 보호자가 시니어별 항목 toggle + 임계값 갱신.
- [ ] **프리셋 적용 API**: `STANDARD` / `INTENSIVE` 프리셋을 적용하면 settings 항목이 일괄 갱신.
- [ ] **시니어 `last_active_at` 갱신**: 시니어가 인증 토큰으로 호출하는 일반 API 요청 시 자동 갱신.

### 비기능 요구사항
- **성능**: mealTime 시점 N명에 대한 알림 발송은 비동기 batch 처리. 1회 실행에 5분 이내 완료 (운영 N=수십명 기준 추정).
- **신뢰성**: FCM 호출 실패 시 retry (3회 exponential backoff). 연속 실패한 device token은 `is_active=false` 처리.
- **멱등성**: 같은 schedule + targetDate에 대해 알림은 1회만 발송 (중복 방지). 알림 row의 자연 키로 멱등 보장.
- **보안**: 푸시 메시지 본문은 의료정보 원문 노출 가능 범위만 (약 이름 OK, 처방 사유는 NO). 시니어 nickname은 보호자에게 OK.
- **관측성**: 알림 발송 진입/결과 INFO 로그 (대시보드와 동일 패턴). 카테고리별 발송/실패 카운트 메트릭.

## 4) 범위 / 비범위

### 포함
- 5종 알림 카테고리 (시니어 복약시간 / 보호자 미복약 / DUR / 가족 안전망 / 복약 완료)
- 인앱 알림함 영속화 + 조회/읽음 API
- 보호자 시니어별 알림 설정 + 프리셋 (`notification_settings` 테이블)
- FCM 푸시 발송
- device token 관리 (기존 `DeviceToken` 엔티티 활용)
- 시니어 `last_active_at` 추적
- **박도현 PR refactor** (보호자 ↔ 시니어 N:M settings 모델 채택에 따른 정렬):
  - `users.notification_mode` 컬럼 제거 → `notification_settings` 테이블로 이전
  - `NotificationMode` enum 이름 변경 (`BASIC_ALERT` → `STANDARD`, `INTENSIVE_CARE` → `INTENSIVE`)
  - `NotificationMode` enum 위치 이동 (`com.ppiyaki.user` → `com.ppiyaki.notification`)
  - `OnboardingRequest`에서 `notificationMode` 필드 제거 + `OnboardingService` 리팩터 (default `STANDARD` 프리셋으로 settings row 자동 생성)
  - `docs/features/onboarding.md` spec 갱신 (`§4 제외`에 알림 모드 항목 추가, §5 갱신)

### 제외 (Out of Scope)
- **시니어 본인의 알림 설정**: 시니어가 자기 복약 시간 알림을 끄는 UI는 현재 디자인에 미확인. 추후 별도 spec.
- **재알림(reminder snooze)**: 시니어가 푸시 무시 시 N분 후 재알림. 옵션이지만 이번 스코프 X.
- **사용자 정의 알림 사운드/조용시간**: 추후.
- **Slack/이메일 등 다른 채널**: FCM(Android) + APNs(iOS, FCM 경유) 만.
- **알림 통계/분석 대시보드**: 발송/도달/응답 통계는 후순위.
- **보호자 복약 완료 알림의 "축하" 게이미피케이션 메시지 변형**: 단일 템플릿만.

## 5) 설계

### 5-1) 도메인 모델

**신규 컨텍스트**: `notification` (`com.ppiyaki.notification.*`).
- `Notification` (알림 row, 영속화)
- `NotificationSettings` (보호자 ↔ 시니어별 설정 — N:M)
- `NotificationMode` enum (`STANDARD` / `INTENSIVE` / `CUSTOM`) — STANDARD/INTENSIVE는 프리셋, CUSTOM은 보호자가 settings 항목을 직접 수정한 상태. 기존 `com.ppiyaki.user.NotificationMode` 위치에서 이전 + 이름 변경 (`BASIC_ALERT`→`STANDARD`, `INTENSIVE_CARE`→`INTENSIVE`)
- `NotificationService` (발송 + 조회)
- `PushSender` (FCM 어댑터)

**기존 엔티티 변경/이동**:
- **`User.notificationMode` 필드 제거** (`users.notification_mode` 컬럼 DROP) — 박도현 PR refactor.
- **`OnboardingRequest`/`OnboardingService` 리팩터** — `notificationMode` 필드 제거. 시니어 생성 시 default `STANDARD` 프리셋으로 `notification_settings` row 자동 생성.
- `DeviceToken` (`com.ppiyaki.medication.DeviceToken`) → `notification` 패키지로 이동.
- `User`에 `last_active_at` 컬럼 추가.
- `MedicationSchedule`, `MedicationLog` (트리거 소스).
- `DurCheckService` (DUR 결과를 알림 큐로 라우팅).

**`docs/ai-harness/06-domain-model.md` 갱신 필요**:
- §4 유비쿼터스 랭귀지: `Notification`, `NotificationCategory`, `NotificationMode`(프리셋), `NotificationSettings` 등재
- §5 엔티티: `notifications`, `notification_settings`, `device_tokens`(이전), `users.last_active_at` 추가
- §6 ERD: 위 엔티티 관계 추가

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | `/api/v1/users/me/devices` | FCM device token 등록 | 필수 | `{ token, platform }` | 201 `DeviceTokenResponse` |
| DELETE | `/api/v1/users/me/devices/{tokenId}` | device token 해제 | 필수 (본인) | - | 204 |
| GET | `/api/v1/notifications` | 내 알림 리스트 (페이징, 카테고리 필터) | 필수 | query: `category?`, `cursor?`, `size?` | `NotificationListResponse` |
| PATCH | `/api/v1/notifications/{id}/read` | 단건 읽음 | 필수 (본인) | - | 204 |
| POST | `/api/v1/notifications/read-all` | 모두 읽음 | 필수 | - | 204 |
| GET | `/api/v1/seniors/{seniorId}/notification-settings` | 보호자가 시니어별 설정 조회 | 필수 (활성 보호자) | - | `NotificationSettingsResponse` |
| PUT | `/api/v1/seniors/{seniorId}/notification-settings` | 보호자가 시니어별 설정 갱신 | 필수 (활성 보호자) | `NotificationSettingsUpdateRequest` | `NotificationSettingsResponse` |
| POST | `/api/v1/seniors/{seniorId}/notification-settings/preset` | 프리셋 일괄 적용 | 필수 (활성 보호자) | `{ mode: "STANDARD" | "INTENSIVE" }` | `NotificationSettingsResponse` |

권한 검증:
- `seniorId` 경로의 모든 endpoint는 보호자가 해당 시니어의 활성 `CareRelation`을 가져야 함 (`CARE_RELATION_NOT_FOUND` 403).
- 시니어 본인은 자기 설정 경로 접근 불가 (보호자 전용 — 디자인 의도). 또는 본인도 read 가능? → **오픈 질문 Q3**.

### 5-3) 외부 연동

**FCM (Firebase Cloud Messaging)**
- Android + iOS(APNs는 FCM 경유) 단일 인터페이스.
- 인증: Firebase Service Account JSON.
- 환경변수: `FCM_PROJECT_ID`, `FCM_CREDENTIALS_JSON_BASE64` (또는 path).
- 어댑터: `com.ppiyaki.notification.push.FcmPushSender`.
- 실패 처리: 3회 exponential backoff retry. 5xx/4xx 분기. `NotFoundError` (token invalid) → device token `is_active=false`.
- API 키 관리: `.env` + CD `docker run -e` 동기화 (피야키 CD 환경변수 룰).

**기존 `DurCheckService`**
- 처방전 등록 후 DUR 결과를 받아 위험 등급이 있으면 `NotificationService.sendDurWarning(seniorId, prescriptionId, severity)` 호출.

### 5-4) 데이터 흐름 / 시퀀스

**시니어 복약 시간 알림**:
1. Cron `MedicationReminderScheduler` 매 분 실행
2. 현재 시각 ± 1분 안의 `MedicationSchedule` (시니어 mealTime 매핑)을 조회
3. 시니어별로 묶어 `NotificationService.sendMedicationReminder(seniorId, schedule들, targetDate)` 호출
4. `Notification` row 1건 + 시니어의 활성 `DeviceToken` 별 FCM 발송
5. 멱등 — `(senior_id, category=MEDICATION_REMINDER, target_date, meal_slot)` 자연키로 row 존재하면 skip

**보호자 미복약/지연 알림**:
1. Cron `MedicationDelayChecker` 매 분 실행
2. mealTime + (보호자별 임계 분) 경과한 schedule 중 `MedicationLog` 미인증인 것
3. 해당 시니어의 활성 보호자 + `MEDICATION_DELAY` enabled 보호자에게 발송
4. 멱등 — `(caregiver_id, category=MEDICATION_DELAY, senior_id, target_date, schedule_id)`

**DUR 경고**: 즉시 트리거. `PrescriptionService.confirm` → DUR 검사 → 위험 시 알림.

**복약 완료 알림**: `MedicationLog` 업서트 후 그 날 schedule이 모두 인증되었는지 확인 → 마지막 인증 시 발송. 멱등 `(caregiver_id, category=MEDICATION_COMPLETE, senior_id, target_date)`.

**가족 안전망 알림**: Cron `FamilySafetyChecker` 매 시간 실행. `User.last_active_at + 임계` 경과한 시니어의 보호자에게 발송. 멱등 — `last_active_at` 갱신 시점 이후의 가장 최근 `FAMILY_SAFETY` row가 없으면 발송, 있으면 skip. 시니어 재접속 시 다음 임계 초과 때 새 알림 가능 (cooldown 정책 = 시니어 재접속까지 1회만).

### 5-5) DB 마이그레이션

> ⚠️ 보호 영역 (`**/db/migration/**`). 사람 리뷰 필수.

```sql
-- 박도현 PR refactor: users.notification_mode 컬럼 제거
ALTER TABLE users DROP COLUMN notification_mode;

-- notifications 테이블
CREATE TABLE notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,                   -- 수신자
    senior_id BIGINT NULL,                     -- 보호자 알림 시 대상 시니어
    category VARCHAR(32) NOT NULL,             -- MEDICATION_REMINDER 등 enum
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    payload JSON NULL,                         -- scheduleId, prescriptionId 등 메타
    target_date DATE NULL,                     -- 멱등 자연키 일부
    meal_slot VARCHAR(16) NULL,                -- 멱등 자연키 일부
    schedule_id BIGINT NULL,                   -- 멱등 자연키 일부
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_user_created (user_id, created_at DESC),
    UNIQUE KEY uk_dedup (user_id, category, senior_id, target_date, meal_slot, schedule_id)
);

-- notification_settings 테이블 (보호자 ↔ 시니어별)
CREATE TABLE notification_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    caregiver_id BIGINT NOT NULL,
    senior_id BIGINT NOT NULL,
    mode VARCHAR(16) NOT NULL DEFAULT 'STANDARD',  -- 'STANDARD' | 'INTENSIVE' | 'CUSTOM'. 항목 직접 수정 시 자동 'CUSTOM'으로 전환
    dur_warning_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    medication_delay_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    medication_delay_threshold_minutes INT NOT NULL DEFAULT 60,  -- STANDARD 프리셋 default와 일치
    family_safety_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    family_safety_threshold_hours INT NOT NULL DEFAULT 48,        -- STANDARD 프리셋 default와 일치
    medication_complete_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_caregiver_senior (caregiver_id, senior_id)
);

-- device_tokens 이전 (이미 있음, 스키마 변경 없음 — 패키지만 이동)
-- users 테이블 컬럼 추가
ALTER TABLE users ADD COLUMN last_active_at DATETIME NULL;
```

**프리셋 default 값** (스펙 합의 후 결정 — 후보):

| 항목 | STANDARD (일반) | INTENSIVE (집중) |
|---|---|---|
| dur_warning | ON | ON |
| medication_delay | ON, 60분 | ON, 30분 |
| family_safety | ON, 48시간 | ON, 12시간 |
| medication_complete | OFF | ON |

→ **오픈 질문 Q2**.

## 6) 작업 분할 (예상 PR 리스트)

순서는 의존성 기반. 각 PR은 독립적으로 동작/테스트 가능해야 함.

- [ ] **PR 1: spec 초안 머지** (this PR, `type:docs`, `scope:medication`)
- [ ] **PR 2: 박도현 PR refactor + notification_settings 신설** (호환성 깨짐, FE cut-over 필요) — `users.notification_mode` DROP 마이그레이션 + `NotificationMode` enum 이전(`com.ppiyaki.user`→`com.ppiyaki.notification`)/리네임(`STANDARD`/`INTENSIVE`) + `notification_settings` 테이블 신설 + `OnboardingRequest`에서 `notificationMode` 필드 제거 + `OnboardingService` 리팩터 (default settings row 자동 생성) + `docs/features/onboarding.md` spec 갱신. scope: `user`. **라벨**: `type:refactor`, `scope:user`, `needs-human-review` (DB 마이그레이션 + 박도현 코드 변경).
- [ ] **PR 3: 알림함 영속화 기반** — `Notification` 엔티티 + `notifications` 테이블 + `GET /notifications` + 읽음 처리 API.
- [ ] **PR 4: device token API + FCM 어댑터** — 기존 `DeviceToken` 패키지 이동 + 등록/해제 API + `FcmPushSender` (실 발송 또는 stub).
- [ ] **PR 5: 알림 설정 조회/갱신/프리셋 API** — PR 2의 테이블 위에 보호자 측 조회/PUT/preset 적용 API.
- [ ] **PR 6: 시니어 복약 시간 알림** — `MedicationReminderScheduler` + 트리거 + 자연키 멱등.
- [ ] **PR 7: 보호자 미복약/지연 알림** — `MedicationDelayChecker` + 보호자별 임계 적용. 같은 PR에서 `DashboardService.DELAY_THRESHOLD_MINUTES` 하드코딩 제거 → settings 참조로 통합 (caregiver-dashboard.md §8 Q5 해소).
- [ ] **PR 8: 보호자 DUR 긴급 경고** — `DurCheckService` 연동. 처방전 등록 시 즉시 트리거.
- [ ] **PR 9: 가족 안전망 알림 + last_active_at** — `users.last_active_at` 컬럼 + 시니어 인증 모든 요청에 `HandlerInterceptor` 1분 throttle 갱신 + `FamilySafetyChecker` cron + 재접속까지 1회 멱등.
- [ ] **PR 10: 보호자 복약 완료 알림** — `MedicationLog` 업서트 후 day-complete 검사 + 멱등 발송.

## 7) 테스트 전략

- **단위**: 각 발송 로직 (`*Service.send*`) — 자연키 멱등, 설정 toggle, 임계 임곗값 (boundary).
- **통합**: cron scheduler가 올바른 schedule을 픽업하는지. JPA 쿼리 정확성.
- **E2E (RestAssured)**: 신규 endpoint별 성공 케이스 1개 + 권한 (CARE_RELATION_NOT_FOUND) 1개.
- **FCM mock**: 실제 FCM 호출 안 함. `PushSender` 인터페이스를 mock하고 호출 인자/횟수 검증.
- **Cron 테스트**: `@SpringBootTest` + `@MockBean` cron clock — 시간 조작.
- **e2e 테스트 시 alarm 발송 확인**: notifications row 생성 + push 호출 인자.

## 8) 오픈 질문

> 모든 질문 2026-05-10 해소 (§9 참조). 새 질문 발생 시 추가.

| # | 질문 | 결정 |
|---|---|---|
| ~~Q1~~ | enum 이름 | `STANDARD` / `INTENSIVE` / `CUSTOM` ✅ |
| ~~Q2~~ | 프리셋 default | §5-5 후보표 그대로 ✅ |
| ~~Q3~~ | 시니어 본인 알림 설정 접근 | 보호자 전용 (시니어 측 설정 페이지 없음) ✅ |
| ~~Q4~~ | FCM Service Account | 사용자 직접 즉시 발급 ✅ |
| ~~Q5~~ | `last_active_at` 갱신 범위 | 시니어 인증 모든 요청 + 1분 throttle ✅ |
| ~~Q6~~ | 직접 수정 시 mode 컬럼 | `CUSTOM`으로 자동 전환 ✅ |
| ~~Q7~~ | 푸시 ↔ row 관계 | 항상 1:1 (5종 모두 알림함 표시) ✅ |
| ~~Q8~~ | 가족 안전망 cooldown | 시니어 재접속까지 1회만 ✅ |
| ~~Q9~~ | 알림 발송 순서 | (a) DB → push, `@TransactionalEventListener(AFTER_COMMIT)` ✅ |

## 9) 결정 로그

- 2026-05-09: 초안 작성 (status=draft). 옛 노션 명세 5건 archive 후 새 spec으로 재시작. 박도현 → 사용자 담당 인계. Figma 화면 4개(시니어 알림함 191:1236, 보호자 알림함 204:1553, 온보딩 99:3288, 보호자 알림 설정 203:1283) 확인 후 1차 설계.
- 2026-05-09: NotificationMode를 단일 enum이 아닌 **프리셋 (settings 일괄 적용용)** 으로 결정. 이유 — Figma 알림 설정 페이지가 항목별 toggle + 임계 시간 드롭다운으로 세분화되어 있어 단일 enum 2값으로 표현 불가. 사용자 합의.
- 2026-05-10: **Q1 해소** — 프리셋 enum 이름을 `STANDARD` / `INTENSIVE`로 채택. 이유 — 기존 `CareMode`(`MANAGED`/`AUTONOMOUS`)와 "CARE" 단어 충돌 회피. 사용자가 가용 코드 `BASIC_ALERT`/`INTENSIVE_CARE`로 짠 부분이 있다면 이번 PR 단계에서 리네임.
- 2026-05-10: **Q2 해소** — §5-5 프리셋 default 후보표를 그대로 채택 (STANDARD: medication_delay 60분, family_safety 48시간, medication_complete OFF / INTENSIVE: 30분, 12시간, ON). 이유 — Figma 디자인에서 default 값을 명시적으로 유추할 수 없었음. 운영 데이터 수집 후 조정 가능.
- 2026-05-10: **Q4 해소** — FCM Service Account는 사용자 본인이 즉시 발급. 환경변수 키: `FCM_PROJECT_ID`, `FCM_CREDENTIALS_JSON_BASE64`. application.yml + backend-cd.yml `docker run -e` 동기화 필수 (피야키 CD 환경변수 룰).
- 2026-05-10: **Q9 해소** — 알림 발송 순서는 (a) DB row 저장 → push 채택. 구현은 `@TransactionalEventListener(phase = AFTER_COMMIT)` 패턴 — 트랜잭션 커밋 후 push 호출. 이유: ① 알림함(인앱 row)이 진실이고 push는 best-effort, ② push 외부 의존도 높아 (b)는 인앱 누락 위험, ③ ppiyaki 운영 규모(시니어 수십 명)에서 (c) outbox 패턴은 over-engineering, ④ 자연키 멱등(`uk_dedup`)으로 push retry 시 row 중복 방지.
- 2026-05-10: **모델 결정 — 옵션 B (보호자 ↔ 시니어 N:M `notification_settings` 테이블) 채택**. develop이 14커밋 진행되며 박도현 PR이 `users.notification_mode` 컬럼 + `OnboardingRequest.notificationMode` 필드 + `NotificationMode` enum (`com.ppiyaki.user` 위치)을 머지한 사실 확인 → 본 spec과 충돌. refactor 필요. 이유: ① Figma 보호자 알림 설정(203:1283)은 시니어별 보호자 측 조정 화면 + 항목별 toggle/임계 시간 드롭다운 → 단일 enum 2값으로 표현 불가, ② 같은 시니어를 여러 보호자가 다른 설정으로 케어할 시나리오 대응, ③ DELAY_THRESHOLD_MINUTES 등 임계값을 보호자 측 설정으로 일원화 (caregiver-dashboard.md §8 Q5 자연 해소). enum은 `STANDARD`/`INTENSIVE`로 재명명 (CareMode와 단어 충돌 회피) + `com.ppiyaki.notification.NotificationMode`로 위치 이전. Onboarding API는 `notificationMode` 필드 제거 + default `STANDARD` 프리셋으로 settings row 자동 생성. `docs/features/onboarding.md` spec 동시 갱신. 사용자 합의.
- 2026-05-10: **Q3 해소** — 시니어 본인 알림 설정 접근 = 보호자 전용. 이유: ① Figma에 시니어 측 설정 화면 없음, ② N:M 모델에서 시니어 read 의미 모호, ③ 본 기능 목적상 시니어가 자기 복약 알림 끄는 것은 모순적, ④ 필요 시 별도 spec으로. `MEDICATION_REMINDER`는 default ON 강제.
- 2026-05-10: **Q5 해소** — `last_active_at` 갱신 = 시니어 인증된 모든 API 요청 + 1분 throttle. 이유: ppiyaki 운영 규모에서 매 요청 update 부담 없음, endpoint 분류 비용 vs 효익 작음, throttle로 동일 분 내 중복 write 방지.
- 2026-05-10: **Q6 해소** — settings 항목 직접 수정 시 `mode` = `CUSTOM` 자동 전환. enum에 `CUSTOM` 추가 (`STANDARD` / `INTENSIVE` / `CUSTOM`). 이유: UI 표시와 실제 값 일치(정직), 서버 로직 단순, 프리셋 재적용은 별도 endpoint이라 자연스럽게 mode 복귀.
- 2026-05-10: **Q7 해소** — 푸시 ↔ 알림함 row 항상 1:1. 이유: Figma 5종(시니어 복약시간 / 보호자 미복약·DUR·가족안전망·복약완료) 모두 알림함 표시 의도, 단순/일관성, 푸시 누락 보완, ppiyaki 규모에서 부하 무시 가능.
- 2026-05-10: **Q8 해소** — 가족 안전망 알림 cooldown = 시니어 재접속까지 1회만. 구현은 `last_active_at` 갱신 시점 이후의 가장 최근 `FAMILY_SAFETY` row 존재 여부로 판정. 이유: 알림 피로 방지, 보호자가 한 번 인지하면 후속은 본인 책임, 구현 단순 (시니어 재접속 시 자연 reset).
- 2026-05-10: 모든 오픈 질문 해소 → status `draft` → `approved`. 다음 단계: PR 머지 후 PR 2(refactor + notification_settings) 착수.
