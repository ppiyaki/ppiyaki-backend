---
feature: 보호자 처방전 확인 워크플로우
slug: prescription-confirmation
owner: @goohong
scope: prescription
related_issues: []
related_prs: []
status: draft
last_reviewed: 2026-04-16
---

# 보호자 처방전 확인 워크플로우

## 1) 개요 (What / Why)
- AI가 추출한 약물 후보는 인간(보호자 또는 시니어 본인) 검증 후에만 활성화한다.
- 자동 보정된 항목(OCR 오인식 → fuzzy 매칭으로 보정)은 사유 메모와 함께 강조 표시.
- 의료 안전·법적 책임 분산·UX 강화를 동시에 달성한다.

## 2) 사용자 시나리오
- **시니어**가 처방전을 등록하면 보호자에게 "처방전 확인 요청" 푸시 알림이 간다.
- **보호자**는 마스킹된 처방전 사진과 추출된 약물 리스트를 검토한다.
  - 정확히 매칭된 약물은 자동 체크 상태
  - 자동 보정된 약물은 ⚠️ 메모와 함께 표시 (예: "OCR '이부프로멘정' → '이부프로펜정 200mg'으로 자동 매칭")
  - 매칭 실패 약물은 후보 선택 또는 수동 검색
  - 잘못된 항목 삭제 가능
  - 처방전에 누락된 약물 수동 추가 가능
- "전체 확인 완료" 버튼을 누르면 약물·복약 일정 등록 화면으로 자동 이동.
- 보호자가 72h 내 미확인 시 시니어 본인 확인 가능 (보호자 부재 fallback).

## 3) 요구사항

### 기능 요구사항
- [ ] 보호자가 시니어와 활성 `care_relations` 관계일 때 시니어의 PENDING 처방전 조회 가능.
- [ ] `GET /api/v1/prescriptions/{id}` 응답에 모든 후보(`PrescriptionMedicineCandidate`)의 matchType·매칭 사유 포함.
- [ ] **EXACT 후보**: 기본 체크 상태로 표시. 보호자가 명시 해제하면 제외(opt-out).
- [ ] **FUZZY_AUTO 후보**: ⚠️ 보정 사유 메모 표시. 보호자 명시 확인 필요(opt-in).
- [ ] **MANUAL_REQUIRED 후보**: 후보 리스트 표시, 보호자 선택.
- [ ] **NO_MATCH 후보**: 수동 검색 UI(별도 search API), 또는 건너뛰기.
- [ ] `PATCH /api/v1/prescriptions/{id}/medicines/{candidateId}` — 보호자 결정 기록 (ACCEPTED / MANUALLY_CORRECTED / REJECTED + chosenItemSeq + note).
- [ ] `POST /api/v1/prescriptions/{id}/medicines` — 처방전에 누락된 약물 수동 추가.
- [ ] `POST /api/v1/prescriptions/{id}/confirm` — 전체 확인. 모든 후보의 `caregiver_decision != PENDING` 검증 후 `Prescription.status = CONFIRMED`로 전이. 활성화된 후보들로 `Medicine` row 생성·연결.
- [ ] `POST /api/v1/prescriptions/{id}/reject` — 처방전 폐기 (재촬영 요청). 마스킹본 + 후보들 모두 삭제. `Prescription.status = REJECTED`.
- [ ] **72h 미확인 처방전**: 시니어 본인이 확인 가능 (별도 권한 부여 로직).
- [ ] 모든 변경 audit log: 누가 언제 어떤 결정을 내렸는지 기록.

### 비기능 요구사항
- 보호자 화면 응답 < 500ms (마스킹본 presigned URL은 별도 fetch).
- 동시 편집 충돌 처리: optimistic locking (version 컬럼) 또는 마지막 쓰기 우선.
- 알림 발송 실패는 워크플로우 차단 안 함 (best effort).

## 4) 범위 / 비범위

### 포함
- 보호자 액션 API (확인/수정/거부/추가)
- 매칭 사유 메모 표시 데이터 구조
- 72h 미확인 fallback 로직
- 확인 완료 시 `Medicine` 엔티티 생성 연동
- audit log 테이블/필드

### 제외 (Out of Scope)
- **OCR/AI 추출 자체** → `prescription-ocr` spec
- **약물 검색·매칭 알고리즘** → `medicine-search` spec
- **푸시 알림 인프라(FCM)** → 별도 spec
- **복약 일정 등록 UI 흐름** → 기존 `medication-schedule-crud` spec 재사용
- **DUR 자동 점검 트리거** → 본 spec에서는 hook만, 동작은 `medicine-dur` spec
- **MCP tool 노출** → `mcp-server-foundation` spec

## 5) 설계

### 5-1) 도메인 모델
- 기존 재사용: `Prescription`, `PrescriptionMedicineCandidate` (`prescription-ocr` spec에서 신설)
- 본 spec에서 활용하는 필드: `caregiver_decision`, `caregiver_chosen_item_seq`, `caregiver_note`, `reviewed_at`
- 신규 audit log 필요 시: `prescription_review_logs` (단순 append-only)

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/v1/prescriptions/{id}` | 추출 결과 + 후보 + matchType별 메모 | 필수 (소유자/연동 보호자) |
| PATCH | `/api/v1/prescriptions/{id}/medicines/{candidateId}` | 후보별 보호자 결정 | 필수 |
| POST | `/api/v1/prescriptions/{id}/medicines` | 누락 약물 수동 추가 | 필수 |
| POST | `/api/v1/prescriptions/{id}/confirm` | 전체 확인 → CONFIRMED, Medicine 생성 | 필수 |
| POST | `/api/v1/prescriptions/{id}/reject` | 폐기 | 필수 |

### 5-3) 상태 머신

```
[PROCESSING] (OCR 진행 중)
   │
   ├── 성공 → [PENDING_REVIEW]
   └── 실패 → [PROCESSING_FAILED]

