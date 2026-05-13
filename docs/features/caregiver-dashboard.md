---
feature: 보호자 대시보드 (일간/주간/월간 복약 현황)
slug: caregiver-dashboard
status: draft
owner: @goohong
scope: medication
related_issues: []
related_prs: []
last_reviewed: 2026-05-08
---

# 보호자 대시보드 (일간/주간/월간 복약 현황)

## 1) 개요 (What / Why)
보호자 앱의 "복약 현황" 화면에서 시니어의 복약 인증 상태를 일/주/월 단위로 볼 수 있게 한다. 보호자가 시니어 상태를 한눈에 파악하고 누락/지연 시 빠르게 인지할 수 있도록 통합 조회 API 3개를 제공한다.

**대상 액터**: 보호자(care_relations 활성), 시니어 본인.

**해결 문제**: 현재는 보호자가 medication-logs / medicines / schedules를 별도로 호출해 합성해야 함. 일자별 상태 색상(초록/노랑/빨강) 같은 비즈니스 룰도 클라이언트에서 계산. → 통합 endpoint로 응답 + status 도출 일관성 확보.

## 2) 사용자 시나리오
- **일간 진입**: 보호자가 앱 첫 화면에서 오늘 시니어가 아침/점심/저녁 약을 인증했는지, 어떤 약이 어느 슬롯에 처방됐는지 확인. 인증 사진도 본다.
- **주간 점검**: 일주일 단위로 어떤 날이 빨강(누락)/노랑(지연)/초록(정시)인지 보고, 빨강 일자 클릭 → 일간 화면으로 이동.
- **월간 트렌드**: 캘린더에서 한 달간 복약 패턴을 색으로 본다. 일자 클릭 → 일간 화면.

## 3) 요구사항
### 기능 요구사항
- [ ] `GET /api/v1/seniors/{seniorId}/dashboard/daily?date=YYYY-MM-DD` — 일간 화면용 통합 응답
  - 헤더 정보(시니어 이름, 보호자 이름, 남은 복약일 수)
  - 슬롯별(BREAKFAST/LUNCH/DINNER) 인증 상태(`PERFECT`/`DELAYED`/`MISSED`/`PENDING`/`NOT_SCHEDULED`) + 인증 사진 URL + 인증 시각
  - 슬롯별 복약 대상 약 목록 (medicine + dosage)
  - 일자별 통합 status enum
- [ ] `GET /api/v1/seniors/{seniorId}/dashboard/weekly?weekStart=YYYY-MM-DD` — 주간 화면용
  - 주간 이행률(%) — 정시/지연 인증 슬롯 / 총 슬롯
  - 일자별(7일) 통합 status enum + 슬롯별 마커
- [ ] `GET /api/v1/seniors/{seniorId}/dashboard/monthly?yearMonth=YYYY-MM` — 월간 캘린더용
  - 일자별(해당 월 1일~말일) 통합 status enum
  - 상세 medicine 정보 미포함 (필요 시 daily 별도 호출)
- [ ] 권한: 시니어 본인 + 활성 `care_relations` 보호자 (`PrescriptionService.validateAccess` 패턴)
- [ ] 미존재 시니어는 `USER_NOT_FOUND`, 권한 없으면 `CARE_RELATION_NOT_FOUND`

### 비기능 요구사항
- **성능**:
  - daily: schedule + log + medicine 조인. 단일 시니어/단일 일자라 row 수 작음(<100). 쿼리 ≤ 50ms 목표.
  - weekly: 7일치 → 단일 쿼리 + 메모리 그룹핑.
  - monthly: 31일치 status만 → 단일 쿼리 + 메모리 그룹핑. medicine 정보 제외로 응답 < 5KB.
- **신뢰성**: status 룰 동적 계산(log row 미변경)이므로 멱등.
- **관측성**: dashboard 호출 INFO 로그 1줄(`/dashboard/daily seniorId=… date=…`).

## 4) 범위 / 비범위
### 포함
- daily / weekly / monthly 조회 endpoint 3개
- status enum 동적 계산 룰
- 일일 소요량 + 남은 복약일 수 계산
- 권한 검증 재사용
- 단위 + E2E 테스트

