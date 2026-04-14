---
feature: 약물 등록/조회/수정/삭제
slug: medicine-crud
status: draft
owner: @goohong
scope: medicine
related_issues: []
related_prs: []
last_reviewed: 2026-04-10
---

# 약물 등록/조회/수정/삭제

## 1) 개요 (What / Why)
- 시니어(또는 보호자)가 복용 중인 약물을 **수동으로 등록·관리**할 수 있도록 한다.
- 처방전 OCR을 통한 자동 등록과 별개로, 비처방약(영양제/일반의약품 등)도 직접 등록할 수 있어야 한다.
- Medicine은 복약 일정(Medication Schedule), DUR 점검, 복약 기록의 **기반 엔티티**이므로 이 CRUD가 선행되어야 후속 기능 구현이 가능하다.

## 2) 사용자 시나리오

### SC-1: 시니어가 약물을 수동 등록
1. 시니어가 "약 추가" 화면에서 약 이름, 총량, 잔량을 입력한다.
2. `POST /api/v1/medicines`로 약물이 생성된다.
3. 응답으로 생성된 약물 정보를 받는다.

### SC-2: 시니어가 자신의 약 목록을 조회
1. 시니어가 "내 약 관리" 화면에 진입한다.
2. `GET /api/v1/medicines`로 본인 소유 약물 목록을 조회한다.

### SC-3: 시니어가 약 정보를 수정
1. 시니어가 약 상세 화면에서 잔량을 수정한다.
2. `PATCH /api/v1/medicines/{medicineId}`로 변경사항을 저장한다.

### SC-4: 시니어가 약물을 삭제
1. 더 이상 복용하지 않는 약을 삭제한다.
2. `DELETE /api/v1/medicines/{medicineId}`로 약물을 제거한다.

## 3) 요구사항

### 기능 요구사항
- [ ] 인증된 사용자가 약물을 등록할 수 있다 (name, totalAmount, remainingAmount 필수)
- [ ] 시니어 본인 등록: `owner_id`는 현재 로그인 사용자로 자동 설정
- [ ] 보호자 대리 등록: 요청에 `seniorId`를 명시. `care_relations`에 활성 관계가 있어야 허용
- [ ] 수동 등록 약물은 `prescription_id = NULL`
- [ ] 본인 소유 약물 목록 조회 (owner_id 기준, 페이징 없이 전체 반환)
- [ ] 보호자는 연동된 시니어의 약물 목록도 조회 가능 (`seniorId` 파라미터)
- [ ] 약물 단건 상세 조회
- [ ] 약물 정보 수정 (name, totalAmount, remainingAmount, durWarningText)
- [ ] 약물 삭제 — 연관된 `medication_schedules`가 있으면 **cascade 삭제** + 응답에 삭제된 스케줄 수 경고 포함
- [ ] 소유자 또는 연동된 보호자가 아닌 사용자의 접근 시 403 응답

### 비기능 요구사항
- 약물명에 민감 의료정보가 포함될 수 있으므로 로그에 약물명 원문 노출 금지

## 4) 범위 / 비범위

### 포함
- Medicine CRUD 5개 엔드포인트 (등록/목록/상세/수정/삭제)
- 소유자 검증 (본인 + 연동 보호자만 접근 가능)
- 보호자의 시니어 약물 대리 등록/조회/수정/삭제 (care_relations 기반)
- Repository / Service / Controller 계층 구현
- 신규 엔드포인트 E2E 테스트

### 제외 (Out of Scope)
- 처방전 OCR로부터의 자동 약물 생성 (prescription 도메인 책임)
- 복약 일정(Medication Schedule) 등록/관리 (별도 feature)
- DUR 점검 연동 (health 도메인, 별도 feature)
- 약물 검색/자동완성 (외부 약물 DB 연동 필요, 후속 feature)

## 5) 설계

### 5-1) 도메인 모델
- `medicines` 테이블 사용. `docs/ai-harness/06-domain-model.md §5 medicines` 참조.
- 현재 엔티티 코드(`Medicine.java`)에 `owner_id`, nullable `prescription_id`가 이미 반영됨.
- 추가 마이그레이션 불필요.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/medicines | 약물 등록 | 필수 | `MedicineCreateRequest` | `MedicineResponse` (201) |
| GET | /api/v1/medicines | 약물 목록 조회 | 필수 | `?seniorId=` (선택) | `MedicineListResponse` (200) |
| GET | /api/v1/medicines/{medicineId} | 약물 상세 조회 | 필수 | - | `MedicineResponse` (200) |
| PATCH | /api/v1/medicines/{medicineId} | 약물 수정 | 필수 | `MedicineUpdateRequest` | `MedicineResponse` (200) |
| DELETE | /api/v1/medicines/{medicineId} | 약물 삭제 | 필수 | - | `MedicineDeleteResponse` (200) |

