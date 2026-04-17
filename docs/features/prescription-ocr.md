---
feature: 처방전 OCR + 보호자 확인 통합 파이프라인
slug: prescription-ocr
owner: @goohong
scope: prescription
related_issues: []
related_prs: []
status: draft
last_reviewed: 2026-04-16
---

# 처방전 OCR + 보호자 확인 통합 파이프라인

## 1) 개요 (What / Why)
- 시니어가 촬영한 처방전 이미지를 받아 OCR + 텍스트 마스킹 + AI 구조화로 약물 후보 리스트를 추출한다.
- AI가 추출한 약물 후보는 인간(보호자 또는 시니어 본인) 검증 후에만 활성화한다.
- 자동 보정된 항목(OCR 오인식 → fuzzy 매칭으로 보정)은 사유 메모와 함께 강조 표시.
- 원본 이미지는 영구 보관하지 않는다 (ADR 0009, PR #141 머지됨). 마스킹본은 영구 보존.
- 약물명 → itemSeq 매칭은 별도 spec(`medicine-search`)의 `MedicineMatchService` 사용.
- 의료 안전·법적 책임 분산·UX 강화를 동시에 달성한다.
- **보호자 연동 전제**: 모든 처방전 등록 흐름은 보호자가 연동된 시니어를 기준으로 설계한다.

## 2) 사용자 시나리오
- **시니어**가 약국에서 받은 종이 처방전을 사진으로 찍어 앱에 업로드한다.
- 백엔드가 OCR + AI 구조화 처리 후 (동기, 1~3초 이내), 보호자에게 "확인 요청" 푸시 알림을 보낸다.
- **보호자**는 마스킹된 처방전 사진과 추출된 약물 리스트를 검토한다.
  - 정확히 매칭된 약물은 자동 체크 상태
  - 자동 보정된 약물은 ⚠️ 메모와 함께 표시 (예: "OCR '이부프로멘정' → '이부프로펜정 200mg'으로 자동 매칭")
  - 매칭 실패 약물은 후보 선택 또는 수동 검색
  - 잘못된 항목 삭제 가능
  - 처방전에 누락된 약물 수동 추가 가능
- "전체 확인 완료" 버튼을 누르면 약물·복약 일정 등록 화면으로 자동 이동.
- 보호자가 72h 내 미확인 시 시니어 본인 확인 가능 (보호자 부재 fallback).

## 3) 요구사항

### 기능 요구사항 — OCR 파이프라인
- [ ] `POST /api/v1/prescriptions` — 처방전 등록 트리거. 입력: 사전 업로드된 원본 이미지의 `objectKey`. 응답: `prescriptionId` + 초기 상태 `PROCESSING`.
- [ ] 백엔드는 NCP Object Storage에서 원본 이미지를 fetch해 메모리에 로드한다.
- [ ] **Clova General OCR** 호출로 텍스트 + 토큰별 좌표(boundingBox) 추출.
- [ ] **PII 영역 식별** — 정규식(주민번호·전화번호) + 키워드("환자명:", "처방의:" 다음 토큰) + 위치 휴리스틱(상단 환자정보 영역).
- [ ] **이미지 마스킹** — 식별된 좌표에 검은 박스 합성 (java.awt.Graphics2D). 마스킹본을 NCP에 별도 저장. **마스킹본은 영구 보존**.
- [ ] **텍스트 마스킹** — OCR 텍스트에서 PII 토큰 제거/대체. AI에 보내는 입력은 마스킹된 텍스트만.
- [ ] **AI 구조화** — gpt-5.4-nano(text-only)에 마스킹된 텍스트 입력. 응답: 약물 후보 리스트 (이름·용량·복약주기 raw).
- [ ] 각 약물 후보에 대해 **`MedicineMatchService.match()` 호출**로 itemSeq 자동 매칭 시도.
- [ ] 결과를 `Prescription` + `PrescriptionMedicineCandidate` 엔티티로 저장. 상태 `PENDING_REVIEW`.
- [ ] **원본 이미지 즉시 삭제** (ADR 0009). NCP 명시 삭제 + Lifecycle Policy로 24h 만료 강제.
- [ ] 처리 완료 시 보호자에게 푸시 알림 (FCM, 후속 spec 의존).
- [ ] `GET /api/v1/prescriptions/{id}` — 추출 결과 + 마스킹본 presigned GET URL 반환.
- [ ] `GET /api/v1/prescriptions?status=PENDING_REVIEW` — 내가 확인할 처방전 리스트.

### 기능 요구사항 — 보호자 확인 워크플로우
- [ ] 보호자가 시니어와 활성 `care_relations` 관계일 때 시니어의 PENDING 처방전 조회 가능.
- [ ] `GET /api/v1/prescriptions/{id}` 응답에 모든 후보(`PrescriptionMedicineCandidate`)의 matchType·매칭 사유 포함.
- [ ] **EXACT 후보**: 기본 체크 상태로 표시. 보호자가 명시 해제하면 제외(opt-out).
- [ ] **CANDIDATES 후보**: 후보 리스트 표시, 보호자 선택. 성분명 재검색 결과인 경우 사유 표시.
- [ ] **NO_MATCH 후보**: 수동 검색 UI(별도 search API), 또는 건너뛰기.
- [ ] `PATCH /api/v1/prescriptions/{id}/medicines/{candidateId}` — 보호자 결정 기록 (ACCEPTED / MANUALLY_CORRECTED / REJECTED + chosenItemSeq).
- [ ] `POST /api/v1/prescriptions/{id}/medicines` — 처방전에 누락된 약물 수동 추가.
- [ ] `POST /api/v1/prescriptions/{id}/confirm` — 전체 확인. 모든 후보의 `caregiver_decision != PENDING` 검증 후 `Prescription.status = CONFIRMED`로 전이. 활성화된 후보들로 `Medicine` row 생성·연결.
- [ ] `POST /api/v1/prescriptions/{id}/reject` — 처방전 폐기 (재촬영 요청). 마스킹본 + 후보들 모두 삭제. `Prescription.status = REJECTED`.
- [ ] **72h 미확인 처방전**: 시니어 본인이 확인 가능 (별도 권한 부여 로직).
- [ ] 모든 변경 audit log: 누가 언제 어떤 결정을 내렸는지 기록.

### 비기능 요구사항
- **응답 시간**: 등록 API는 동기. 전체 처리(OCR+AI+매칭) 5초 이내 p95 목표.
- **장애 격리**: Clova OCR 또는 GPT 실패 시 처방전 상태 `PROCESSING_FAILED` + 에러 메시지. 사용자에게 재시도 안내.
- **보안**: 원본은 메모리·임시 객체에서만. 영구 저장 0. 마스킹본은 SSE-S3 암호화. 접근통제 시니어/보호자만.
- **관측성**: 처리 단계별(OCR/마스킹/AI/매칭) 시간·성공률 로깅. PII 누출 로그 금지.
- **비용**: 처방전 1건당 약 3.4원 (Clova 3원 + gpt-5.4-nano 0.4원, VAT 별도).
- 보호자 화면 응답 < 500ms (마스킹본 presigned URL은 별도 fetch).
- 동시 편집 충돌 처리: optimistic locking (version 컬럼) 또는 마지막 쓰기 우선.
- 알림 발송 실패는 워크플로우 차단 안 함 (best effort).

## 4) 범위 / 비범위

### 포함
- 처방전 업로드·OCR·마스킹·AI 구조화·매칭·저장 전체 파이프라인
- Clova General OCR 어댑터
- gpt-5.4-nano text 모델 어댑터
- PII 마스킹(텍스트·이미지) 로직
- `Prescription`·`PrescriptionMedicineCandidate` 엔티티 신설
- 마스킹본 NCP 저장 (영구 보존, presigned GET 별도 후속 이슈)
- 보호자 액션 API (확인/수정/거부/추가)
- 매칭 사유 메모 표시 데이터 구조
- 72h 미확인 fallback 로직
- 확인 완료 시 `Medicine` 엔티티 생성 연동
- audit log 테이블/필드

### 제외 (Out of Scope)
- **`MedicineSearchService` / `MedicineMatchService` 구현** → `medicine-search` spec
- **MCP tool 노출** → `mcp-server-foundation` spec
- **푸시 알림(FCM) 인프라** → 후속 spec
- **원본 이미지 영구 보관** (ADR 0009로 명시 제외)
- **OCR 학습 데이터셋 구축** — 별도 동의 흐름 + 가명처리 spec 필요
- **GPT vision 사용** — Clova OCR 텍스트로 충분, vision 불필요로 결정
- **복약 일정 등록 UI 흐름** → 기존 `medication-schedule-crud` spec 재사용
- **DUR 자동 점검 트리거** → 본 spec에서는 hook만, 동작은 `medicine-dur` spec
- **약물 검색·매칭 알고리즘** → `medicine-search` spec

## 5) 설계

### 5-1) 도메인 모델

**신규 엔티티 — `Prescription`** (`com.ppiyaki.prescription`)
```
id                         bigint PK
owner_id                   bigint     // 시니어 user_id
status                     enum       // PROCESSING / PENDING_REVIEW / CONFIRMED / REJECTED / PROCESSING_FAILED
masked_image_object_key    varchar    // NCP objectKey, presigned GET 발급용
failure_reason             text nullable
created_at / updated_at              // BaseTimeEntity
```

**신규 엔티티 — `PrescriptionMedicineCandidate`**
```
id                          bigint PK
prescription_id             bigint FK
ocr_raw_text                varchar    // OCR 원문 (예: "이부프로멘정 200mg, 1일 3회")
extracted_name              varchar    // AI 파싱 약물명
extracted_dosage            varchar nullable
extracted_schedule          varchar nullable
matched_item_seq            varchar nullable  // MedicineMatchService 결과
matched_item_name           varchar nullable
match_type                  enum       // EXACT / CANDIDATES / NO_MATCH
match_reason                text nullable
caregiver_decision          enum       // PENDING / ACCEPTED / REJECTED / MANUALLY_CORRECTED
caregiver_chosen_item_seq   varchar nullable  // 보호자가 다른 약물 선택 시
reviewed_at                 datetime nullable
created_medicine_id         bigint nullable FK to medicines
created_at
```

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/v1/prescriptions` | 처방전 등록 (objectKey 입력, 동기 처리) | 필수 |
| GET | `/api/v1/prescriptions/{id}` | 추출 결과 + 후보 + matchType별 메모 + 마스킹본 presigned URL | 필수 (소유자/보호자) |
| GET | `/api/v1/prescriptions?status=...` | 내가 확인할/확인한 처방전 리스트 | 필수 |
| PATCH | `/api/v1/prescriptions/{id}/medicines/{candidateId}` | 후보별 보호자 결정 | 필수 |
| POST | `/api/v1/prescriptions/{id}/medicines` | 누락 약물 수동 추가 | 필수 |
| POST | `/api/v1/prescriptions/{id}/confirm` | 전체 확인 → CONFIRMED, Medicine 생성 | 필수 |
| POST | `/api/v1/prescriptions/{id}/reject` | 폐기 | 필수 |

### 5-3) 외부 연동

#### Clova General OCR
- 엔드포인트: NCP CLOVA OCR Invoke URL (도메인별 발급)
- 인증: `X-OCR-SECRET` 헤더, 환경변수 `CLOVA_OCR_SECRET` + `CLOVA_OCR_INVOKE_URL`
- 요청: 이미지 base64 또는 multipart, 타입 명시
- 응답: 토큰 단위 텍스트 + boundingBox 좌표
- 단가: **3원/건** (글자 추출 General, VAT 별도). 무료 100건/월
- 타임아웃: 연결 2s / 읽기 5s
- 실패 처리: `Prescription.status = PROCESSING_FAILED` + reason 기록

#### OpenAI gpt-5.4-nano (text)
- 엔드포인트: `https://api.openai.com/v1/chat/completions`
- 인증: `OPENAI_API_KEY` 환경변수
- 입력: 시스템 prompt(약물 추출 지시) + 마스킹된 OCR 텍스트
- 출력: JSON Mode 활성화로 약물 후보 리스트 강제
- 모델: **gpt-5.4-nano** (잠정 확정). 단가: 입력 $0.20/1M, 출력 $1.25/1M → 건당 약 0.4원
- 타임아웃: 10s (LLM은 5s보다 여유)
- 실패 처리: 동일

### 5-4) 데이터 흐름

```
[Client] 처방전 사진 촬영
   │
   │ 1. presigned PUT URL 발급 (POST /api/v1/uploads/presigned)
   │ 2. NCP Object Storage `upload/prescription/` prefix에 업로드 (Lifecycle 24h 만료)
   ▼
[Client] objectKey 획득
   │
   │ 3. POST /api/v1/prescriptions {objectKey}
   ▼
[PrescriptionController]
   │
   │ 4. 처방전 row 생성 (status=PROCESSING) → 동기 처리 시작
   │
   ▼
[PrescriptionProcessingService] (동기)
   │
   │ 5. NCP에서 원본 이미지 fetch
   │ 6. ClovaOcrClient.ocr(image) → text + bboxes
   │ 7. PiiMaskingService.identifyPii(text, bboxes) → [bbox]
   │ 8. ImageMaskingService.mask(image, [bbox]) → maskedImage
   │ 9. NCP `masked/prescription/` prefix에 마스킹본 저장 (영구)
   │ 10. PiiMaskingService.maskText(text, [bbox]) → maskedText
   │ 11. OpenAiClient.extractMedicines(maskedText) → [extractedItem]
   │
   │ FOR each extractedItem:
   │   12. medicineMatchService.match(item.name, item.dosage, item.form)
   │       → MatchResult (matchType, recommended, candidates, reason)
   │   13. PrescriptionMedicineCandidate row 생성
   │
   │ 14. Prescription.status = PENDING_REVIEW
   │ 15. 원본 이미지 NCP 명시 삭제 (이중 안전: Lifecycle 24h 만료도 활성)
   │ 16. 보호자 push 알림 발송 (NotificationService, 미구현 시 스킵)
   │ 17. 201 응답 반환 (prescriptionId + status)
   ▼
[보호자] GET /api/v1/prescriptions?status=PENDING_REVIEW → 확인 화면 진입
```

### 5-5) 보호자 결정 → Medicine 생성 흐름

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

### 5-6) 상태 머신

```
[PROCESSING] (OCR 진행 중 — 동기)
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

### 5-7) DB 마이그레이션
- 신규 테이블: `prescriptions` (위 엔티티 정의)
- 신규 테이블: `prescription_medicine_candidates`
- 인덱스:
  - `prescriptions(owner_id, status, created_at DESC)` — 사용자별 리스트 조회
  - `prescription_medicine_candidates(prescription_id)`
- `prescription_medicine_candidates`에 `created_medicine_id` (bigint nullable, FK to medicines) 컬럼 포함
- (선택) `prescription_review_logs` 테이블 신설 — append-only audit
  ```
  id, prescription_id, candidate_id nullable, reviewer_user_id,
  action enum (ACCEPT/REJECT/CORRECT/ADD/CONFIRM/REJECT_PRESCRIPTION),
  before_value text nullable, after_value text nullable, created_at
  ```

## 6) 작업 분할 (예상 PR 리스트)

선행 의존: `medicine-search` spec/PR 머지 (MatchService 필요)

> **구현 순서 (전체 spec 기준)**:
> 1. `item_seq` 컬럼 추가 (`medicine-dur` PR 2)
> 2. `medicine-search` 구현 (+ `medicine-dur` 병렬 가능)
> 3. `medicine-dur` 구현
> 4. **본 spec (prescription-ocr 통합본)** ← 현재
> 5. `mcp-server-foundation` 구현

- [ ] PR 1 `docs(prescription)`: 본 spec 커밋
- [ ] PR 2 `chore(infra)`: `CLOVA_OCR_SECRET` / `CLOVA_OCR_INVOKE_URL` / `OPENAI_API_KEY` 환경변수 추가. `ClovaOcrProperties`, `OpenAiProperties` (`@Validated` + `@ConditionalOnProperty`). `application*.yml`, `.env.example`, `backend-cd.yml`. NCP Lifecycle Policy 설정 가이드 README. `needs-human-review`.
- [ ] PR 3 `feat(prescription)`: `Prescription`/`PrescriptionMedicineCandidate` 엔티티 + Repository
- [ ] PR 4 `feat(infra)`: `ClovaOcrClient` 어댑터 + 단위 테스트 (mock)
- [ ] PR 5 `feat(infra)`: `OpenAiClient`(text completions, gpt-5.4-nano) 어댑터 + 단위 테스트 (mock)
- [ ] PR 6 `feat(prescription)`: `PiiMaskingService` (텍스트·이미지 마스킹). 정규식 + 키워드 기반. 단위 테스트.
- [ ] PR 7 `feat(prescription)`: `PrescriptionProcessingService` 통합 + `PrescriptionController`. 동기 처리. E2E 테스트.
- [ ] PR 8 `feat(prescription)`: 후보별 PATCH·삭제·추가 API + Service. `caregiver_decision` 전이 검증.
- [ ] PR 9 `feat(prescription)`: confirm/reject API + Medicine 생성 연동. 권한(소유자/보호자) 검증.
- [ ] PR 10 `feat(prescription)`: 72h fallback — 시니어 본인 확인 권한 활성화 로직 (`@Scheduled` 배치 또는 요청 시점 계산).
- [ ] PR 11 `feat(prescription)`: audit log (선택, MVP에서는 candidate row의 변경 시각만 활용 후 후속 PR로 분리 가능).
- [ ] PR 12 `test(prescription)`: E2E — 보호자 확인 / 시니어 자동 fallback / 권한 실패 / 잘못된 상태 전이.
- [ ] PR 13 `chore(infra)`: NCP Object Storage Lifecycle Policy `upload/prescription/` prefix에 24h 만료 규칙 설정 (NCP 콘솔 작업)

## 7) 테스트 전략

### 단위
- `ClovaOcrClient` — 요청 형식, 응답 파싱, 실패 분기
- `OpenAiClient` — JSON Mode 검증, 응답 파싱, 토큰 한도
- `PiiMaskingService` — 정규식 정확도(주민번호/전화/면허번호), 키워드 매칭, bbox 계산
- `ImageMaskingService` — 박스 좌표 합성 정확도
- 상태 머신 전이 검증 (잘못된 전이 reject)
- 권한 검증 (소유자/보호자/72h fallback)
- candidate.caregiver_decision 변경 감사

### 통합 (E2E)
- `@SpringBootTest`로 mock OCR/OpenAI 빈 주입
- 정상 처방전 → 후보 N개 + status=PENDING_REVIEW
- OCR 실패 → status=PROCESSING_FAILED + 사유 기록
- 권한 없는 사용자 조회 → 403
- 마스킹본 presigned URL 발급 검증
- 정상 흐름: OCR → PENDING_REVIEW → 보호자 confirm → CONFIRMED → Medicine 생성 확인
- MANUALLY_CORRECTED: 보호자가 다른 itemSeq 선택 → Medicine.itemSeq에 chosen 값 반영
- 누락 약물 추가: 보호자가 수동으로 약물 1개 추가 → Medicine 생성 확인
- 72h 후 시니어 확인: 보호자 미확인 상태에서 시니어 confirm 시도 → 성공
- 권한 실패: 다른 사용자가 confirm 시도 → 403

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q-OCR-2 | OpenAI 모델 확정 | gpt-5.4-nano 잠정 확정. OpenAI 라인업 변경 시 재검토 | @goohong / PR 5 전 |
| Q-OCR-3 | 마스킹본 보존 기간 | **영구 보존** (결정됨 — §9 참조) | ✅ 결정 |
| Q-CONF-3 | 보호자가 약물을 수동 추가하는 권한 범위 | (a) 후보에 추가만 / (b) Medicine 직접 생성 | @goohong / PR 9 전 |
| Q-CONF-4 | confirm 후 DUR 자동 점검 | (a) 자동 트리거 / (b) 별도 사용자 액션 | @goohong / DUR 후속 |
| Q-OCR-5 | 회전된 처방전 이미지 대응 | EXIF orientation 보정 또는 이미지 분석 자동 회전. 현재 90도 회전 이미지는 OCR 텍스트는 추출되나 바운딩박스 좌표가 이미지 픽셀과 불일치하여 PII 마스킹 위치가 틀어짐 | TODO |
| Q-OCR-6 | PII 키워드 마스킹 범위 | 현재 키워드 다음 토큰 1개만 마스킹. 같은 줄 전체 마스킹으로 개선 필요 (주소 등 여러 토큰에 걸친 PII 누락) | TODO |

## 9) 결정 로그
- 2026-04-16: 초안 작성 (`prescription-ocr.md` + `prescription-confirmation.md` 분리 초안)
- 2026-04-16: 원본 이미지 영구 미보관 (ADR 0009)
- 2026-04-16: OCR = Clova General OCR, AI = gpt-4o-mini text only (vision 미사용). 근거: Clova가 한국어 인쇄 텍스트 정확도 우위, vision 추가는 비용·복잡도 증가 대비 이득 미미 (→ gpt-5.4-nano로 변경, 하단 참조)
- 2026-04-16: 비용 추정 약 3.3원/건 (Clova 3원 + GPT 0.3원) — 초기 추정치 (→ 3.4원으로 갱신, 하단 참조)
- 2026-04-16: PII 마스킹은 **이미지·텍스트 둘 다** 수행. 이미지 마스킹은 보호자 검증용, 텍스트 마스킹은 LLM 입력용
- 2026-04-16: 약물 매칭은 `medicine-search` spec의 `MedicineMatchService`에 위임 (책임 분리)
- 2026-04-16: **EXACT 포함 모든 후보에 보호자 확인** — 처방전 자체 검증(시니어 것인지·중단된 약 포함 여부 등) 의미. UX는 "한 번에 전체 확인" 패턴으로 마찰 최소화. EXACT는 기본 체크 상태.
- 2026-04-16: **72h 후 시니어 본인 확인 fallback** — 보호자 부재로 약물 등록이 영구 보류되는 것 방지.
- 2026-04-16: **자동 보정 메모는 candidate 테이블에 영구 기록** — 사후 디버깅·신뢰 추적 자료.
- 2026-04-16: 매칭 임계치·정책은 `medicine-search` spec에 위임.
- 2026-04-16: **동기 처리 확정** (Q-OCR-1 해소) — MVP 단계에서 `@Async` 미도입. 등록 API가 OCR+AI+매칭 완료 후 201 반환. 5s p95 달성 가능, 복잡도 감소.
- 2026-04-16: **약물 수 무제한** (Q-OCR-4 해소) — 처방전 1건당 약물 수 상한 없음. 식약처 처방전 현실 상 30개 이내가 대부분이므로 인위적 상한 불필요.
- 2026-04-16: **AI 모델 gpt-5.4-nano 잠정 확정** (Q-OCR-2 부분 해소) — OpenAI 라인업 변경으로 gpt-5.4-nano 채택. 단가: 입력 $0.20/1M, 출력 $1.25/1M → 건당 약 0.4원. 비용 추정 갱신: 3.4원/건 (Clova 3원 + gpt-5.4-nano 0.4원).
- 2026-04-16: **마스킹본 영구 보존 확정** (Q-OCR-3 해소) — 보호자 검증 UI 재표시·사후 감사·법적 증거자료 용도. 사용자 탈퇴 시 처리는 별도 정책 PRi.
- 2026-04-16: **spec 통합** — `prescription-confirmation.md`를 본 spec에 흡수. 처방전 OCR과 보호자 확인은 단일 파이프라인 feature이므로 분리 관리 불필요.
- 2026-04-16: **보호자 연동 전제** (Q-CONF-1 해소) — 처방전 등록 흐름은 항상 보호자가 연동된 시니어 기준. 보호자 없는 시니어 단독 흐름은 현 MVP 범위 밖.
- 2026-04-16: **EXACT 후보 처리 방식** (Q-CONF-2 해소) — 자동 통과(opt-out 없이) 대신 "기본 체크 + 한 번 확인" 패턴 채택. 보호자가 전체 목록 한 번 보고 확인하는 UX.
- 2026-04-16: **구현 순서 확정** — ① item_seq 컬럼 추가 → ② medicine-search (+ medicine-dur 병렬 가능) → ③ medicine-dur → ④ prescription-ocr 통합본 → ⑤ mcp-server-foundation.
- 2026-04-16: **Prescription 엔티티에서 ai_model, processed_at, ocr_raw_text 제거** — ai_model은 config/git history로 추적, processed_at은 동기 처리라 created_at과 동일, ocr_raw_text는 candidate별 원문으로 충분하고 전체 원문 보관은 추가 PII 리스크.
- 2026-04-16: **PrescriptionMedicineCandidate에서 match_similarity, caregiver_note 제거** — similarity는 match_reason에 사람 가독형 포함, note는 MVP 불필요(행위 자체가 의사 표현).
- 2026-04-16: **NCP Object Storage prefix 네이밍 확정** — 원본(24h 만료): `upload/prescription/{userId}/{uuid}.{ext}`, 마스킹본(영구): `masked/prescription/{userId}/{uuid}.{ext}`. Lifecycle Policy는 `upload/prescription/` prefix에 1일 만료 규칙. `UploadPurpose` enum에 `PRESCRIPTION_TEMP` / `PRESCRIPTION_MASKED` 추가.
- 2026-04-16: **PII 마스킹 패턴 선제 확장** — 주민번호/전화번호/이름키워드("환자명","성명","수진자","처방의","의사","약사") 외 면허번호/주소키워드/보험번호/이메일/생년월일까지 포함. Phase 1에서 넓게 적용, 오탐 시 축소.