### 제외 (Out of Scope)
- **MISSED 자동 전환 cron**: log row를 PENDING → MISSED로 batch 전환하는 별도 작업. 본 spec은 dashboard 응답 시 동적 계산만.
- **푸시 알림 / 보호자 알림**: 빨강 발생 시 알림 발송은 별도 spec.
- **이행률 통계 추세**: 월간 이행률 그래프 / 추세 분석은 별도.
- **일간 리포트 PDF / 공유**: 본 spec은 조회 API만.
- **시니어 다중 보호자 통합 대시보드**: 보호자 1명이 여러 시니어 보는 화면 별도.
- **schedule 변경 이력**: 과거 schedule이 변경된 경우 과거 일자 status 재계산 룰 — 현재 schedule만으로 계산 (단순화).

## 5) 설계
### 5-1) 도메인 모델
재사용:
- `MedicationLog (status, taken_at, photo_object_key, ...)` — 인증 row
- `MedicationSchedule (medicine_id, meal_slot, dosage, days_of_week, start_date, end_date)` — 처방 일정
- `Medicine (name, total_amount, remaining_amount)` — 약
- `User (breakfast_time, lunch_time, dinner_time, nickname)` — 시니어 mealTimes
- `CareRelation (senior_id, caregiver_id, deleted_at)` — 보호자 관계

신규 entity 없음.

### 5-2) status 도출 룰

#### 슬롯 단위 status (`SlotStatus`)
| 값 | 룰 |
|---|---|
| `PERFECT` | log.status=TAKEN AND log.taken_at - mealTime ≤ 1시간 |
| `DELAYED` | log.status=TAKEN AND log.taken_at - mealTime > 1시간 |
| `MISSED` | log row 없음 또는 log.status=MISSED, **AND** 해당 일자 자정 경과 |
| `PENDING` | log row 없음 또는 log.status=PENDING/MISSED, **AND** 해당 일자 자정 미경과 (오늘) |
| `NOT_SCHEDULED` | 해당 슬롯에 schedule 없음 (mealTime null 또는 schedule 미할당) |

비고:
- `taken_at - mealTime`는 timezone Asia/Seoul (v0.9.7 후) 기준. 음수(정시 전 인증)는 `PERFECT`로 간주.
- mealTime이 null이면 그 슬롯은 `NOT_SCHEDULED` (status 룰 X). 이 슬롯은 이행률 분모에서 제외.

