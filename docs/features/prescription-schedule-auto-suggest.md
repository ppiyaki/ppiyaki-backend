---
feature: 처방전 confirm 시 복약 일정 자동 제안 (시간대 Phase 3)
slug: prescription-schedule-auto-suggest
status: done
owner: @goohong
scope: prescription
related_issues: []
related_prs: [#228, #229]
last_reviewed: 2026-05-07
---

# 처방전 confirm 시 복약 일정 자동 제안 (시간대 Phase 3)

> 시간대 기능 Phase 3. 선행 스펙: `meal-time-defaults.md`(Phase 1, done), `medication-schedule-meal-slot.md`(Phase 2, done).
> 처방전 spec과 동시에 영향: `prescription-ocr.md`.

## 1) 개요 (What / Why)
- 보호자/시니어가 처방전 OCR confirm 시, **약별로 식사 슬롯(BREAKFAST/LUNCH/DINNER)을 함께 확정**하면 백엔드가 `MedicationSchedule`을 자동 생성한다.
- 슬롯 후보는 OCR 추출 시점에 LLM이 제안한다 (예: "1일 3회 식후" → `[BREAKFAST, LUNCH, DINNER]`). 보호자가 그대로 수락하거나 수정 가능.
- **Why**: 현재는 confirm 후 보호자가 약마다 schedule CRUD UI를 따로 거쳐 슬롯·dosage를 다시 입력. 처방전 OCR이 이미 복약주기 텍스트(`extractedSchedule`)를 추출하고 있어 LLM이 슬롯까지 매핑하면 마찰을 한 단계 줄일 수 있다.
- **Safety**: 자동 적용이 아니라 "제안 + 보호자 확정" 모델. LLM 잘못 매핑 시 시니어가 잘못된 시각에 알림 받는 사고 위험을 보호자 검수가 차단한다.

대상 사용자: 보호자(다중 약 처방전 검수가 잦음), 시니어 본인(자율형 모드).

## 2) 사용자 시나리오
- **표준 흐름 (식후 3회)**: 보호자가 처방전 사진 업로드 → OCR 결과에 약 3종 표시. 약 1번 candidate에 `suggestedMealSlots: [BREAKFAST, LUNCH, DINNER]` 자동 채워짐. 보호자가 그대로 ACCEPTED 결정 → confirm. 백엔드가 약 1번에 대해 schedule 3건(아침·점심·저녁) 자동 생성.
- **수정 흐름**: LLM이 "1일 2회 식후"를 `[BREAKFAST, DINNER]`로 매핑했지만 보호자가 점심·저녁이 맞다고 판단. PATCH로 `confirmedMealSlots: [LUNCH, DINNER]` 갱신 → confirm 시 점심·저녁 schedule 생성.
- **무관 약 흐름**: "취침 전" 약은 LLM이 `mealSlots: []` (빈 배열) 반환. candidate `suggestedMealSlots`도 빈 값. confirm 시 해당 약은 schedule 자동 생성 skip(Medicine만 생성). 보호자가 후속으로 수동 schedule 등록.
- **mealTimes 미설정 흐름**: 시니어 mealTimes 미설정 상태에서 confirm 시 비어있는 슬롯이 하나라도 schedule 생성 시도되면 400 `MEAL_TIMES_NOT_SET` 반환 → 보호자가 시니어에게 mealTimes 설정 요청 후 재시도.

## 3) 요구사항
### 기능 요구사항
- [x] `OpenAiClient.ExtractedMedicine`에 `mealSlots: List<MealSlot>` 필드 추가, 시스템 프롬프트에 슬롯 매핑 규칙 추가 (PR #229)
- [x] LLM 응답 파싱: `mealSlots`가 누락/null이면 빈 리스트로 처리. 잘못된 enum 값(예: "BEDTIME")은 필터링 (PR #229)
- [x] `PrescriptionMedicineCandidate`에 `suggested_meal_slots`, `confirmed_meal_slots` (둘 다 VARCHAR CSV, nullable) 컬럼 신설 (PR #229)
- [x] OCR 처리(`PrescriptionService.processAndCreate`)에서 LLM `mealSlots`를 `suggestedMealSlots`로 저장 (PR #229)
- [x] `PrescriptionMedicineCandidateResponse`에 `suggestedMealSlots`, `confirmedMealSlots` 노출 (PR #229)
- [x] `PATCH /api/v1/prescriptions/{id}/medicines/{candidateId}` 본문에 `confirmedMealSlots: List<MealSlot>` 추가 (선택, 기존 결정 갱신과 별도 갱신 가능) (PR #229)
- [x] `POST /api/v1/prescriptions/{id}/confirm`: ACCEPTED/MANUALLY_CORRECTED candidate 중 `confirmedMealSlots`가 비어있지 않으면 슬롯별로 `MedicationSchedule` 자동 생성 (dosage는 candidate `extractedDosage` 사용, daysOfWeek=`DAILY`, startDate=오늘, endDate=null) (PR #230)
- [x] confirm 시 시니어 mealTimes의 해당 슬롯이 null이면 400 `MEAL_TIMES_NOT_SET`로 거절 — 트랜잭션 변경 시작 전 사전 검증으로 구현(전체 롤백). `MedicationScheduleService.create` 검증과 동일 의미. (PR #230)
- [x] confirm 호출의 멱등성: 동일 candidate 재confirm 시 `created_medicine_id`가 이미 있으면 Medicine·schedule 중복 생성 방지 (PR #230). (medicineId, mealSlot) UNIQUE 인덱스는 본 spec 외 follow-up
- [x] 도메인 문서 §4 유비쿼터스 랭귀지에 "제안 슬롯 / 확정 슬롯" 등재 (PR #229)
- [x] 도메인 문서 §5 `prescription_medicine_candidates` 컬럼 추가 (PR #229)
- [x] `schema.sql` 동기화 + prod 마이그레이션 SQL — `prescription_medicine_candidates`는 `clova.ocr.secret` ConditionalOnProperty로 schema.sql에서 제외되므로 DDL은 prod 마이그레이션 SQL로만 관리 (§5-5)
- [x] Notion API 명세: candidate 응답·PATCH 본문·confirm 동작 변경 — 사용자 사이클 안에서 동기화

### 비기능 요구사항
- **성능**: confirm은 ACCEPTED candidate × 슬롯 수만큼 schedule INSERT. 평균 3종 약 × 평균 2슬롯 = 6 INSERT. 추가 비용 미미.
- **신뢰성**: LLM 슬롯 매핑은 보조 신호. 보호자가 모든 schedule을 검수·수정할 수 있어야 한다(Phase 2 schedule CRUD API 그대로 활용).
- **보안**: 의료정보 슬롯 정보는 일반 데이터로 취급. 로그에 슬롯 노출 OK.
- **관측성**: LLM 슬롯 추출 성공률(`mealSlots` non-empty 비율) 운영 메트릭으로 추후 검토. 본 PR 범위 외.

## 4) 범위 / 비범위 (중요)

### 포함
- LLM 프롬프트 확장 + `ExtractedMedicine.mealSlots` 추가
- candidate 컬럼 2개 신설(suggested/confirmed)
- candidate 응답·PATCH·confirm API 동작 변경
- `MedicationScheduleRepository`에 (medicineId, mealSlot) 중복 방지용 조회 메서드 추가
- 단위 + E2E 테스트 (LLM mock으로 슬롯 후보 주입, confirm 후 schedule 검증)
- 도메인 문서 + Notion API 명세 동기화

### 제외 (Out of Scope)
- **`MealSlot` enum 확장 (BEDTIME, FASTING 등)** — 본 spec 범위 외. mealSlots=[]이면 schedule 자동 생성 skip하고 보호자 수동 등록으로 우회.
- **dosage 자동 보정** — `extractedDosage`가 null이면 schedule 자동 생성 skip. 보호자가 수동 등록.
- **요일 패턴 자동 매핑(월수금 등)** — daysOfWeek=`DAILY` 고정. 주 단위 패턴은 OCR 데이터 분포 보고 후속.
- **시작일/종료일 자동 산출(예: "7일분")** — startDate=오늘, endDate=null 고정. 처방 일수 추출은 후속.
- **자동 적용 (suggestion 없이 강제 생성)** — Safety 위반.
- **LLM 슬롯 추출 정확도 모니터링 메트릭** — 별도 관측성 spec.
- **시니어 권한으로 confirm 호출 시 추가 검증** — 기존 `validateMutationAccess` 그대로.
- **mealTimes 미설정 시 graceful fallback** — 명시 400 반환. 부분 자동 생성 분기 도입 안 함.

## 5) 설계

### 5-1) 도메인 모델
- 컨텍스트: `prescription` (주) + `medication`(파급)
- `PrescriptionMedicineCandidate`에 `suggestedMealSlots`(LLM 제안 CSV), `confirmedMealSlots`(보호자 확정 CSV) 추가
- `MealSlot` enum, `MedicationSchedule`, `MedicationScheduleService.create` 검증 그대로 재사용

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/prescriptions | OCR 생성. candidate 응답에 `suggestedMealSlots` 포함 | 필수 | 기존 | 기존 + 필드 |
| GET | /api/v1/prescriptions/{id} | candidate 응답 동일 확장 | 필수 | — | 기존 + 필드 |
| PATCH | /api/v1/prescriptions/{id}/medicines/{candidateId} | candidate 결정/슬롯 갱신 | 필수 | `CandidateDecisionRequest`(확장) | 200 |
| POST | /api/v1/prescriptions/{id}/medicines | 수동 약 추가. `mealSlots` 선택 입력 | 필수 | `PrescriptionMedicineAddRequest`(확장) | 201 |
| POST | /api/v1/prescriptions/{id}/confirm | confirm + candidate별 schedule 자동 생성 | 필수 | — | 기존 |

#### CandidateDecisionRequest (확장)
```json
{
  "decision": "ACCEPTED",
  "chosenItemSeq": null,
  "confirmedMealSlots": ["BREAKFAST", "LUNCH", "DINNER"]
}
```
- `confirmedMealSlots`: optional. 누락이면 기존 값 유지(첫 PATCH면 null). decision이 ACCEPTED/MANUALLY_CORRECTED일 때만 의미 있음.

#### PrescriptionMedicineCandidateResponse (확장)
```json
{
  "id": 12,
  "extractedName": "타이레놀정500밀리그람",
  "extractedDosage": "1정",
  "extractedSchedule": "1일 3회 식후",
  "suggestedMealSlots": ["BREAKFAST", "LUNCH", "DINNER"],
  "confirmedMealSlots": null,
  "caregiverDecision": "PENDING",
  ...
}
```

#### confirm 동작 변경
기존: ACCEPTED/MANUALLY_CORRECTED candidate → Medicine 생성.
신규: 위에 더해, candidate `confirmedMealSlots`가 비어있지 않으면 슬롯별 `MedicationSchedule` 생성:
- `medicineId` = 방금 생성한 Medicine
- `mealSlot` = candidate `confirmedMealSlots`의 각 원소
- `dosage` = candidate `extractedDosage` (null이면 schedule 생성 skip + 응답 메시지로 안내)
- `daysOfWeek` = "DAILY"
- `startDate` = 오늘
- `endDate` = null

#### 에러 응답
| 상황 | HTTP | code |
|---|---|---|
| confirm 시 시니어 mealTimes의 해당 슬롯 미설정 | 400 | `USER_002` (`MEAL_TIMES_NOT_SET`) |
| `confirmedMealSlots`에 잘못된 enum 값 | 400 | `COMMON_001` |
| 인증/권한 | 기존 동일 | — |

### 5-3) 외부 연동
- OpenAI gpt-5.4-nano(text-only). 프롬프트만 확장, 추가 호출 없음.

### 5-4) 데이터 흐름
```
[OCR 단계] (변경)
PrescriptionService.processAndCreate
  → OpenAiClient.extractMedicines(maskedText)
       → 응답에 mealSlots 포함
  → 각 candidate에 suggestedMealSlots 저장 (CSV)

[보호자 검수 단계] (확장)
PATCH /prescriptions/{id}/medicines/{candidateId}
  body: { decision, chosenItemSeq, confirmedMealSlots }
  → candidate.update(decision, chosenItemSeq, confirmedMealSlots)

[confirm 단계] (확장)
POST /prescriptions/{id}/confirm
  → 모든 candidate decision=PENDING 아님 검증
  → ACCEPTED/MANUALLY_CORRECTED candidate 순회
       → Medicine 생성 (기존)
       → candidate.confirmedMealSlots 비어있지 않고 dosage 있음:
            → 각 슬롯에 대해 MedicationSchedule 생성
                 (시니어 mealTimes 검증 → 미설정 시 400 즉시 throw)
       → candidate.linkMedicine
  → prescription.confirm()
```

### 5-5) DB 마이그레이션

```sql
-- prod
ALTER TABLE prescription_medicine_candidates
    ADD COLUMN suggested_meal_slots VARCHAR(64) NULL,
    ADD COLUMN confirmed_meal_slots VARCHAR(64) NULL;
```
- CSV 형식. 예: `"BREAKFAST,LUNCH,DINNER"`. 길이 64 = 3슬롯 × ~10자 + 여유.
- `schema.sql` 동기화.

### 5-6) 코드 영향 범위
| 파일 | 변경 |
|---|---|
| `common/ai/OpenAiClient.java` | 시스템 프롬프트에 mealSlots 매핑 규칙. `ExtractedMedicine` record에 `List<MealSlot> mealSlots` 추가. 응답 파싱에 enum 검증 |
| `prescription/PrescriptionMedicineCandidate.java` | 컬럼 2개 + setter/도메인 메서드 |
| `prescription/PrescriptionMedicineCandidateResponse.java` | 응답 필드 2개 |
| `prescription/controller/dto/CandidateDecisionRequest.java` | `confirmedMealSlots` 추가 |
| `prescription/service/PrescriptionService.java` | OCR 저장 로직에 suggestedMealSlots 저장. confirm에 schedule 생성 분기. mealTime 검증은 `MedicationScheduleService` 또는 동일 패턴으로 인라인 |
| `medication/repository/MedicationScheduleRepository.java` | (선택) (medicineId, mealSlot) 중복 방지 조회 메서드. 멱등 보장용 |
| `resources/schema.sql` | 컬럼 2개 추가 |
| `docs/ai-harness/06-domain-model.md` | §4 유비쿼터스 랭귀지, §5 prescription_medicine_candidates 컬럼 표 |
| `test/.../PrescriptionServiceTest.java` 또는 `PrescriptionControllerE2ETest.java` | confirm 시 schedule 자동 생성 검증, mealTimes 미설정 400 |

## 6) 작업 분할 (예상 PR 리스트)
- [x] PR 1 (`docs(prescription)`): 본 spec — PR #228
- [x] PR 2 (`feat(prescription)`): LLM 프롬프트 + candidate 컬럼 + suggestedMealSlots 응답 노출 + PATCH 갱신 — PR #229
- [x] PR 3 (`feat(prescription)`): confirm 시 schedule 자동 생성 + 멱등 보장 + E2E — PR #230

PR 2/3 분리 이유: PR 2는 prod 동작 변경 없이 데이터/응답만 확장 (사용자 기존 흐름 유지). PR 3는 confirm 동작 변경(schedule 자동 생성). 각각 독립 검증 가능.

## 7) 테스트 전략
- **단위 테스트**:
  - `OpenAiClient`: 응답에 mealSlots 누락/잘못된 enum 시 빈 리스트로 정규화
  - `PrescriptionMedicineCandidate.update*`: confirmedMealSlots 갱신 도메인 검증
  - `PrescriptionService.confirm`: candidate confirmedMealSlots 기반 schedule 생성 mock
- **E2E (RestAssured)**:
  - 처방전 생성 → candidate 응답에 suggestedMealSlots 포함 확인 (LLM mock)
  - PATCH로 confirmedMealSlots 갱신 → 응답 확인
  - mealTimes 설정한 시니어 confirm → schedule N건 생성 확인 (DB 직접 조회)
  - mealTimes 미설정 시니어 confirm → 400 USER_002
  - confirm 재호출 시 schedule 중복 생성 안 됨 (멱등)
- LLM 응답은 mock. 실제 OpenAI 호출 안 함.

## 8) 오픈 질문
| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q1 | confirmedMealSlots 입력 시점 | (a) PATCH 본문 (Recommended, 확정) / (b) confirm 본문에 candidate 배열 / (c) 두 개 다 허용 | 결정 — (a) PATCH 본문. confirm은 트리거 단순화 |
| Q2 | candidate `extractedDosage`가 null인 경우 | (a) schedule 자동 생성 skip + 응답에 안내 (Recommended) / (b) 400으로 confirm 거절 | 결정 — (a). 보호자가 후속 schedule 수동 등록으로 보완 |
| Q3 | confirm 멱등 보장 방식 | (a) candidate.created_medicine_id 있으면 전체 skip / (b) (medicineId, mealSlot) UNIQUE 인덱스 / (c) 둘 다 | 결정 — (a) 우선. UNIQUE 인덱스는 보강 검토(스키마 변경 별도) |
| Q4 | LLM 슬롯 매핑 정확도 검증 | (a) 운영 트래픽 보고 추후 / (b) 본 spec에 정확도 메트릭 포함 | (a). 본 spec 외. mismatch 발견 시 프롬프트 튜닝 follow-up |

## 9) 결정 로그
- 2026-05-07: 초안 작성 (status=draft). 시간대 Phase 3 = 처방전 confirm 자동 schedule 생성 + LLM 슬롯 매핑.
- 2026-05-07: **매핑 = LLM 프롬프트 확장**. 정규식 휴리스틱 미채택. 이유: OCR이 이미 schedule 텍스트 추출 중이라 nano 추가 호출 없이 같은 응답에 슬롯 포함 가능.
- 2026-05-07: **suggestion + 보호자 확정**. 자동 적용 미채택. 이유: 잘못 매핑 시 시니어 알림 시각 오류 → 안전 우선. 보호자 검수가 차단막.
- 2026-05-07: **mealSlots=[] (식사 무관 약)**. schedule 자동 생성 skip + Medicine만 생성. 보호자 수동 schedule 등록으로 보완. BEDTIME 등 enum 확장은 본 spec 외.
- 2026-05-07: **dosage null이면 schedule skip**. confirm 자체는 성공. 응답에 안내 정보 포함 검토(Q2 후속).
- 2026-05-07: **confirmedMealSlots 입력 = PATCH**. confirm은 트리거. 흐름 분리 명확. UI는 candidate 검수 화면에서 PATCH 호출.
- 2026-05-07: **mealTimes 미설정 시 400 즉시**. graceful fallback 없음. 보호자가 시니어 mealTimes 설정 후 재시도.
