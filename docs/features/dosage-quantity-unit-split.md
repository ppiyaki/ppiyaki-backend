---
feature: dosage 컬럼 정수 + 단위 분리
slug: dosage-quantity-unit-split
status: ready-for-impl
owner: @goohong
scope: prescription
related_issues: []
related_prs: [318]
last_reviewed: 2026-05-12
---

# dosage 컬럼 정수 + 단위 분리

## 1) 개요 (What / Why)
- 처방전·복약 도메인의 `dosage`가 현재 자유 입력 String(`"1정"`, `"한 정"`, `"PRN"`, `"30mg"`)이라 정수 추출이 정규식 fallback에 의존한다.
- AI(OpenAI)가 추출한 raw dosage 텍스트가 `MedicationSchedule.dosage` / `PrescriptionMedicineCandidate.extractedDosage`에 그대로 저장되고, 약 차감/일정 카운트 시점에 `MedicationSchedule.parseDosageInt`로 정수만 추출, 실패 시 fallback 1개 차감.
- **Primary Why**: (1) 비정수 표현(`한 정`, `PRN`, `30mg`)은 잔여량 차감을 항상 1로 만들어 데이터 정합성 떨어짐. (2) AI 출력 schema가 정밀하지 않아 사람 검수 비용 큼. (3) `parseDosageInt` fallback 제거가 PR #318 머지로 가능해진 후속 정리.
- **부수 효과**: PRN(필요 시 복용) 같은 케이스를 도메인적으로 명확히 구분할 진입로 마련.