#### 일자 단위 status (`DayStatus`)
일자 안의 모든 슬롯을 보고 1개 status로 축약:
| 값 | 룰 |
|---|---|
| `PERFECT` | 1개 이상 schedule된 슬롯이 있고 모두 PERFECT |
| `DELAYED` | DELAYED 슬롯 1개 이상, MISSED 0 |
| `MISSED` | MISSED 슬롯 1개 이상 (자정 경과 후만 발생) |
| `PENDING` | PENDING 슬롯 1개 이상, MISSED 0, DELAYED 0 (오늘 진행 중) |
| `FUTURE` | date > today |
| `NOT_SCHEDULED` | 가입 이전 날짜(#326) **또는** 모든 슬롯이 `NOT_SCHEDULED`인 일자(#340) |

비고:
- "오늘"의 status는 frontend isToday 플래그로 추가 강조 (디자인 image2의 25일). backend는 룰 그대로.
- 모든 슬롯이 `NOT_SCHEDULED`(mealTimes null 또는 schedule 없음)인 일자는 `NOT_SCHEDULED`로 표시 — 슬롯/일자 의미가 일관되도록 §8 Q1 (c) 채택 (#340, 2026-05-13).

### 5-3) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| GET | `/api/v1/seniors/{seniorId}/dashboard/daily` | 일간 통합 | 본인 또는 활성 보호자 | query: `date` (LocalDate) | `DailyDashboardResponse` |
| GET | `/api/v1/seniors/{seniorId}/dashboard/weekly` | 주간 통합 | 동일 | query: `weekStart` (LocalDate, 일요일) | `WeeklyDashboardResponse` |
| GET | `/api/v1/seniors/{seniorId}/dashboard/monthly` | 월간 통합 | 동일 | query: `yearMonth` (YYYY-MM) | `MonthlyDashboardResponse` |

#### DailyDashboardResponse (의사 코드)
```json
{
  "seniorId": 16,
  "date": "2026-05-08",
  "dayStatus": "DELAYED",
  "header": {
    "seniorName": "김장군",
    "caregiverName": "김철수",
    "remainingDays": 3
  },
  "slots": [
    {
      "slot": "BREAKFAST",
      "status": "PERFECT",
      "mealTime": "08:00",
      "takenAt": "2026-05-08T07:55:00",
      "photoUrl": "https://kr.object.ncloudstorage.com/.../breakfast.jpg",
      "medicines": [
        {"medicineId": 12, "name": "지스로맥스정250mg", "dosage": "1정"},
        {"medicineId": 13, "name": "엘도신캡슐", "dosage": "1정"}
      ]
    },
    { "slot": "LUNCH", "status": "PERFECT", ... },
    { "slot": "DINNER", "status": "PENDING", "takenAt": null, "photoUrl": null, "medicines": [...] }
  ],
  "medicines": [
    {"medicineId": 12, "name": "지스로맥스정250mg", "slots": ["BREAKFAST"]},
    {"medicineId": 14, "name": "코슈정", "slots": ["BREAKFAST", "LUNCH", "DINNER"]}
  ]
}
```

#### WeeklyDashboardResponse
```json
{
  "seniorId": 16,
  "weekStart": "2026-05-04",
  "weekEnd": "2026-05-10",
  "adherenceRate": 75.0,
  "days": [
    {
      "date": "2026-05-04",
      "dayStatus": "PERFECT",
      "slots": [
        {"slot": "BREAKFAST", "status": "PERFECT"},
        {"slot": "LUNCH", "status": "PERFECT"},
        {"slot": "DINNER", "status": "PERFECT"}
      ]
    },
    ...
    { "date": "2026-05-10", "dayStatus": "FUTURE", "slots": [] }
  ]
}
```

#### MonthlyDashboardResponse
```json
{
  "seniorId": 16,
  "yearMonth": "2026-05",
  "days": [
    { "date": "2026-05-01", "dayStatus": "PERFECT" },
    { "date": "2026-05-02", "dayStatus": "DELAYED" },
    ...
    { "date": "2026-05-31", "dayStatus": "FUTURE" }
  ]
}
```

### 5-4) 남은 복약일 수 계산
`DailyDashboardResponse.header.remainingDays`:

```
medicineDays(m) = floor(m.remainingAmount / dailyConsumption(m))
dailyConsumption(m) = sum(parseInt(s.dosage)) for s in active schedules of m
remainingDays = MIN(medicineDays(m)) for all medicines of senior with dailyConsumption > 0
```

규칙:
- `s.dosage`에서 정수 파싱 — `"1정"` → 1, `"2정"` → 2, `"반정"` → 0(소수 미지원, §8 Q2)
- `dailyConsumption(m) = 0`이면 그 medicine은 분자에서 제외 (남은 일수 무한)
- 전체 medicines가 dailyConsumption=0이면 `remainingDays=null` (계산 불가)
- 미래 schedule(start_date > today)은 제외, 종료 schedule(end_date < today) 제외

### 5-5) 데이터 흐름 (daily)
1. 권한 검증: seniorId가 userId 본인이거나 활성 care_relations
2. 시니어 + mealTimes 조회 (`User`)
3. 보호자 닉네임 조회 (요청자)
4. 해당 일자 active medication_schedules 조회 (start_date ≤ date ≤ end_date)
5. 해당 일자 medication_logs 조회 (`schedule_id IN (...)`, `target_date = date`)
6. 약 목록 조회 (`medicineId IN (...)`)
7. 슬롯별 status 룰 적용 + medicines 슬롯 매핑
8. 잔여분 기반 remainingDays 계산
9. DailyDashboardResponse 조립

weekly/monthly는 4~6 단계를 기간으로 확장, status만 도출.

### 5-6) 코드 영향 범위
| 위치 | 변경 |
|---|---|
| `medication/controller/DashboardController.java` | **신규** — 3개 endpoint |
| `medication/service/DashboardService.java` | **신규** — daily/weekly/monthly 도출 로직 |
| `medication/controller/dto/DailyDashboardResponse.java` | **신규** |
| `medication/controller/dto/WeeklyDashboardResponse.java` | **신규** |
| `medication/controller/dto/MonthlyDashboardResponse.java` | **신규** |
| `medication/SlotStatus.java` / `DayStatus.java` | **신규 enum** |
| `medication/repository/MedicationLogRepository.java` | findByScheduleIdsAndTargetDateBetween 추가 |
| `medication/repository/MedicationScheduleRepository.java` | findActiveByOwnerAndDateRange 추가 |
| `prescription/service/PrescriptionService.java` | validateAccess 추출(공용 util)? — 옵션 |

## 6) 작업 분할 (예상 PR 리스트)
- [ ] **PR 1: daily endpoint** — `GET /seniors/{id}/dashboard/daily` + DailyDashboardResponse + slot status 룰. 단위 + E2E 테스트.
- [ ] **PR 2: weekly endpoint** — `GET /seniors/{id}/dashboard/weekly` + 이행률 + 일자별 slots. 단위 + E2E.
- [ ] **PR 3: monthly endpoint** — `GET /seniors/{id}/dashboard/monthly` + 캘린더용. 단위 + E2E.
- (optional) PR 4: 공용 권한 검증 util 추출 (PrescriptionService.validateAccess와 통합).

각 PR scope=`medication`, type=`feat`, ai-generated 라벨. Notion API 명세 동기화 필수.

## 7) 테스트 전략
- **단위(DashboardService)**: status 룰 분기 — PERFECT/DELAYED/MISSED/PENDING/FUTURE/NOT_SCHEDULED 모든 케이스. mealTime null 처리. dayStatus 축약 룰. remainingDays 계산.
- **E2E**: RestAssured + 시니어/보호자 토큰 + medication_log seed → 응답 status/slots/medicines 검증. 권한 분기(보호자 외 접근 → 401/403). seniorId 미존재 → USER_NOT_FOUND.
- 외부 연동 없음 — mock 불필요.

## 8) 오픈 질문
| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| ~~Q1~~ | ~~시니어 mealTimes 미설정 일자(모든 슬롯 NOT_SCHEDULED)의 dayStatus~~ | **해소 — (c) `NOT_SCHEDULED` 채택 (#340, 2026-05-13). 가입 전 #326 처리와 일관된 의미.** ✅ |
| Q2 | dosage 파싱 — "반정"(0.5정) / "2정 반"(2.5정) 같은 소수 표기 | (a) 정수만 — 비정수면 0으로 (단순) / (b) BigDecimal로 정밀 / (c) 파싱 실패 시 fallback dailyConsumption=1 | @goohong / 구현 시 |
| Q3 | weekly의 weekStart 요일 | (a) 일요일 (한국 캘린더 기본) / (b) 월요일 (ISO 8601) — 디자인 image2 "일/월/화/수…" 순이라 (a)로 보임 | 디자인 측 / 구현 시 |
| Q4 | monthly 응답에 통합 status 외 추가 정보(예: 슬롯별 마커) | (a) 통합 status enum만(권장) / (b) 슬롯별 마커도 — 응답 크기 증가 | @frontend / 구현 시 |
| ~~Q5~~ | ~~"지연" 임계값 1시간~~ | **해소 — 보호자별 `notification_settings.medication_delay_threshold_minutes` 참조 (default 60분, 시니어 본인 caller일 때만 60 fallback)** ✅ |

## 9) 결정 로그
- 2026-05-08: spec 초안 작성 (status=draft).
- 2026-05-08: status enum = PERFECT/DELAYED/MISSED/PENDING/FUTURE + 슬롯 단위 NOT_SCHEDULED. 색깔 매핑은 frontend 책임. 사용자 결정.
- 2026-05-08: DELAYED 임계값 = mealTime 대비 1시간. 사용자 결정.
- 2026-05-08: MISSED 기준 = 자정까지 미인증. 사용자 결정.
- 2026-05-08: 오늘 일자 처리 = 기존 룰 그대로(PENDING). "오늘 강조"는 frontend isToday 플래그. 사용자 결정.
- 2026-05-08: remainingDays = MIN(remainingAmount / dailyConsumption). 잔여분 자동 차감(v0.9.8) + confirm amount 입력(PR #263)에 의존.
- 2026-05-08: MISSED 자동 전환 cron은 본 spec scope 외 — dashboard 응답 시 동적 계산.
- 2026-05-10 (#294): **Q5 해소** — DELAYED 임계 = 보호자별 `notification_settings.medication_delay_threshold_minutes` 참조. `DashboardService.DELAY_THRESHOLD_MINUTES = 60` 하드코딩 제거 → caller 보호자의 settings 조회. 시니어 본인 caller(userId == seniorId)일 때는 default 60 fallback. medication-notification.md PR 7과 동시 머지.
- 2026-05-08: 통합 endpoint 신규(daily/weekly/monthly) — 기존 logs/medicines/schedules 합성보다 N+1 호출 부담 감소 + status 룰 일관성.
- 2026-05-13 (#340): **Q1 해소** — 모든 슬롯이 NOT_SCHEDULED인 일자의 dayStatus를 PERFECT가 아닌 NOT_SCHEDULED로 통일. 기존 #326의 가입 전 처리와 의미적 일관성 확보. `DashboardService.deriveDayStatus(FromMarkers)` 빈 `present` 분기 PERFECT → NOT_SCHEDULED.