#### 접근 권한 규칙
- **시니어**: 본인 약물만 CRUD 가능
- **보호자**: `care_relations`에 활성 관계(`deleted_at IS NULL`)가 있는 시니어의 약물에 한해 CRUD 가능
  - 등록 시 `seniorId` 필수 지정
  - 목록 조회 시 `seniorId` 쿼리 파라미터로 대상 시니어 지정
  - 수정/삭제 시 약물의 `owner_id`와 연동 관계 검증

#### DTO 설계

**MedicineCreateRequest**
```json
{
  "seniorId": Long (선택 — 보호자가 대리 등록 시 필수, 시니어 본인은 생략),
  "name": String (필수),
  "totalAmount": Integer (필수),
  "remainingAmount": Integer (필수),
  "durWarningText": String (선택)
}
```

**MedicineUpdateRequest**
```json
{
  "name": String (선택),
  "totalAmount": Integer (선택),
  "remainingAmount": Integer (선택),
  "durWarningText": String (선택)
}
```

**MedicineResponse**
```json
{
  "id": Long,
  "name": String,
  "totalAmount": Integer,
  "remainingAmount": Integer,
  "prescriptionId": Long (nullable),
  "durWarningText": String (nullable),
  "createdAt": LocalDateTime
}
```

**MedicineListResponse**
```json
{
  "responses": List<MedicineResponse>
}
```

**MedicineDeleteResponse**
```json
{
  "deletedMedicineId": Long,
  "deletedScheduleCount": Integer (연관 삭제된 복약 일정 수, 0이면 없음)
}
```

### 5-3) 외부 연동
- 없음. 순수 CRUD.

### 5-4) 데이터 흐름

```text
Client → [Authorization: Bearer token]
       → MedicineController
       → MedicineService (소유자 검증 포함)
       → MedicineRepository (JPA)
       → medicines 테이블
```

- **시니어 등록**: `@AuthenticationPrincipal`에서 userId 추출 → `owner_id`로 설정
- **보호자 대리 등록**: `seniorId` 지정 → `care_relations` 활성 관계 검증 → `owner_id = seniorId`
- **조회/수정/삭제**: `medicine.ownerId == currentUserId` 또는 `care_relations`에 활성 관계 존재 확인
- **삭제**: cascade로 연관 `medication_schedules` 함께 삭제, 삭제된 스케줄 수를 응답에 포함

### 5-5) DB 마이그레이션
- 추가 마이그레이션 없음. `medicines` 테이블과 `Medicine.java` 엔티티가 이미 타깃 스키마와 일치.

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: `feat(medicine)` MedicineRepository + MedicineService + MedicineController (CRUD 5개 엔드포인트) + E2E 테스트

> 엔티티가 이미 존재하고 외부 연동이 없으므로 단일 PR로 충분.

## 7) 테스트 전략
- **E2E (RestAssured)**: 5개 엔드포인트 각각 성공 케이스 필수
  - 등록 → 201 + 응답 검증
  - 목록 조회 → 200 + 본인 약물만 반환
  - 상세 조회 → 200
  - 수정 → 200 + 변경된 필드 검증
  - 삭제 → 200 + `deletedScheduleCount` 검증
- **Service 단위 테스트**: 소유자 검증 실패 시 예외 발생 케이스

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| ~~Q1~~ | ~~약물 삭제 시 연관 medication_schedule 처리~~ | ~~(a) cascade 삭제~~ | **결정됨** → §9 참조 |
| ~~Q2~~ | ~~보호자가 시니어의 약물을 등록/수정할 수 있는가~~ | ~~(a) 이번에 포함~~ | **결정됨** → §9 참조 |
| ~~Q3~~ | ~~목록 조회 시 페이징 필요 여부~~ | ~~(a) 전체 반환~~ | **결정됨** → §9 참조 |

## 9) 결정 로그
- 2026-04-10: 초안 작성 (status=draft). DUR 점검, 복약 일정은 out-of-scope.
- 2026-04-10: Q1 결정 — 약물 삭제 시 연관 `medication_schedules` cascade 삭제. 응답에 삭제된 스케줄 수를 포함하여 클라이언트가 사용자에게 경고를 표시할 수 있도록 함.
- 2026-04-10: Q2 결정 — 보호자의 시니어 약물 대리 관리를 이번 feature에 포함. `care_relations` 활성 관계 검증 기반. 등록 시 `seniorId` 파라미터 추가.
- 2026-04-10: Q3 결정 — 목록 조회는 페이징 없이 전체 반환. 개인 약물 수가 적을 것으로 예상(5~20개). 필요 시 후속 추가.