[PENDING_REVIEW]
   │
   ├── confirm   → [CONFIRMED]   → Medicine 엔티티 생성
   ├── reject    → [REJECTED]    → 마스킹본·후보 삭제
   └── 72h 경과  → 시니어 본인 확인 권한 활성 (상태 변경 없음)

[CONFIRMED] / [REJECTED] / [PROCESSING_FAILED] — terminal
```

### 5-4) 보호자 결정 → Medicine 생성 흐름

`POST /api/v1/prescriptions/{id}/confirm` 시:

```
1. 모든 candidate.caregiver_decision != PENDING 검증
2. accepted = candidates.filter(d == ACCEPTED || d == MANUALLY_CORRECTED)
3. FOR each accepted candidate:
     itemSeq = candidate.caregiver_chosen_item_seq ?? candidate.matched_item_seq
     name    = candidate.matched_item_name (또는 chosen 약물명)
     medicine = MedicineService.create(
                  ownerId=prescription.owner_id,
                  itemSeq=itemSeq,
                  name=name,
                  prescriptionId=prescription.id   // 추적용 FK
                )
     candidate.created_medicine_id = medicine.id
4. prescription.status = CONFIRMED
5. (hook) DUR 자동 점검 비동기 트리거 (medicine-dur spec)
6. 응답: 생성된 Medicine 리스트 → 클라이언트는 복약 일정 등록 화면으로 이동
```

### 5-5) DB 마이그레이션
- `prescription_medicine_candidates`에 `created_medicine_id` (bigint nullable, FK to medicines) 컬럼 추가
- (선택) `prescription_review_logs` 테이블 신설 — append-only audit
  ```
  id, prescription_id, candidate_id nullable, reviewer_user_id,
  action enum (ACCEPT/REJECT/CORRECT/ADD/CONFIRM/REJECT_PRESCRIPTION),
  before_value text nullable, after_value text nullable, created_at
  ```

## 6) 작업 분할 (예상 PR 리스트)

선행: `prescription-ocr` PR 7 머지

- [ ] PR 1 `docs(prescription)`: 본 spec 커밋
- [ ] PR 2 `feat(prescription)`: 후보별 PATCH·삭제·추가 API + Service. `caregiver_decision` 전이 검증.
- [ ] PR 3 `feat(prescription)`: confirm/reject API + Medicine 생성 연동. 권한(소유자/보호자) 검증.
- [ ] PR 4 `feat(prescription)`: 72h fallback — 시니어 본인 확인 권한 활성화 로직 (`@Scheduled` 배치 또는 요청 시점 계산).
- [ ] PR 5 `feat(prescription)`: audit log (선택, MVP에서는 candidate row의 변경 시각만 활용 후 후속 PR로 분리 가능).
- [ ] PR 6 `test(prescription)`: E2E — 보호자 확인 / 시니어 자동 fallback / 권한 실패 / 잘못된 상태 전이.

## 7) 테스트 전략

### 단위
- 상태 머신 전이 검증 (잘못된 전이 reject)
- 권한 검증 (소유자/보호자/72h fallback)
- candidate.caregiver_decision 변경 감사

### 통합 (E2E)
- 정상 흐름: OCR → PENDING_REVIEW → 보호자 confirm → CONFIRMED → Medicine 생성 확인
- MANUALLY_CORRECTED: 보호자가 다른 itemSeq 선택 → Medicine.itemSeq에 chosen 값 반영
- 누락 약물 추가: 보호자가 수동으로 약물 1개 추가 → Medicine 생성 확인
- 72h 후 시니어 확인: 보호자 미확인 상태에서 시니어 confirm 시도 → 성공
- 권한 실패: 다른 사용자가 confirm 시도 → 403

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q-CONF-1 | 보호자 없는 시니어가 처방전 등록할 때 | (a) 시니어 본인이 즉시 확인 가능 / (b) 보호자 연동 강제 | @goohong / PR 3 전 |
| Q-CONF-2 | EXACT 후보를 묻지도 않고 자동 통과? | (a) 자동 통과 / (b) 기본 체크 + 한 번 확인 | @goohong / PR 2 전 |
| Q-CONF-3 | 보호자가 약물을 수동 추가하는 권한 범위 | (a) 후보에 추가만 / (b) Medicine 직접 생성 | @goohong / PR 3 전 |
| Q-CONF-4 | confirm 후 DUR 자동 점검 | (a) 자동 트리거 / (b) 별도 사용자 액션 | @goohong / DUR 후속 |

## 9) 결정 로그
- 2026-04-16: 초안 작성
- 2026-04-16: **EXACT 포함 모든 후보에 보호자 확인** — 처방전 자체 검증(시니어 것인지·중단된 약 포함 여부 등) 의미. UX는 "한 번에 전체 확인" 패턴으로 마찰 최소화. EXACT는 기본 체크 상태.
- 2026-04-16: **72h 후 시니어 본인 확인 fallback** — 보호자 부재로 약물 등록이 영구 보류되는 것 방지.
- 2026-04-16: **자동 보정 메모는 candidate 테이블에 영구 기록** — 사후 디버깅·신뢰 추적 자료.
- 2026-04-16: 매칭 임계치·정책은 `medicine-search` spec에 위임.