## 2) 사용자 시나리오
- **AI/시스템**: OCR 마스킹 텍스트에서 약 정보를 추출할 때 `{quantity: 1, unit: "정"}` 형태로 정밀 응답 → 사람 검수 필요 케이스(`한 정`, `PRN`)를 양 통합 필드가 아니라 unit만 받는 형식으로 표현.
- **보호자**: 처방전 검토 시 OCR 누락분/오류분의 dosage를 정수 + 단위로 입력 (PR #318로 추가된 dosage 필드 갱신).
- **시스템**: 복약 인증 시 `parseDosageInt` 없이 `schedule.dosageQuantity`로 잔여량 차감. PRN/null 케이스는 차감 자체를 skip하거나 별도 정책으로 처리.

## 3) 요구사항

### 기능 요구사항
- [ ] `MedicationSchedule`에 `dosage_quantity DECIMAL(5,2) NULL` + `dosage_unit VARCHAR(16) NULL` 컬럼 추가 (Q2 답변: 분수 허용)
- [ ] `PrescriptionMedicineCandidate`에 동일 두 컬럼 추가
- [ ] `ExtractedMedicine`(OpenAI 응답 DTO)에 `dosageQuantity: BigDecimal`/`dosageUnit: String` 필드 + 시스템 프롬프트 갱신: **"한 정/한 캡슐"도 quantity=1로 변환, "반정"=0.5, PRN처럼 횟수 정의 안 되는 케이스만 quantity=null** 강제 (Q1 정정)
- [ ] `CandidateDecisionRequest`(PR #318 결과)에서 `dosage` String 필드 **즉시 제거** + `dosageQuantity`/`dosageUnit` 두 필드 신설 (Q5: 호환 끊음)
- [ ] `MedicationLogService.java:124-125` fallback 1개 차감 제거 → `schedule.getDosageQuantity()` 직접 사용. `dosageQuantity == null` 케이스는 차감 skip
- [ ] `MedicationSchedule.parseDosageInt` 메서드 + `DOSAGE_INT_PATTERN` 상수 제거 (호출처 0)
- [ ] 잔여량 차감/대시보드 카운트 로직을 `BigDecimal` 또는 `double` 기반으로 변경
- [ ] 응답 DTO(`PrescriptionMedicineCandidateResponse`, schedule 조회/대시보드/medication-log) 모두 분리 구조 노출
- [ ] **backfill 생략** (Q6 정정): 옛 dosage String → 분리 컬럼 자동 변환 안 함. 모든 옛 row는 `dosage_quantity = NULL` 상태. SQL 정규식이 옛 `parseDosageInt`와 같은 한계 갖는 걸 회피하기 위함
- [ ] Notion API 명세 다수 갱신 (prescription confirm/detail/PATCH, medication-log upsert/조회, schedule 조회, 대시보드)

### 비기능 요구사항
- 기존 데이터 무손실 (옛 `dosage` String 컬럼은 일정 기간 공존, 분리 컬럼 backfill 검증 후 drop)
- AI 출력 schema는 **즉시 새 schema 강제** (Q4: 호환 없음). PR 1(엔티티)+PR 2(AI schema)+PR 3(DTO/Service)을 **같은 release에 묶어 cut-over** 보장 필수

## 4) 범위 / 비범위

### 포함
- 양 엔티티 컬럼 추가 + AI schema 변경 + DTO 분리 + 호출처 정리 + fallback 제거
- prod 마이그레이션 SQL 가이드 작성 (수동 ALTER + backfill SQL)
- Notion API 명세 갱신
- E2E 회귀 + 신규 케이스(PRN/null/정수 모두) 검증

### 제외 (Out of Scope)
- 단위 마스터(예: `정`/`캡슐`/`ml`/`mg`/`알`/`포` 표준화 enum) — 우선 자유 입력 String. enum 표준화는 별도 follow-up
- PRN 처방의 알림/스케줄 정책 변경 — 등록만 허용, 알림 트리거는 기존 방식 유지 (별도 도메인 결정)
- Medicine 도메인 자체의 단위 표기 (제품 사양) — prescription/schedule 관점만
- 옛 `dosage` String 컬럼 drop — release 후 안정화 기간(예: 2주) 후 별도 PR로 처리
- **옛 schedule(이미 confirm된 처방의 schedule) dosage 보강** — Q6/Q7 결정에 따라 자동 backfill도 안 하고 caregiver 보강 API도 신설 안 함. release 직후 기존 schedule들은 `dosage_quantity = NULL`로 잔여량 차감 멈춘 상태로 유지. 운영 데이터 영향이 작아 그대로 두기로 결정. 차후 필요해지면 별도 follow-up

## 5) 설계

### 5-1) 도메인 모델
- 엔티티 변경: `MedicationSchedule`, `PrescriptionMedicineCandidate`
- `06-domain-model.md` §5 엔티티 + §6 Mermaid ERD 동시 갱신
- 유비쿼터스 랭귀지 §4: `Dosage Quantity` / `Dosage Unit` 등재

### 5-2) API 엔드포인트 (변경)

| Method | Path | 변경 내용 |
|---|---|---|
| PATCH | /api/v1/prescriptions/{id}/medicines/{candidateId} | `dosage` String → `dosageQuantity` Int + `dosageUnit` String |
| GET | /api/v1/prescriptions/{id} | candidate 응답에 분리 두 필드 |
| POST | /api/v1/prescriptions/{id}/confirm | 내부 schedule 생성 시 분리 두 필드 사용 |
| POST | /api/v1/prescriptions/{id}/medicines | 수동 추가에서도 분리 두 필드 |
| PUT | /api/v1/medication-logs | 응답에 schedule.dosageQuantity 노출 (필요 시) |
| GET | /api/v1/seniors/{id}/dashboard/* | 슬롯별 dosage 노출 시 분리 구조 |

### 5-3) 외부 연동 (OpenAI)
- 시스템 프롬프트 갱신:
  - "**dosage는 반드시 quantity(숫자) + unit(단위) 두 필드로 분리해서 응답하라**"
  - "**한국어 수량 표현(`한`/`두`/`세`)도 숫자로 변환하라**" (`한 정` → `{quantity:1, unit:"정"}`)
  - "**분수 표현(`반`)은 소수로**" (`반정` → `{quantity:0.5, unit:"정"}`)
  - "**횟수가 정의되지 않은 케이스(`PRN`, `필요 시`)만 quantity=null**" (`PRN` → `{quantity:null, unit:"PRN"}`)
- 호환성: **즉시 새 schema 강제, 옛 schema 응답 미지원**. 옛 schema 응답 발생 시 candidate 등록 자체 실패 → AI 프롬프트 안정성 검증을 PR 2에서 충분히 수행

### 5-4) 데이터 흐름
1. OCR → 마스킹 → OpenAI 호출 → `ExtractedMedicine{name, dosageQuantity?, dosageUnit?, ...}` 응답
2. `PrescriptionMedicineCandidate(extractedDosageQuantity, extractedDosageUnit)` 저장
3. caregiver PATCH로 보강 (PR #318 dosage String → 분리 두 필드)
4. confirm 시 `MedicationSchedule(dosageQuantity, dosageUnit)` 생성. **dosageQuantity == null이면 schedule 생성 skip은 유지** (현행 정책)
5. 복약 인증 시 `medicine.decreaseRemainingAmount(schedule.getDosageQuantity())` — null이면 skip

### 5-5) DB 마이그레이션 (prod 수동)
```sql
-- Phase A: 컬럼 추가 (nullable, DECIMAL — 분수 허용)
ALTER TABLE medication_schedules
  ADD COLUMN dosage_quantity DECIMAL(5,2) NULL,
  ADD COLUMN dosage_unit VARCHAR(16) NULL;

ALTER TABLE prescription_medicine_candidates
  ADD COLUMN extracted_dosage_quantity DECIMAL(5,2) NULL,
  ADD COLUMN extracted_dosage_unit VARCHAR(16) NULL;

-- Phase B: 자동 backfill 안 함 (Q6 정정 결정).
-- 옛 dosage String의 정규식 한계가 새 시스템에 침투하는 걸 막기 위해
-- 옛 row는 모두 NULL 상태로 두고, caregiver/운영자가 별도 보강.

-- Phase C (별도 PR/release): 옛 dosage 컬럼 drop
-- ALTER TABLE medication_schedules DROP COLUMN dosage;
```

## 6) 작업 분할 (예상 PR 리스트)

> ⚠️ Q4(즉시 cut-over) 결정에 따라 **PR 1~3을 같은 release 사이클에 묶어 머지**. 분리 머지 시 옛/새 schema가 섞여 candidate 등록 실패 가능.

- [ ] **PR 1 (보호 영역, needs-human-review)**: 양 엔티티에 `dosage_quantity DECIMAL(5,2)` + `dosage_unit VARCHAR(16)` 컬럼 추가 (옛 dosage 컬럼 유지) + JPA 매핑. prod 마이그레이션 가이드(Phase A+B) 첨부. backfill SQL 포함
- [ ] **PR 2**: AI 프롬프트/응답 schema 갱신 + `ExtractedMedicine` 변경. **옛 schema 응답 미지원, 즉시 새 schema 강제** (Q4). 프롬프트 안정성 검증 테스트
- [ ] **PR 3**: DTO 분리 + Service 호출처 정리. `CandidateDecisionRequest.dosage` String 필드 **즉시 제거** + `dosageQuantity`/`dosageUnit` 신설 (Q5). `addManualMedicine`, `confirm` 흐름. **MedicationLogService fallback 제거 + `parseDosageInt` 제거 함께 처리** (BigDecimal 차감으로 전환). 응답 DTO 분리 구조 노출. Notion 명세 일괄 갱신
- [ ] **PR 4 (다음 release 후 별도)**: 옛 `dosage` String 컬럼 drop, 옛 매핑/관련 코드 제거

## 7) 테스트 전략
- 단위: `MedicationSchedule` 도메인 메서드, `MedicationLogService` 차감 로직 (정수/null 케이스)
- 통합: AI 응답 mock으로 새 schema → candidate 저장 검증
- E2E: PR #318 추가 dosage 보강 케이스를 분리 구조로 갱신 + 신규 PRN/한 정 (quantity=null) 케이스
- AI 프롬프트 검증: 실 OpenAI 호출 테스트 1-2건 (선택, CI 비용 고려)

## 8) 오픈 질문

(현재 미해소 항목 없음 — 모두 §9 결정 로그로 이전)

## 9) 결정 로그

- 2026-05-12: 초안 작성 (status=draft). PR #318 머지 후 follow-up으로 합의.
- 2026-05-12: 오픈 질문 6건 합의 완료, status=ready-for-impl
  - **Q1 정정**: AI 프롬프트가 "한 정" 같은 표현도 quantity=1로 변환 강제. 진짜 비정수 케이스(`PRN`/`필요 시`)만 quantity=null
  - **Q2**: dosage_quantity 컬럼 타입 = `DECIMAL(5,2)`. 분수("반정"=0.5) 허용. 차감 로직 BigDecimal 기반
  - **Q3**: 단위 표준화(enum)는 별도 follow-up. unit은 String 자유 입력 유지
  - **Q4**: AI schema 즉시 cut-over (옛 schema 호환 없음). PR 1~3을 같은 release 사이클에 묶음
  - **Q5**: PR #318의 `CandidateDecisionRequest.dosage` String 필드 즉시 제거 + `dosageQuantity`/`dosageUnit` 두 필드 신설
  - **Q6 정정**: backfill 자체 생략. 옛 dosage row는 모두 `dosage_quantity = NULL` 상태. SQL 정규식 한계가 새 시스템에 침투하는 걸 막기 위함
  - **Q7 (Q6 후속)**: release 직후 옛 schedule들의 잔여량 차감 멈추는 문제 — 그대로 두기로 결정. 운영 데이터 영향 작음. 보강 API(예: PATCH /api/v1/medication-schedules/{id}) 신설도 이번 spec 범위 외
