---
feature: 복약 일정 식사 슬롯 모델
slug: medication-schedule-meal-slot
status: done
owner: @goohong
scope: medication
related_issues: [#225]
related_prs: [#225]
last_reviewed: 2026-05-07
---

# 복약 일정 식사 슬롯 모델 (meal-time-defaults Phase 2)

## 1) 개요 (What / Why)
복약 일정(`MedicationSchedule`)이 보관하는 시각을 절대 시각(`scheduled_time LocalTime`)에서 식사 슬롯(`meal_slot ENUM`)으로 전환한다. 실제 알림/표시 시각은 시니어의 `breakfastTime/lunchTime/dinnerTime`(Phase 1에서 도입)을 매번 참조해 동적으로 계산한다.

이 모델은 **변경 cascade가 필요 없다** — schedule이 슬롯만 보유하므로 시니어가 식사 시간을 바꾸면 자동으로 모든 약 알림 시각이 따라온다. Phase 2의 본질은 "초기 등록 시 사용자별 식사 시간을 자연스럽게 반영"이다.

대상 사용자: 시니어(약 schedule이 본인 식사 시간 따라 동작), 보호자(시니어 약 등록 시 슬롯 입력).

## 2) 사용자 시나리오
- **약 등록**: 보호자/시니어가 약 등록 화면에서 "점심" 슬롯을 선택해 schedule을 만든다. 백엔드는 `mealSlot=LUNCH`로 저장한다. 응답에는 시니어의 lunchTime을 join해 `scheduledTime=12:30:00`도 함께 내려간다.
- **시니어 식사 시간 변경**: 시니어가 lunchTime을 12:30 → 13:00으로 갱신. 별도 cascade API 호출 없이, 다음 schedule 조회/알림 발송부터 13:00으로 계산된다.
- **mealTimes 미설정 상태에서 약 등록 시도**: `MEAL_TIMES_NOT_SET` 400 에러로 거절. 클라이언트는 mealTimes 설정 화면으로 유도.

## 3) 요구사항
### 기능 요구사항
- [x] `MealSlot` enum 추가: `BREAKFAST` / `LUNCH` / `DINNER`
- [x] `MedicationSchedule.scheduled_time` 컬럼 제거, `meal_slot VARCHAR NOT NULL` 컬럼 추가 (Java enum + AttributeConverter 또는 EnumType.STRING)
- [x] `ScheduleCreateRequest`/`ScheduleUpdateRequest`: `scheduledTime LocalTime` → `mealSlot MealSlot` 치환. 검증: `@NotNull MealSlot`, jackson enum binding으로 잘못된 값 시 400
- [x] `ScheduleResponse`: `mealSlot` + 동적 계산된 `scheduledTime` 둘 다 포함. service 레이어에서 owner의 mealTimes 한 번 join해 매핑
- [x] `POST /api/v1/medicines/{medicineId}/schedules`: 시니어(=`medicine.owner_id`)의 해당 슬롯 mealTime이 NULL이면 `MEAL_TIMES_NOT_SET` 400
- [x] `PATCH /api/v1/medicines/{medicineId}/schedules/{scheduleId}`: 동일 검증
- [x] `MedicationScheduleRepository.findActiveByOwnerAndScheduledTime` → `findActiveByOwnerAndMealSlot`으로 변경. medication-log-phase2 약 개수 검증 흐름 갱신
- [x] MCP tool `get_today_medication_schedule`: `scheduledTime` → 시니어 mealTimes로 동적 변환해 반환 (LLM 응답 포맷 유지)
- [x] 기존 `medication_schedules` 데이터 전부 DROP (마이그레이션 SQL에 포함)
- [x] `ErrorCode.MEAL_TIMES_NOT_SET` 추가: HTTP 400, code `USER_002`
- [x] 도메인 문서 §4 유비쿼터스 랭귀지에 `식사 슬롯 / Meal Slot` 등재
- [x] 도메인 문서 §5 `medication_schedules` 갱신 (scheduled_time 제거, meal_slot 추가)
- [x] 도메인 문서 §6 ERD 갱신
- [x] `schema.sql` 동기화
- [x] Notion API 명세 동기화: schedule POST/PATCH/GET 응답 변경

### 비기능 요구사항
- **성능**: schedule 조회 시 owner의 mealTimes를 가져오기 위한 join 1회 추가. medicine→user 1-hop이라 비용 작음. n+1 방지 위해 service 레이어에서 owner 1회 fetch 후 schedule 리스트 매핑.
- **신뢰성**: 슬롯 enum은 DB varchar + Java enum (기존 패턴 일관). 잘못된 값은 jackson 단계에서 400.
- **보안**: 의료정보 아니므로 일반 데이터 정책. 슬롯 정보 로그 노출 OK.
- **관측성**: 별도 메트릭 불필요.

## 4) 범위 / 비범위 (중요)

### 포함
- `MedicationSchedule` 모델 변경 (scheduled_time 제거, meal_slot 추가)
- 모든 schedule CRUD API의 입력/출력 변경
- `medication-log-phase2` 약 개수 검증 쿼리 갱신
- MCP tool 응답 포맷 유지(슬롯→시각 변환은 백엔드)
- 기존 데이터 DROP
- 도메인 문서 + Notion API 명세 동기화
- E2E 테스트 (성공 + mealTimes 미설정 400 케이스)

### 제외 (Out of Scope)
- **시니어 mealTimes 변경 시 cascade API** — 동적 계산 모델이라 불필요
- **알림 발송 큐(`MedicationReminder`) 시각 계산** — 현재 미구현. 알림 인프라 도입 시점에 결정 (enqueue 시점 stamp vs 발송 시점 동적)
- **OCR schedule 텍스트 → 슬롯 자동 매핑** → Phase 3 (별도 spec)
- **처방전 confirm 시 schedule 자동 생성** → Phase 3
- **mealTimes 부분 설정** — Phase 1 결정 유지(전체 설정 또는 전체 미설정만)
- **회원가입 시 mealTimes 기본값 자동 채움** — 후속 검토 (사용자 메모: "초기에 기본값이 채워지게 디자인할 것 같음")
- **시니어 권한 강제** — 보호자 mealTimes 의미 없지만 별도 검증 안 함. 권한 도입(role 기반) 후 처리
- **식사와 무관한 약**(자기 전, 공복 등) — 모든 schedule은 식사 슬롯에 묶이는 모델 채택. 향후 필요 시 별도 슬롯 enum 확장(`BEDTIME` 등) 또는 fixed_time 모델 도입 검토
- **기존 데이터 슬롯 자동 매핑 backfill** — DROP 채택

## 5) 설계

### 5-1) 도메인 모델
- 컨텍스트: `medication`
- `MedicationSchedule.scheduled_time` (LocalTime) **제거**
- `MedicationSchedule.meal_slot` (varchar, Java `MealSlot` enum) **추가, NOT NULL**
- `MealSlot { BREAKFAST, LUNCH, DINNER }` — `com.ppiyaki.medication.MealSlot`
- `User.breakfastTime/lunchTime/dinnerTime` (Phase 1) 활용. 변경 없음.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | `/api/v1/medicines/{medicineId}/schedules` | 슬롯 기반 schedule 등록 | 필수 | `ScheduleCreateRequest` | `ScheduleResponse` |
| PATCH | `/api/v1/medicines/{medicineId}/schedules/{scheduleId}` | schedule 수정 (mealSlot 변경 가능) | 필수 | `ScheduleUpdateRequest` | `ScheduleResponse` |
| GET | `/api/v1/medicines/{medicineId}/schedules` | schedule 목록 (응답에 동적 시각 포함) | 필수 | — | `ScheduleListResponse` |
| GET | `/api/v1/medicines/{medicineId}/schedules/{scheduleId}` | 단건 조회 | 필수 | — | `ScheduleResponse` |
| DELETE | `/api/v1/medicines/{medicineId}/schedules/{scheduleId}` | 삭제 (변경 없음) | 필수 | — | — |

#### ScheduleCreateRequest
```json
{
  "mealSlot": "LUNCH",
  "dosage": "1정",
  "daysOfWeek": "DAILY",
  "startDate": "2026-05-07",
  "endDate": null
}
```
- `mealSlot`: `@NotNull MealSlot` (`BREAKFAST`/`LUNCH`/`DINNER` 외 400)
- `dosage`, `daysOfWeek`, `startDate`, `endDate`: 변경 없음

#### ScheduleResponse (확장)
```json
{
  "id": 42,
  "medicineId": 7,
  "mealSlot": "LUNCH",
  "scheduledTime": "12:30:00",
  "dosage": "1정",
  "daysOfWeek": "DAILY",
  "startDate": "2026-05-07",
  "endDate": null,
  "createdAt": "2026-05-07T10:15:00"
}
```
- `scheduledTime`: 시니어의 슬롯별 mealTime을 service에서 매핑. `mealSlot=LUNCH`이면 `user.lunchTime`.
- 매핑 시점에 mealTime이 NULL이면 schedule이 존재하지 말아야 하나(생성 시 검증), 안전을 위해 응답에선 `null`로 fallback.

#### 에러 응답
| 상황 | HTTP | code |
|---|---|---|
| `mealSlot` 누락/오타 | 400 | `COMMON_001` |
| 시니어 해당 슬롯 mealTime 미설정 상태에서 schedule 생성/수정 | 400 | `USER_002` (`MEAL_TIMES_NOT_SET`) |
| 인증 없음 | 401 | `AUTH_001` |
| 본인 약/연동 시니어 약 아님 | 403 | `CARE_001` |

### 5-3) 외부 연동
없음.

### 5-4) 데이터 흐름
```
클라이언트
  ↓ POST /medicines/{id}/schedules { mealSlot: LUNCH, dosage, ... }
MedicationScheduleController.create
  ↓
MedicationScheduleService.create(userId, medicineId, request)
  ├─ findMedicineAndValidateAccess
  ├─ Medicine.ownerId(=시니어) → User 조회
  ├─ user.lunchTime == null ⇒ throw MEAL_TIMES_NOT_SET
  └─ MedicationSchedule(meal_slot=LUNCH, dosage, ...) 저장
       ↓
ScheduleResponse.from(schedule, user)
  └─ scheduledTime = user.lunchTime
```

조회 흐름:
```
GET /medicines/{id}/schedules
  → service.readAll
    → Medicine 조회 → owner User 1회 조회 (mealTimes 포함)
    → schedules 리스트 조회
    → 각 schedule: mealSlot 보고 user.{slot}Time 매핑하여 ScheduleResponse 생성
```

### 5-5) DB 마이그레이션

```sql
-- 1) 기존 schedule 데이터 DROP (사용자 동의함, prod에 운영 데이터 없음 가정)
DELETE FROM medication_logs WHERE schedule_id IN (SELECT id FROM medication_schedules);
DELETE FROM medication_reminders WHERE schedule_id IN (SELECT id FROM medication_schedules);
DELETE FROM medication_schedules;

-- 2) 컬럼 교체
ALTER TABLE medication_schedules
    DROP COLUMN scheduled_time,
    ADD COLUMN meal_slot VARCHAR(16) NOT NULL;
```

- prod NCP MySQL에 머지 직전 수동 실행 (보호 영역 + DDL).
- `schema.sql`도 동일하게 갱신.
- `medication_logs` / `medication_reminders` FK 정리는 cascade 미설정 가정해 명시 DELETE.

### 5-6) 코드 영향 범위
| 파일 | 변경 |
|---|---|
| `medication/MealSlot.java` | **신규** enum |
| `medication/MedicationSchedule.java` | `scheduledTime` → `mealSlot`, 생성자/update 시그니처 |
| `medication/repository/MedicationScheduleRepository.java` | `findActiveByOwnerAndScheduledTime` → `findActiveByOwnerAndMealSlot` |
| `medication/service/MedicationScheduleService.java` | owner User join, mealTime 검증 |
| `medication/controller/dto/ScheduleCreateRequest.java` | `scheduledTime` → `mealSlot` |
| `medication/controller/dto/ScheduleUpdateRequest.java` | 동일 |
| `medication/controller/dto/ScheduleResponse.java` | `mealSlot` + 동적 `scheduledTime` |
| `medication/service/MedicationLogService.java` (Phase 2 약 개수 검증) | 슬롯 기준 기대 카운트 쿼리로 전환 |
| `common/mcp/MedicationMcpTools.java` | LLM 응답 시 슬롯→시각 변환 |
| `resources/schema.sql` | 컬럼 정의 변경 |
| `test/.../MedicationScheduleControllerE2ETest.java` | 입력/응답 갱신, 신규 400 케이스 |
| `test/.../MedicationLogControllerE2ETest.java` | 슬롯 기반 schedule fixture |
| `test/.../MedicationLogServicePhase2Test.java` | 동일 |
| `docs/ai-harness/06-domain-model.md` | §4 유비쿼터스 랭귀지, §5 medication_schedules, §6 ERD |
| `docs/features/medication-schedule-crud.md` | 슬롯 모델 반영 |
| `docs/features/medication-log-phase2.md` | §5-4 기대 카운트 쿼리 슬롯 매칭으로 |
| `docs/features/meal-time-defaults.md` | status=done 처리, "후속: medication-schedule-meal-slot.md" 링크 |

## 6) 작업 분할
단일 PR `feat(medication)`로 진행. 컬럼 변경 + 모든 호출처 동시 갱신이 분리 시 빌드 깨짐.

- [x] PR #225 `feat(medication)`:
  - `MealSlot` enum 신규
  - 엔티티/repository/service/controller/DTO 변경
  - MCP tool 갱신
  - 마이그레이션 SQL + schema.sql
  - 도메인 문서 §4/§5/§6 갱신
  - 관련 spec 갱신 (medication-schedule-crud, medication-log-phase2, meal-time-defaults)
  - Notion API 명세 동기화
  - E2E + 단위 테스트

## 7) 테스트 전략
- **단위 테스트**:
  - `MedicationSchedule` 생성자 — `mealSlot` null 거부
  - `ScheduleResponse.from` — 슬롯별 시각 매핑 (LUNCH → user.lunchTime)
- **E2E (RestAssured)**:
  - 성공: 시니어 mealTimes 설정 → POST schedule with LUNCH → 200, 응답에 `scheduledTime=lunchTime`
  - mealTimes 미설정 시니어가 POST → 400 `USER_002`
  - 잘못된 mealSlot 값 → 400
  - 시니어 mealTimes 갱신 후 GET schedule → 응답 시각이 새 시간 반영
- 외부 연동 mock 불필요

## 8) 오픈 질문
없음 — 합의 완료 (§9 참조).

## 9) 결정 로그
- 2026-05-07: 초안 작성 (status=draft). meal-time-defaults Phase 2를 별도 spec으로 분리.
- 2026-05-07: **schedule엔 슬롯만 저장, 시각은 동적 계산**. 시니어가 식사 시간 변경하면 자동 반영 → cascade API 불필요. 사용자 의도: "변경을 염두에 둔 설계가 아니라, 초기에 사람마다 다른 식사시간을 설정하기 위한 설계".
- 2026-05-07: **`scheduled_time` 컬럼 완전 제거**. 모든 schedule은 식사 슬롯에 묶임. 자기 전·공복약 등 비식사 약은 현재 범위 외.
- 2026-05-07: **기존 prod schedule 데이터 DROP**. 운영 데이터 없음 (테스트 데이터만).
- 2026-05-07: **mealTimes 미설정 시 schedule 등록 400**. 의도 명확. 회원가입 시 기본값 자동 채움은 후속 과제.
- 2026-05-07: **mealTimes는 전체 설정 또는 전체 미설정만**. Phase 1 spec §5-2 채택, §4 "일부 가능" 정정 대상.
- 2026-05-07: **응답에 `mealSlot` + 동적 `scheduledTime` 둘 다 포함**. 클라이언트 호출 1회로 화면 표시 가능. owner user 1회 join.
- 2026-05-07: **알림 큐 시각 계산은 본 spec 범위 외**. `MedicationReminder` 발송 로직 미구현 상태. 인프라 도입 시 결정.
