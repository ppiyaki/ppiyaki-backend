---
feature: 복약 기록 API — 인증 사진 첨부 포함
slug: medication-log
status: draft
owner: @goohong
scope: medication
related_issues: [183]
related_prs: []
last_reviewed: 2026-04-18
---

# 복약 기록 API — 인증 사진 첨부 포함

## 1) 개요 (What / Why)
- 시니어가 복약 일정(`MedicationSchedule`)대로 약을 복용한 결과를 기록하고, **복약 인증 사진**을 첨부한다.
- **Primary Why**: 시니어의 복약 이행을 사진으로 **증빙** → 보호자 신뢰 확보. 보호자가 조회 API로 해당 사진을 열람 가능.
- 식별 정확도는 Phase 1에서 불필요 — 사진 + 타임스탬프 + 사용자 확정 상태만으로도 증빙 가치. AI 기반 개수·종류 검증은 Phase 2 (#184) / Phase 3 (#185)에서 확장.

## 2) 사용자 시나리오
- **시니어**가 복약 일정 시각에 알림을 받고 약을 먹은 뒤, 약 봉지·손바닥 위 알약을 촬영해 앱에서 "먹었어요"를 눌러 기록한다. 촬영은 선택 (사진 없는 확정도 허용).
- **시니어**가 외출 등 사유로 약을 못 먹은 경우, "못 먹었어요"(MISSED)로 기록한다.
- **보호자**는 시니어의 복약 기록을 일자별로 조회해 사진이 있는 경우 확인한다.
- **보호자**가 시니어 대신 "먹었다고 확인"할 수 있다(대리 기록, `is_proxy=true`).

## 3) 요구사항

### 기능 요구사항
- [ ] `MedicationLogRepository` 신설 — `findByScheduleIdAndTargetDate`, `findBySeniorIdAndTargetDateBetween` 등
- [ ] `LogStatus` enum 신설 — `TAKEN` / `MISSED` / `PENDING`. `MedicationLog.status` varchar에 매핑.
- [ ] `PUT /api/v1/medication-logs` — 복약 체크 (멱등 업서트). 자연 키 `(scheduleId, targetDate)` 기반.
- [ ] `GET /api/v1/medication-logs` — 기간별 기록 조회 (본인 + 보호자 권한)
- [ ] `(schedule_id, target_date)` UNIQUE 제약 추가 — 같은 일정·일자에 기록 1건만 허용
- [ ] 보호자 대리 기록 시 `is_proxy`, `confirmed_by_user_id` 자동 세팅
- [ ] 요청은 `photoObjectKey`(optional, 스토리지 내 상대 경로)로 받고, 응답은 서버가 조립한 `photoUrl`로 내려준다. 미첨부 허용.
- [ ] `photoObjectKey`의 `{userId}` 세그먼트가 요청자 userId와 일치하는지 검증 — 타인 objectKey 도용 차단.

### 비기능 요구사항
- JWT 인증 필수.
- `photoObjectKey` 검증: 형식(`{purpose}/{userId}/{uuid}.{ext}`) + userId 세그먼트 일치 검증. 외부 URL/스키마 주입 원천 불가(서버가 호스트·버킷 조립).
- 민감정보 로깅 금지 — 복약 상태·objectKey를 로그 원문에 남기지 않음(필요 시 id/userId만).

## 4) 범위 / 비범위

### 포함
- 복약 기록 생성/조회 API (PUT 멱등 업서트, GET)
- 본인/보호자 권한 검증 (기존 `MedicationScheduleService` 패턴 재사용 — `CareRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull`)
- `LogStatus` enum 신설 (ADR 0006: Java enum / DB varchar)
- `(schedule_id, target_date)` UNIQUE 인덱스 도입

### 제외 (Out of Scope)
- **약 개수/종류 AI 검증** — Phase 2 (#184, spec: `medication-log-phase2.md`), Phase 3 (#185)
- **Vision LLM 호출** — Phase 2에서 도입
- **미복용 자동 판정 스케줄러** — 예정 시각 + N분 경과 시 자동 MISSED 처리하는 배치. 별도 이슈로 분리(Q3 결정). 대신 조회 API가 실제 저장된 row만 반환하고, **프론트가 "스케줄 × 해당 기간 날짜 조합 vs 기록"을 교차 계산해 "기록 없음 = 시니어 미확인"으로 표시** — 이 방식으로 UX는 동일 품질 유지.
- **리포트 집계/PDF 생성 API** — `Report` 엔티티는 placeholder 상태. 보호자 조회 API로 사진 확인 가능한 수준까지만.
- **복약 성공 시 삐약이 포인트 증가 이벤트** — 별도 이벤트/도메인 이슈.
- **복약 기록 삭제 API** — 필요해지면 별도 이슈 (감사 로그 관점에서 soft-delete 여부 결정 필요).
- **변경 이력 테이블(`medication_log_revisions`)** — PUT 업서트는 기존 row를 덮어쓰므로 이력이 남지 않음. 감사 요구 발생 시 별도 이슈로 도입.

## 5) 설계

### 5-1) 도메인 모델
기존 `MedicationLog` 엔티티 그대로 사용. 컬럼 구조 변경 없음.

| 컬럼 | 타입 | 설명 | 이번 이슈에서 |
|---|---|---|---|
| id | bigint PK | | 기존 |
| senior_id | bigint | `users.id` | 기존 |
| schedule_id | bigint | `medication_schedules.id` | 기존 |
| target_date | date | 예정 복약 일자 | 기존 |
| taken_at | datetime nullable | 실제 확인 시각 | 기존 |
| status | varchar | `LogStatus` enum 매핑 | **enum 신설** |
| photo_url | varchar nullable | **실제로는 objectKey를 저장** (호스트·버킷은 응답 시 서버가 조립) | 기존 (활용 시작) |
| ai_status | varchar nullable | AI 판정 결과 | 기존 (Phase 2에서 사용, 이번엔 항상 null) |
| is_proxy | boolean | 보호자 대리 여부 | 기존 |
| confirmed_by_user_id | bigint nullable | 확정 사용자 | 기존 |
| created_at | timestamp | `CreatedTimeEntity` | 기존 |

`(schedule_id, target_date)` UNIQUE 제약 추가 — ERD 및 `06-domain-model.md §7-12`에서 이미 권장되어 있음.

> **컬럼명 주의**: 기존 컬럼명은 `photo_url`이지만 이번 이슈부터 **값은 `objectKey`**(예: `medication-log/42/uuid.jpg`)를 저장한다. 컬럼명 리네임(`photo_object_key`)은 별도 리팩터 이슈로 분리.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| PUT | /api/v1/medication-logs | 복약 체크 (멱등 업서트) | JWT | `MedicationLogUpsertRequest` | `MedicationLogResponse` |
| GET | /api/v1/medication-logs | 기간별 복약 기록 조회 | JWT | query: `seniorId`, `from`, `to` | `MedicationLogListResponse` |

**MedicationLogUpsertRequest**
```json
{
  "scheduleId": 1,
  "targetDate": "2026-04-18",
  "takenAt": "2026-04-18T09:00:00",
  "status": "TAKEN",
  "photoObjectKey": "medication-log/42/9b3e7a1c-8d55-4f0a-b2e1-5f9a7b3d8c21.jpg"
}
```

- `takenAt`은 null 허용. null이면 서버 시각으로 채움.
- `photoObjectKey`는 null 허용. 제공 시 형식(`{purpose}/{userId}/{uuid}.{ext}`) + `{userId}`가 요청자와 일치하는지 서버에서 검증 — 불일치 시 400 `INVALID_INPUT` (§5-3과 동일 규칙).
- `status`는 enum 문자열 (`TAKEN`/`MISSED`/`PENDING`).

**MedicationLogResponse**
```json
{
  "id": 42,
  "seniorId": 1,
  "scheduleId": 1,
  "targetDate": "2026-04-18",
  "takenAt": "2026-04-18T09:00:00",
  "status": "TAKEN",
  "photoUrl": "https://kr.object.ncloudstorage.com/ppiyaki/medication-log/42/9b3e7a1c-8d55-4f0a-b2e1-5f9a7b3d8c21.jpg",
  "aiStatus": null,
  "isProxy": false,
  "confirmedByUserId": 1,
  "createdAt": "2026-04-18T09:00:01"
}
```

- Response는 **호스트·버킷을 조립한 full URL**(`photoUrl`)로 내려준다. 프론트가 바로 `<img src>` 사용 가능.
- 입력(`photoObjectKey`)과 출력(`photoUrl`) 필드명 의도적으로 분리 — 서버가 URL 조립 책임을 독점함을 API 계약에서 드러냄.

**GET 쿼리 규칙**
- `seniorId` 생략 가능. 생략 시 요청자 본인의 시니어 데이터 (요청자가 시니어일 때).
- `from`, `to`는 `LocalDate` ISO-8601. 기본 기간 최대 31일(이상이면 400).

**멱등 업서트 규칙 (PUT)**
- 자연 키: `(scheduleId, targetDate)`.
- 동일 키 row가 **이미 있으면** `takenAt`/`status`/`photoUrl`/`isProxy`/`confirmedByUserId`를 갱신.
- 없으면 신규 생성.
- 동일 요청을 재전송해도 결과 동일(멱등) — 네트워크 재시도 안전.
- 반환 상태 코드: 신규 생성·갱신 구분 없이 `200 OK` + 본문으로 최신 상태 반환 (PUT 관례).

### 5-3) 권한 규칙
- `confirmedByUserId`는 JWT 유저 ID.
- 시니어 본인이면 `isProxy = false`.
- 보호자면 `CareRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(userId, seniorId)` 검증 → 존재 시 `isProxy = true`.
- 무관 사용자는 `ErrorCode.CARE_RELATION_NOT_FOUND`로 거부.

**seniorId 결정**: Request body에 `seniorId`를 받지 않는다. `scheduleId`로부터 `medicationSchedule.medicine.ownerId`(=seniorId)를 서버가 역추적. 클라이언트 실수/위변조 surface 축소.

**photoObjectKey 검증**: `photoObjectKey`의 `{userId}` 세그먼트가 요청자 userId와 일치하는지 확인. 예: `medication-log/42/uuid.jpg` ← 이 "42"가 JWT 유저와 같아야 함. 불일치 시 `ErrorCode.INVALID_INPUT` 400.
- 보호자 대리 기록 시: **보호자 본인이 찍어 업로드한 objectKey**여야 함(보호자의 userId가 들어감). 시니어의 objectKey를 보호자가 대신 전송하는 시나리오는 허용 안 함 — presigned URL 발급 시점에 이미 업로더 신원이 결정되므로 자연스럽다.

### 5-4) 외부 연동
- 파일 업로드: 이미 완료된 `POST /api/v1/uploads/presigned` 재사용 (이 API에서는 직접 호출 안 함). 프론트가 업로드 → URL만 본 API로 전달.
- 없음 (Phase 2/3에서 Vision LLM 추가).

### 5-5) 데이터 흐름
```text
[복약 체크 — 시니어 본인]
Client → (선택) POST /api/v1/uploads/presigned
           {purpose: MEDICATION_LOG, extension: jpg, contentType: image/jpeg}
       ← {objectKey: "medication-log/42/uuid.jpg", presignedUrl, expiresAt}
       → PUT presignedUrl (바이너리, 서버 안 거침)

       → PUT /api/v1/medication-logs
         {scheduleId, targetDate, takenAt, status, photoObjectKey: "medication-log/42/uuid.jpg"}
       → MedicationLogService.upsert(userId, request)
         → photoObjectKey 형식 + userId 세그먼트 검증
         → schedule 조회, ownerId(seniorId) 추출
         → 권한 검증 (본인? 보호자 via CareRelation?)
         → 기존 log 있으면 update, 없으면 create (photo_url 컬럼에 objectKey 저장)
       → 응답에서 objectKey → photoUrl 조립해 반환
       ← 200 OK + MedicationLogResponse

[조회 — 보호자]
Client → GET /api/v1/medication-logs?seniorId=42&from=...&to=...
       → MedicationLogService.readByPeriod(userId, seniorId, from, to)
         → 권한 검증 (본인 or CareRelation 존재)
         → repository 쿼리
         → 각 row의 objectKey → photoUrl 조립
       ← MedicationLogListResponse
```

URL 조립 책임: 서비스 계층(또는 별도 유틸)에 `PhotoUrlAssembler` 같은 컴포넌트 신설 고려. `NcpStorageProperties`에서 endpoint/bucket을 주입받아 `objectKey → full URL` 변환. 조립 규칙 변경 시 한 곳만 수정.

### 5-6) DB 마이그레이션
- 신규 테이블 없음.
- `(schedule_id, target_date)` UNIQUE 인덱스 추가.
- `ddl-auto: update` 정책상 자동 반영. `schema.sql`에 수동 정의 시 함께 반영.

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: Feature Spec 작성 (`docs/features/medication-log.md`) — `type:docs`, `scope:medication`
- [ ] PR 2: 엔티티 보완(`LogStatus` enum, UNIQUE 제약) + `MedicationLogRepository` + 단위 테스트 — `type:feat`, `scope:medication`
- [ ] PR 3: `MedicationLogService` + `MedicationLogController` + DTO + 컨트롤러/E2E 테스트 — `type:feat`, `scope:medication`

## 7) 테스트 전략
- **엔티티/enum 단위**: `LogStatus` 매핑, UNIQUE 제약 동작 (Hibernate `DataIntegrityViolationException` 확인)
- **Repository**: 기간 조회 쿼리(`findBySeniorIdAndTargetDateBetween`), `(schedule_id, target_date)` 조회
- **Service 단위**:
  - 본인 복약 체크 → `isProxy=false`, `confirmedByUserId=userId` 세팅
  - 보호자 복약 체크 → `isProxy=true`
  - 무관 사용자 → `CARE_RELATION_NOT_FOUND`
  - 동일 `(scheduleId, targetDate)` PUT 두 번 호출 → 업데이트 (row 수 불변, 멱등)
  - 조회 기간 31일 초과 → 400
  - **photoObjectKey의 userId 세그먼트 불일치 → 400** (타인 objectKey 도용 시도)
  - photoObjectKey 형식 불일치(세그먼트 부족, `..` 포함 등) → 400
- **URL 조립 단위**: `PhotoUrlAssembler` — objectKey 주입 시 endpoint/bucket 기반 정확한 URL 생성
- **Controller (@WebMvcTest)**: 정상 200, 인증 없음 401, 유효성 400, objectKey userId 불일치 400
- **E2E (RestAssured)**: 복약 체크(PUT) → 조회(GET) → 응답에 조립된 `photoUrl` 포함 확인 (성공 케이스 1건 필수)

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| — | 현재 오픈 질문 없음. Q1~Q3은 §9 결정 로그로 이관됨. | — | — |

## 9) 결정 로그
- 2026-04-18: 초안 작성 (status=draft).
- 2026-04-18: Phase 1 범위를 "복약 기록 CRUD + 인증 사진 첨부"로 한정. AI 기반 개수/종류 검증은 Phase 2(#184), Phase 3(#185)로 분리.
- 2026-04-18: Request body에 `seniorId`를 받지 않고 `scheduleId`로부터 역추적. 클라이언트 실수/위변조 축소.
- 2026-04-18: `LogStatus` enum 신설 (TAKEN/MISSED/PENDING). ADR 0006 "Java enum / DB varchar" 원칙 따름.
- 2026-04-18: 리포트 생성/PDF는 이번 범위 외. `Report` 엔티티 존재하나 별도 이슈에서 서비스/컨트롤러 구현 예정.
- 2026-04-18: **Q1 해소 — `PUT /api/v1/medication-logs` 멱등 업서트 채택**. 자연 키 `(scheduleId, targetDate)`가 존재하고 "그 슬롯의 상태를 설정한다"는 도메인 시맨틱이 PUT과 일치. 단일 엔드포인트로 프론트 분기 단순, 네트워크 재시도 안전(멱등), REST 정석.
- 2026-04-18: **Q2 해소 — 요청은 `photoObjectKey`로 받고 응답은 서버 조립 `photoUrl` 반환**. 외부 URL/스키마 주입 원천 차단 + objectKey의 `{userId}` 세그먼트 검증으로 타인 objectKey 도용 차단 + CDN/호스트 변경 시 DB row 수정 불필요. DB 컬럼은 현 `photo_url` 유지하되 **값은 objectKey를 저장**. 컬럼명 리네임(`photo_object_key`)은 별도 리팩터 이슈.
- 2026-04-18: **Q3 해소 — 미복용 자동 판정 스케줄러는 별도 이슈로 분리**. 스케줄러는 타임존·중복 실행·catch-up·다중 인스턴스 등 설계 부담이 크고, Phase 1의 Primary Why("복약 증빙")와 결이 다름. 대신 조회 API가 실제 저장된 row만 반환하고, 프론트가 "스케줄 × 날짜 조합 vs 기록"을 교차 계산해 미복용을 표시 — UX 품질은 동일 확보.
