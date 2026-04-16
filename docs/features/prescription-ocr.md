---
feature: 처방전 OCR 및 약물 추출 파이프라인
slug: prescription-ocr
owner: @goohong
scope: prescription
related_issues: []
related_prs: []
status: draft
last_reviewed: 2026-04-16
---

# 처방전 OCR 및 약물 추출 파이프라인

## 1) 개요 (What / Why)
- 시니어가 촬영한 처방전 이미지를 받아 OCR + 텍스트 마스킹 + AI 구조화로 약물 후보 리스트를 추출한다.
- 추출된 후보는 보호자 확인 워크플로우(`prescription-confirmation`)로 넘어간다.
- 원본 이미지는 영구 보관하지 않는다 (ADR 0009).
- 약물명 → itemSeq 매칭은 별도 spec(`medicine-search`)의 `MedicineMatchService` 사용.

## 2) 사용자 시나리오
- **시니어**가 약국에서 받은 종이 처방전을 사진으로 찍어 앱에 업로드한다.
- 백엔드가 1~3초 내 OCR + AI 구조화 처리 후, 보호자에게 "확인 요청" 푸시 알림을 보낸다.
- **보호자**는 마스킹된 처방전 이미지와 추출된 약물 리스트를 검토해 확정한다.
- AI 모델·OCR이 잘못 인식한 부분은 보호자가 수정하며, 자동 보정된 항목은 메모와 함께 표시된다.

## 3) 요구사항

### 기능 요구사항
- [ ] `POST /api/v1/prescriptions` — 처방전 등록 트리거. 입력: 사전 업로드된 원본 이미지의 `objectKey`. 응답: `prescriptionId` + 초기 상태 `PROCESSING`.
- [ ] 백엔드는 NCP Object Storage에서 원본 이미지를 fetch해 메모리에 로드한다.
- [ ] **Clova General OCR** 호출로 텍스트 + 토큰별 좌표(boundingBox) 추출.
- [ ] **PII 영역 식별** — 정규식(주민번호·전화번호) + 키워드("환자명:", "처방의:" 다음 토큰) + 위치 휴리스틱(상단 환자정보 영역).
- [ ] **이미지 마스킹** — 식별된 좌표에 검은 박스 합성 (java.awt.Graphics2D). 마스킹본을 NCP에 별도 저장.
- [ ] **텍스트 마스킹** — OCR 텍스트에서 PII 토큰 제거/대체. AI에 보내는 입력은 마스킹된 텍스트만.
- [ ] **AI 구조화** — gpt-4o-mini(text-only)에 마스킹된 텍스트 입력. 응답: 약물 후보 리스트 (이름·용량·복약주기 raw).
- [ ] 각 약물 후보에 대해 **`MedicineMatchService.match()` 호출**로 itemSeq 자동 매칭 시도.
- [ ] 결과를 `Prescription` + `PrescriptionMedicineCandidate` 엔티티로 저장. 상태 `PENDING_REVIEW`.
- [ ] **원본 이미지 즉시 삭제** (ADR 0009). NCP 명시 삭제 + Lifecycle Policy로 24h 만료 강제.
- [ ] 처리 완료 시 보호자에게 푸시 알림 (FCM, 후속 spec 의존).
- [ ] `GET /api/v1/prescriptions/{id}` — 추출 결과 + 마스킹본 presigned GET URL 반환.
- [ ] `GET /api/v1/prescriptions?status=PENDING_REVIEW` — 내가 확인할 처방전 리스트.

### 비기능 요구사항
- **응답 시간**: 등록 API는 비동기. 전체 처리(OCR+AI+매칭) 5초 이내 p95 목표.
- **장애 격리**: Clova OCR 또는 GPT 실패 시 처방전 상태 `PROCESSING_FAILED` + 에러 메시지. 사용자에게 재시도 안내.
- **보안**: 원본은 메모리·임시 객체에서만. 영구 저장 0. 마스킹본은 SSE-S3 암호화. 접근통제 시니어/보호자만.
- **관측성**: 처리 단계별(OCR/마스킹/AI/매칭) 시간·성공률 로깅. PII 누출 로그 금지.
- **비용**: 처방전 1건당 약 3.3원 (Clova 3원 + GPT-4o-mini 0.3원, VAT 별도).

## 4) 범위 / 비범위

### 포함
- 처방전 업로드·OCR·마스킹·AI 구조화·매칭·저장 전체 파이프라인
- Clova General OCR 어댑터
- gpt-4o-mini text 모델 어댑터
- PII 마스킹(텍스트·이미지) 로직
- `Prescription`·`PrescriptionMedicineCandidate` 엔티티 신설
- 마스킹본 NCP 저장 (presigned GET 별도 후속 이슈)

### 제외 (Out of Scope)
- **보호자 확인 UI/UX 흐름** → `prescription-confirmation` spec
- **`MedicineSearchService` / `MedicineMatchService` 구현** → `medicine-search` spec
- **MCP tool 노출** → `mcp-server-foundation` spec
- **푸시 알림(FCM) 인프라** → 후속 spec
- **원본 이미지 영구 보관** (ADR 0009로 명시 제외)
- **OCR 학습 데이터셋 구축** — 별도 동의 흐름 + 가명처리 spec 필요
- **GPT vision 사용** — Clova OCR 텍스트로 충분, vision 불필요로 결정

