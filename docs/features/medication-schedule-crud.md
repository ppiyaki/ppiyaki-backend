---
feature: 복약 일정 등록/조회/수정/삭제
slug: medication-schedule-crud
status: draft
owner: @goohong
scope: medication
related_issues: []
related_prs: []
last_reviewed: 2026-04-11
---

# 복약 일정 등록/조회/수정/삭제

## 1) 개요 (What / Why)
- 약물(Medicine)에 대한 복약 일정(시간, 용량, 요일, 기간)을 등록·관리할 수 있도록 한다.
- 복약 일정은 알림(MedicationReminder), 복약 기록(MedicationLog)의 기반이 되므로 선행 구현이 필수이다.
- 하나의 약물에 여러 시간대의 일정을 등록할 수 있다 (예: 아침 8시 1정, 저녁 8시 1정).

## 2) 사용자 시나리오

### SC-1: 시니어가 약물에 복약 일정 등록
1. 시니어가 약 상세 화면에서 "일정 추가"를 탭한다.
2. 복용 시각, 1회 복용량, 요일 패턴, 시작일을 입력한다.
3. `POST /api/v1/medicines/{medicineId}/schedules`로 일정이 생성된다.

### SC-2: 시니어가 약물의 복약 일정 목록 확인
1. 약 상세 화면에서 해당 약물의 복약 일정 목록을 본다.
2. `GET /api/v1/medicines/{medicineId}/schedules`로 조회.

### SC-3: 시니어가 복약 일정 수정
1. 복용 시각이나 용량을 변경한다.
2. `PATCH /api/v1/medicines/{medicineId}/schedules/{scheduleId}`로 수정.

### SC-4: 시니어가 복약 일정 삭제
1. 더 이상 필요 없는 일정을 삭제한다.
2. `DELETE /api/v1/medicines/{medicineId}/schedules/{scheduleId}`로 삭제.

## 3) 요구사항

### 기능 요구사항
- [ ] 약물에 복약 일정을 등록할 수 있다 (scheduledTime, dosage 필수)
- [ ] 등록 시 medicineId가 존재하는지 검증
- [ ] 등록 시 해당 약물의 소유자(또는 연동된 보호자)인지 검증
- [ ] 약물별 복약 일정 목록 조회 (페이징 없이 전체 반환)
- [ ] 복약 일정 단건 상세 조회
- [ ] 복약 일정 수정 (scheduledTime, dosage, daysOfWeek, startDate, endDate)
- [ ] 복약 일정 삭제
- [ ] 소유자 또는 연동된 보호자가 아닌 사용자의 접근 시 403 응답

### 비기능 요구사항
- daysOfWeek는 "MON,TUE,WED" 또는 "DAILY" 형식의 문자열

## 4) 범위 / 비범위

### 포함
- MedicationSchedule CRUD 5개 엔드포인트
- 약물 소유자 기반 접근 검증 (medicine.ownerId + care_relations)
- MedicationScheduleRepository 확장
- MedicationScheduleService + MedicationScheduleController 구현
- E2E 테스트

### 제외 (Out of Scope)
- 복약 알림(MedicationReminder) 발송 (별도 feature)
- 복약 기록(MedicationLog) 생성 (별도 feature)
- 일정 충돌 검사 (같은 시간에 다른 약물 일정 겹침)
- 반복 패턴 복잡 로직 (매월 N일 등 — 현재 요일 패턴만 지원)

## 5) 설계

