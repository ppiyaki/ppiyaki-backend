---
feature: 복약 사진 약 개수 AI 검증 (Phase 2)
slug: medication-log-phase2
status: draft
owner: @goohong
scope: medication
related_issues: [184]
related_prs: []
last_reviewed: 2026-04-30
---

# 복약 사진 약 개수 AI 검증 (Phase 2)

## 1) 개요 (What / Why)
- 시니어가 복약 인증으로 업로드한 사진에서 **알약 개수**를 Vision LLM으로 추출해, 같은 시각에 예정된 모든 schedule의 dosage 합과 비교한다.
- 일치 여부를 `MedicationLog.ai_status`에 기록하여 보호자/시니어가 GET 응답으로 확인 가능.
- Phase 3 (#185, 알약 종류 식별)와 분리: Phase 2는 **개수 세기만** 수행 (식별 X).
- **Primary Why**: 복약 정확도 1차 검증. "약을 손에 들고 사진은 찍었지만 정작 안 먹음" 같은 위장 케이스를 줄이는 안전장치.

## 2) 사용자 시나리오
- 시니어가 아침 8시에 약 2종(아스피린 1정 + 비타민 1정)을 손바닥에 올리고 촬영 → PUT.
- 서버가 GPT-4o Vision으로 사진 속 알약 개수 추출 (예: 2개) → 같은 시각 schedule들의 dosage 합 계산 (예: 1+1=2) → 일치 → `ai_status=COUNT_MATCH`.
- 만약 사진에 1개만 보이면 `COUNT_MISMATCH` → 보호자가 GET으로 발견 → 시니어에게 재촬영/재복약 요청 (UX는 후속).
- dosage 파싱 실패 또는 Vision 실패 시 `COUNT_UNKNOWN` / `COUNT_FAILED`.

## 3) 요구사항

### 기능 요구사항
- [ ] `LogAiStatus` enum 신설 — `COUNT_MATCH` / `COUNT_MISMATCH` / `COUNT_UNKNOWN` / `COUNT_FAILED`. `MedicationLog.ai_status`에 매핑.
- [ ] `OpenAiClient.countPills(imageBytes, mediaType): Optional<Integer>` 신규 메서드 — GPT-4o Vision JSON Mode로 정수 반환. 실패 시 `Optional.empty()`.
- [ ] `DosageParser.parsePillCount(dosage): Optional<Integer>` 유틸 — 정규식 `(\d+)` 첫 매치만 추출. "1정" → 1, "2알" → 2, "반알" → empty.
- [ ] `MedicationLogService.upsert`에 검증 트리거: `photoObjectKey != null && status == TAKEN`일 때만.
- [ ] 트리거 시 동일 `(seniorId, targetDate, scheduledTime)` 의 모든 active schedule 조회 → dosage 합산.
- [ ] dosage 파싱 실패가 1개라도 있으면 → `COUNT_UNKNOWN`.
- [ ] Vision API 실패 시 → `COUNT_FAILED`. retry 1회 (timeout 30s).
- [ ] 결과를 `ai_status` 컬럼에 저장. 응답 DTO에 노출 (이미 Phase 1 응답에 `aiStatus` 필드 있음).

### 비기능 요구사항
- **응답 시간**: 동기 처리. **실측 기반(2026-04-30 prod 서버에서 OpenAI Vision API 직접 호출): gpt-5.4-nano 평균 ~3s (1x1 더미 이미지), 실제 폰 카메라 사진은 ~4-6s 예상.** p50 ~3s / p95 ~6s 목표. 클라이언트 timeout 30s 권장.
- **비용**: gpt-5.4-nano 호출당 약 0.4~1원 수준 추정 (이미지 토큰 포함). 처방전 OCR 비용 대비 차수 동일. 일일 트래픽 모니터링 권장.
- **보안**: 사진은 메모리에서만 처리 (이미 NCP S3에 저장된 것을 fetch). Vision API에 사용자 ID/PII 전송 안 함.
- **타임아웃**: 서버측 30초 (Vision LLM 처리 시간 여유). 클라이언트 측 타임아웃 30초 이상 권장.
- **민감정보 로깅 금지**: dosage·objectKey·결과 카운트는 로그에 남기지 않음 (id/userId만).

## 4) 범위 / 비범위

### 포함
- `LogAiStatus` enum 신설
- Vision LLM 어댑터 확장 (`OpenAiClient.countPills`)
- dosage 파싱 유틸
- 동일 시각 schedule 합산 로직
- 동기 검증 흐름
- 단위 + E2E 테스트 (Vision 응답은 mock)

### 제외 (Out of Scope)
- **알약 종류 식별 (Phase 3, #185)** — 별도 spec
- **비동기 처리 / 재시도 큐** — Phase 2.1로 분리. 동기 MVP가 운영 데이터로 검증된 후 도입.
- **FCM 푸시 알림 (mismatch 시)** — 별도 spec.
- **시각 정렬 허용 오차** — 동일 `scheduledTime`만 합산. 7:55 vs 8:00 같은 인접 시각 합산은 후속.
- **dosage 0.5 처리** — 정수 추출만. "반알"은 UNKNOWN로 fallback.
- **사진 다중 촬영(여러 사진을 한 번에)** — Phase 1 단일 사진 모델 그대로 유지.

## 5) 설계

### 5-1) 도메인 모델
기존 `MedicationLog` 엔티티 그대로 사용. `ai_status` 컬럼 활용.

| 컬럼 | 타입 | 이번 이슈에서 |
|---|---|---|
| ai_status | varchar | **`LogAiStatus` enum 매핑** (Phase 1에서는 항상 null) |

신규 enum:
```java
public enum LogAiStatus {
    COUNT_MATCH,      // Vision 추출 개수 == dosage 합
    COUNT_MISMATCH,   // 추출 개수 != 합
    COUNT_UNKNOWN,    // dosage 파싱 실패 (예: "반알") → 검증 불가
    COUNT_FAILED      // Vision API 호출 실패 (timeout, 5xx 등)
}
```

### 5-2) API 엔드포인트 변경
**없음**. 기존 `PUT /api/v1/medication-logs` / `GET /api/v1/medication-logs` 응답에 `aiStatus`가 enum 값으로 채워질 뿐.

### 5-3) 외부 연동 — OpenAI GPT-4o Vision

- 엔드포인트: `https://api.openai.com/v1/chat/completions` (이미 사용 중)
- 모델: **`gpt-5.4-nano`** — 프로젝트 표준 (prescription-ocr와 동일, `OpenAiProperties.model`로 주입). vision 입력 지원.
- 입력: `messages` 배열에 image_url(base64 data URL) + system prompt
- 출력: JSON Mode로 `{"count": <integer>}` 강제. 파싱 실패 시 retry 1회.
- System prompt 초안:
  ```
  Count the visible pills in the photo. Return JSON {"count": N} where N is a non-negative integer.
  If unsure or no pills visible, return {"count": 0}.
  ```

### 5-4) 데이터 흐름

```text
[PUT /api/v1/medication-logs]
  ├─ status != TAKEN || photoObjectKey == null → AI 검증 스킵, ai_status=null
  └─ photoObjectKey 있음 + status == TAKEN
       ↓
       1. NCP S3에서 사진 fetch (byte[])
       2. 동일 (seniorId, targetDate, scheduledTime) 의 모든 active schedule 조회
       3. 각 schedule.dosage를 DosageParser.parsePillCount() 적용 → 정수 합산
          ├─ 하나라도 파싱 실패 → ai_status = COUNT_UNKNOWN, Vision 호출 스킵
          └─ 모두 성공 → expectedCount 산출 → 다음 단계
       4. OpenAiClient.countPills(imageBytes) → Optional<Integer>
          ├─ empty (Vision 실패) → ai_status = COUNT_FAILED
          └─ present → 비교
              ├─ actualCount == expectedCount → COUNT_MATCH
              └─ != → COUNT_MISMATCH
       5. medication_log.ai_status 갱신 후 저장
       6. 응답 200 + aiStatus 포함 (PUT 응답 시간: 평소 + 5~10s)
```

### 5-5) DB 마이그레이션
- 없음. 기존 `medication_logs.ai_status` 컬럼 (varchar) 활용.
- enum 강제는 Java 측에서만 (`@Enumerated(EnumType.STRING)`). DB는 varchar 유지로 향후 enum 값 추가 유연성 확보 (ADR 0006).

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: 본 spec 커밋 — `type:docs`, `scope:medication`
- [ ] PR 2: 구현 — `type:feat`, `scope:medication`
  - `LogAiStatus` enum 신설
  - `OpenAiClient.countPills(imageBytes, mediaType): Optional<Integer>` 추가
  - `DosageParser` 유틸 신설
  - `MedicationLogService.upsert`에 검증 트리거 통합
  - `MedicationLog.ai_status` 매핑 LogAiStatus enum으로 전환
  - 단위 테스트 (DosageParser, OpenAiClient mock, Service 분기 5케이스)
  - E2E 테스트 (Vision API mock 처리)

## 7) 테스트 전략

### 단위
- **`DosageParser`**:
  - "1정" → Optional.of(1)
  - "2알" → Optional.of(2)
  - "1.5캡슐" → Optional.of(1) (정수 첫 매치만)
  - "반알" → Optional.empty()
  - null / "" → Optional.empty()
- **`OpenAiClient.countPills`**:
  - 정상 응답 `{"count": 3}` → Optional.of(3)
  - JSON 파싱 실패 → retry 후 Optional.empty()
  - 5xx 응답 → Optional.empty()
- **`MedicationLogService` 분기 5케이스**:
  - status=MISSED → ai_status=null (검증 스킵)
  - photoObjectKey=null → ai_status=null
  - 단일 schedule, dosage="1정", Vision=1 → COUNT_MATCH
  - 두 schedule (1정, 1정), Vision=1 → COUNT_MISMATCH
  - dosage="반알" 포함 → COUNT_UNKNOWN, Vision 호출 안 됨 (verify mock no interaction)
  - Vision Optional.empty() → COUNT_FAILED

### 통합 (E2E)
- `@SpringBootTest`로 mock OpenAiClient 빈 주입
- 정상 흐름: 시니어 PUT (사진 + status=TAKEN, 동일 시각 schedule 2개, Vision mock=2) → ai_status=COUNT_MATCH 응답 검증
- mismatch 케이스: Vision mock=1 (실제 2 예정) → ai_status=COUNT_MISMATCH
- Vision 실패: mock throws → ai_status=COUNT_FAILED

## 8) 오픈 질문
| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q1 | 동일 시각 정렬 허용 오차 | 동일 `scheduledTime` 정확 일치만 합산. 인접 시각 합산은 후속 | ✅ 결정 |
| Q2 | gpt-5.4-nano vision 정확도 | 운영 데이터로 측정 후 모델 변경 여부 결정 | 운영 모니터링 |

## 9) 결정 로그
- 2026-04-30: 초안 작성. Phase 1 머지 직후 진행.
- 2026-04-30: **Vision LLM = OpenAI gpt-5.4-nano** — 프로젝트 표준 모델 사용 (prescription-ocr와 동일). 기존 `OpenAiClient` + `OpenAiProperties.model` 인프라 그대로 확장. Clova Vision 미채택.
- 2026-04-30: **동기 처리** — UX 단순성 우선. 실측 기반 p50 ~3s / p95 ~6s 응답 시간 감수. 시니어가 응답 받기 전 화면이 안 넘어가는 자연 차단 효과까지 부수적으로 확보. 비동기는 운영 트래픽 데이터 보고 후속 결정.
- 2026-04-30: **트리거 = `photoObjectKey != null && status == TAKEN`** — 미복용/사진 없음은 검증 무의미.
- 2026-04-30: **동일 시각 schedule 합산** — 시니어가 같은 시각에 여러 약 한 번에 촬영하는 실제 행동 패턴 정합.
- 2026-04-30: **dosage 파싱 = 정규식 `(\d+)` 첫 매치** — 단순 / 실패 시 `COUNT_UNKNOWN`로 fallback. "반알" 등은 검증 불가로 처리.
- 2026-04-30: **결과 알림은 Phase 2 범위 외** — `ai_status` 갱신만 하고, FCM 푸시는 별도 spec 의존.