## 5) 설계

### 5-1) 도메인 모델

**신규 엔티티 — `Prescription`** (`com.ppiyaki.prescription`)
```
id                    bigint PK
owner_id              bigint     // 시니어 user_id
status                enum       // PROCESSING / PENDING_REVIEW / CONFIRMED / REJECTED / PROCESSING_FAILED
masked_image_object_key varchar  // NCP objectKey, presigned GET 발급용
ocr_raw_text          text nullable  // PII 마스킹된 OCR 원문 (감사용)
ai_model              varchar    // ex: "gpt-4o-mini-2024-07-18"
processed_at          datetime
failure_reason        text nullable
created_at / updated_at
```

**신규 엔티티 — `PrescriptionMedicineCandidate`**
```
id                    bigint PK
prescription_id       bigint FK
ocr_raw_text          varchar    // OCR 원문 (예: "이부프로멘정 200mg, 1일 3회")
extracted_name        varchar    // AI 파싱 약물명
extracted_dosage      varchar nullable
extracted_schedule    varchar nullable
matched_item_seq      varchar nullable  // MedicineMatchService 결과
matched_item_name     varchar nullable
match_type            enum       // EXACT / FUZZY_AUTO / MANUAL_REQUIRED / NO_MATCH
match_similarity      float nullable
match_reason          text nullable
caregiver_decision    enum       // PENDING / ACCEPTED / REJECTED / MANUALLY_CORRECTED
caregiver_chosen_item_seq varchar nullable  // 보호자가 다른 약물 선택 시
caregiver_note        text nullable
reviewed_at           datetime nullable
created_at
```

> 이 엔티티의 `caregiver_*` 필드 활용은 `prescription-confirmation` spec에서 다룬다.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/v1/prescriptions` | 처방전 등록 (objectKey 입력, 비동기 처리 트리거) | 필수 |
| GET | `/api/v1/prescriptions/{id}` | 추출 결과 + 마스킹본 presigned URL | 필수 (소유자/보호자) |
| GET | `/api/v1/prescriptions?status=...` | 내가 확인할/확인한 처방전 리스트 | 필수 |

`POST /api/v1/prescriptions/{id}/confirm`, `PATCH /api/v1/prescriptions/{id}/medicines/{medId}` 등 보호자 액션은 `prescription-confirmation` spec에서 정의.

### 5-3) 외부 연동

#### Clova General OCR
- 엔드포인트: NCP CLOVA OCR Invoke URL (도메인별 발급)
- 인증: `X-OCR-SECRET` 헤더, 환경변수 `CLOVA_OCR_SECRET` + `CLOVA_OCR_INVOKE_URL`
- 요청: 이미지 base64 또는 multipart, 타입 명시
- 응답: 토큰 단위 텍스트 + boundingBox 좌표
- 단가: **3원/건** (글자 추출 General, VAT 별도). 무료 100건/월
- 타임아웃: 연결 2s / 읽기 5s
- 실패 처리: `Prescription.status = PROCESSING_FAILED` + reason 기록

#### OpenAI gpt-4o-mini (text)
- 엔드포인트: `https://api.openai.com/v1/chat/completions`
- 인증: `OPENAI_API_KEY` 환경변수
- 입력: 시스템 prompt(약물 추출 지시) + 마스킹된 OCR 텍스트
- 출력: JSON Mode 활성화로 약물 후보 리스트 강제
- 단가 (잠정, 2025-05 기준 추정): 입력 $0.15/1M, 출력 $0.60/1M → 건당 약 0.3원
- 타임아웃: 10s (LLM은 5s보다 여유)
- 실패 처리: 동일

### 5-4) 데이터 흐름

```
[Client] 처방전 사진 촬영
   │
   │ 1. presigned PUT URL 발급 (POST /api/v1/uploads/presigned)
   │ 2. NCP Object Storage에 직접 업로드 (24h 만료 객체로)
   ▼
[Client] objectKey 획득
   │
   │ 3. POST /api/v1/prescriptions {objectKey}
   ▼
[PrescriptionController]
   │
   │ 4. 처방전 row 생성 (status=PROCESSING) + 201 리턴 (비동기)
   │
   ▼
[PrescriptionProcessingService] (비동기)
   │
   │ 5. NCP에서 원본 이미지 fetch
   │ 6. ClovaOcrClient.ocr(image) → text + bboxes
   │ 7. PiiMaskingService.identifyPii(text, bboxes) → [bbox]
   │ 8. ImageMaskingService.mask(image, [bbox]) → maskedImage
   │ 9. NCP에 마스킹본 저장 (영구)
   │ 10. PiiMaskingService.maskText(text, [bbox]) → maskedText
   │ 11. OpenAiClient.extractMedicines(maskedText) → [extractedItem]
   │
   │ FOR each extractedItem:
   │   12. medicineMatchService.match(item.name, item.dosage, item.form)
   │       → MatchResult (matchType, itemSeq, similarity, reason)
   │   13. PrescriptionMedicineCandidate row 생성
   │
   │ 14. Prescription.status = PENDING_REVIEW
   │ 15. 원본 이미지 NCP 명시 삭제 (이중 안전: Lifecycle 24h 만료도 활성)
   │ 16. 보호자 push 알림 발송 (NotificationService, 미구현 시 스킵)
   ▼
[보호자] GET /api/v1/prescriptions?status=PENDING_REVIEW → 확인 화면 진입
```