### 5-1) 도메인 모델
- `medication_schedules` 테이블 사용. `docs/ai-harness/06-domain-model.md §5 medication_schedules` 참조.
- MedicationSchedule 엔티티 이미 존재 (모든 필드 구현 완료).
- 소유자 검증: `scheduleId → medicine.ownerId` 경로로 간접 검증.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/medicines/{medicineId}/schedules | 일정 등록 | 필수 | `ScheduleCreateRequest` | `ScheduleResponse` (201) |
| GET | /api/v1/medicines/{medicineId}/schedules | 일정 목록 조회 | 필수 | - | `ScheduleListResponse` (200) |
| GET | /api/v1/medicines/{medicineId}/schedules/{scheduleId} | 일정 상세 조회 | 필수 | - | `ScheduleResponse` (200) |
| PATCH | /api/v1/medicines/{medicineId}/schedules/{scheduleId} | 일정 수정 | 필수 | `ScheduleUpdateRequest` | `ScheduleResponse` (200) |
| DELETE | /api/v1/medicines/{medicineId}/schedules/{scheduleId} | 일정 삭제 | 필수 | - | 204 |

#### 접근 권한 규칙
- medicineId로 Medicine 조회 → `medicine.ownerId`와 현재 사용자 비교
- 본인 소유 또는 care_relations 활성 관계가 있는 보호자만 접근 가능

#### DTO 설계

**ScheduleCreateRequest**
```json
{
  "scheduledTime": "08:00" (필수, HH:mm),
  "dosage": "1정" (필수),
  "daysOfWeek": "DAILY" (선택, 기본값 "DAILY"),
  "startDate": "2026-04-11" (선택, 기본값 오늘),
  "endDate": null (선택, null이면 무기한)
}
```

**ScheduleUpdateRequest**
```json
{
  "scheduledTime": "09:00" (선택),
  "dosage": "2정" (선택),
  "daysOfWeek": "MON,WED,FRI" (선택),
  "startDate": "2026-04-15" (선택),
  "endDate": "2026-07-15" (선택)
}
```

**ScheduleResponse**
```json
{
  "id": Long,
  "medicineId": Long,
  "scheduledTime": "08:00",
  "dosage": "1정",
  "daysOfWeek": "DAILY",
  "startDate": "2026-04-11",
  "endDate": null,
  "createdAt": LocalDateTime
}
```

**ScheduleListResponse**
```json
{
  "responses": List<ScheduleResponse>
}
```

### 5-3) 외부 연동
- 없음. 순수 CRUD.

### 5-4) 데이터 흐름

```text
Client → [Authorization: Bearer token]
       → MedicationScheduleController
       → MedicationScheduleService
         → MedicineRepository.findById(medicineId)  // 약물 존재 검증
         → medicine.ownerId 기반 접근 검증           // 소유자/보호자 확인
         → MedicationScheduleRepository              // CRUD
       → medication_schedules 테이블
```

### 5-5) DB 마이그레이션
- 없음. `medication_schedules` 테이블과 `MedicationSchedule.java` 엔티티가 이미 타깃 스키마와 일치.

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: `feat(medication)` MedicationScheduleService + MedicationScheduleController (CRUD 5개) + DTO + E2E 테스트

## 7) 테스트 전략
- **E2E (RestAssured)**:
  - 일정 등록 → 201 + 응답 검증
  - 목록 조회 → 200 + 해당 약물의 일정만 반환
  - 상세 조회 → 200
  - 수정 → 200 + 변경된 필드 검증
  - 삭제 → 204
  - 다른 사용자의 약물에 일정 등록 시도 → 403

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| ~~Q1~~ | ~~소유자 검증 방식~~ | ~~medicine.ownerId 경로로 간접 검증~~ | **결정됨** → §9 참조 |
| ~~Q2~~ | ~~URI 구조~~ | ~~중첩: /medicines/{id}/schedules~~ | **결정됨** → §9 참조 |

## 9) 결정 로그
- 2026-04-11: 초안 작성 (status=draft). 알림/복약 기록은 out-of-scope.
- 2026-04-11: Q1 결정 — 소유자 검증은 medicine.ownerId를 통한 간접 검증. schedule 자체에 ownerId를 두지 않음.
- 2026-04-11: Q2 결정 — URI는 중첩 방식(`/medicines/{medicineId}/schedules`). schedule은 medicine의 종속 리소스이므로 관계가 URL에 드러나는 게 RESTful.