### 5-5) DB 마이그레이션
- 신규 테이블: `prescriptions` (위 엔티티 정의)
- 신규 테이블: `prescription_medicine_candidates`
- 인덱스:
  - `prescriptions(owner_id, status, created_at DESC)` — 사용자별 리스트 조회
  - `prescription_medicine_candidates(prescription_id)`

## 6) 작업 분할 (예상 PR 리스트)

선행 의존: `medicine-search` spec/PR 머지 (MatchService 필요)

- [ ] PR 1 `docs(prescription)`: 본 spec 커밋
- [ ] PR 2 `chore(infra)`: `CLOVA_OCR_SECRET` / `CLOVA_OCR_INVOKE_URL` / `OPENAI_API_KEY` 환경변수 추가. `ClovaOcrProperties`, `OpenAiProperties` (`@Validated` + `@ConditionalOnProperty`). `application*.yml`, `.env.example`, `backend-cd.yml`. NCP Lifecycle Policy 설정 가이드 README. `needs-human-review`.
- [ ] PR 3 `feat(prescription)`: `Prescription`/`PrescriptionMedicineCandidate` 엔티티 + Repository
- [ ] PR 4 `feat(infra)`: `ClovaOcrClient` 어댑터 + 단위 테스트 (mock)
- [ ] PR 5 `feat(infra)`: `OpenAiClient`(text completions) 어댑터 + 단위 테스트 (mock)
- [ ] PR 6 `feat(prescription)`: `PiiMaskingService` (텍스트·이미지 마스킹). 정규식 + 키워드 기반. 단위 테스트.
- [ ] PR 7 `feat(prescription)`: `PrescriptionProcessingService` 통합 + `PrescriptionController`. 비동기 처리는 `@Async` 또는 단순 동기 (MVP). E2E 테스트.
- [ ] PR 8 `chore(infra)`: NCP Object Storage Lifecycle Policy "원본 prefix 24h 만료" 설정 (코드 외 콘솔 작업, 운영 메모 필요)

## 7) 테스트 전략

### 단위
- `ClovaOcrClient` — 요청 형식, 응답 파싱, 실패 분기
- `OpenAiClient` — JSON Mode 검증, 응답 파싱, 토큰 한도
- `PiiMaskingService` — 정규식 정확도(주민번호/전화/면허번호), 키워드 매칭, bbox 계산
- `ImageMaskingService` — 박스 좌표 합성 정확도

### 통합 (E2E)
- `@SpringBootTest`로 mock OCR/OpenAI 빈 주입
- 정상 처방전 → 후보 N개 + status=PENDING_REVIEW
- OCR 실패 → status=PROCESSING_FAILED + 사유 기록
- 권한 없는 사용자 조회 → 403
- 마스킹본 presigned URL 발급 검증

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q-OCR-1 | 비동기 처리 방식 | (a) `@Async` + 단순 큐 / (b) 메시지 브로커(추후) | @goohong / PR 7 전 |
| Q-OCR-2 | OpenAI 모델 확정 | gpt-4o-mini 잠정. 2026-04 시점 신모델 단가·품질 재확인 후 결정 | @goohong / PR 5 전 |
| Q-OCR-3 | 마스킹본 보존 기간 | (a) 영구 / (b) 사용자 약물 삭제 시 같이 / (c) N년 후 자동 만료 | @goohong / PR 3 전 |
| Q-OCR-4 | 처방전 1건당 추출 약물 수 상한 | (a) 무제한 / (b) 20개 / (c) 50개 | @goohong / PR 5 전 |

## 9) 결정 로그
- 2026-04-16: 초안 작성
- 2026-04-16: 원본 이미지 영구 미보관 (ADR 0009)
- 2026-04-16: OCR = Clova General OCR, AI = gpt-4o-mini text only (vision 미사용). 근거: Clova가 한국어 인쇄 텍스트 정확도 우위, vision 추가는 비용·복잡도 증가 대비 이득 미미
- 2026-04-16: 비용 추정 약 3.3원/건 (Clova 3원 + GPT 0.3원). 단가는 NCP 공식·OpenAI 공식 최신 확인 후 spec에 박을 것
- 2026-04-16: PII 마스킹은 **이미지·텍스트 둘 다** 수행. 이미지 마스킹은 보호자 검증용, 텍스트 마스킹은 LLM 입력용
- 2026-04-16: 약물 매칭은 `medicine-search` spec의 `MedicineMatchService`에 위임 (책임 분리)
