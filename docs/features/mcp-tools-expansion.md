---
feature: MCP 도구 확장 — AI 챗봇 약물·복약 정보 조회
slug: mcp-tools-expansion
owner: @goohong
scope: infra
related_issues: []
related_prs: []
status: draft
last_reviewed: 2026-04-17
---

# MCP 도구 확장 — AI 챗봇 약물·복약 정보 조회

## 1) 개요 (What / Why)
- AI 챗봇(ChatClient)이 사용자 질문에 답하기 위해 호출할 수 있는 @Tool 메서드를 확장한다.
- 현재: 약물 검색(searchMedicine), 약물 매칭(matchMedicineFromOcr), DUR 점검(checkDur) 3개.
- 추가: 약 상세 정보(e약은요), 복약 일정 현황, 남은 약 수량 조회.
- 시니어가 "오늘 아침약 먹었나?", "타이레놀 몇 알 남았어?", "이 약 부작용 뭐야?" 같은 질문에 AI가 답할 수 있게 한다.

## 2) 사용자 시나리오

### 시나리오 A: 복약 현황 확인
- 시니어: "오늘 약 먹었어?"
- AI가 `getTodaySchedules` 호출 → 오늘 복약 일정 + 복용 여부 반환
- AI: "오늘 아침 8시 가스모틴정, 저녁 7시 타이레놀정이 예정되어 있어요. 아침약은 아직 안 드셨어요."

### 시나리오 B: 남은 약 확인
- 보호자: "어머니 타이레놀 얼마나 남았어?"
- AI가 `getMedicineRemaining` 호출 → 남은 수량 + 예상 소진일
- AI: "타이레놀정 12알 남았어요. 현재 복약 일정 기준 4일치입니다."

### 시나리오 C: 약 정보 질문
- 시니어: "가스모틴정 부작용이 뭐야?"
- AI가 `getDrugInfo` 호출 (e약은요 API) → 효능/부작용/주의사항
- AI: "가스모틴정은 소화기관용약으로, 부작용으로 설사, 복통 등이 나타날 수 있어요."

### 시나리오 D: 약 모양 확인
- 시니어: "내가 먹는 하얀 알약이 뭐야?"
- AI가 `getDrugInfo` 호출 → itemImage URL 반환
- AI: "가스모틴정은 이렇게 생긴 흰색 장방형 정제예요. [이미지]"

## 3) 기능 요구사항

### 3-1) 내부 DB 도구 (즉시 구현)

- [ ] `getTodaySchedules(userId)` — 오늘 복약 일정 목록 반환
  - medicines 테이블 join medication_schedules
  - 오늘 요일이 daysOfWeek에 포함되고, startDate <= today <= endDate(nullable)
  - 응답: [{medicineName, scheduledTime, dosage, taken(boolean)}]
  - taken 판단: 현재 MVP에서 복용 기록 테이블이 없으므로 항상 false (TODO)

- [ ] `getMedicineRemaining(userId, medicineName?)` — 남은 약 수량 조회
  - medicines 테이블에서 ownerId = userId인 약물의 remainingAmount
  - medicineName이 있으면 해당 약만, 없으면 전체 목록
  - 응답: [{medicineName, remainingAmount, totalAmount}]

### 3-2) e약은요 API 도구 (API 승인 후 구현)

- [ ] `getDrugInfo(itemName)` — 약 상세 정보 조회
  - e약은요 API (`DrbEasyDrugInfoService/getDrbEasyDrugList`)
  - 검색 파라미터: itemName (contains 검색)
  - 응답: {itemName, entpName, 효능, 사용법, 주의사항, 상호작용, 부작용, 보관법, itemImage}
  - 캐시: InMemoryCache 24h (MfdsResponseCache 패턴 재사용)

## 4) 현재 MCP 도구 현황

| 도구 | 패키지 | 설명 |
|------|--------|------|
| `searchMedicine` | MedicineMcpTools | 식약처 DUR 약물 검색 |
| `matchMedicineFromOcr` | MedicineMcpTools | 약물명→itemSeq 매칭 |
| `checkDur` | DurMcpTools | DUR 안전성 점검 |

## 5) 추가할 MCP 도구

| 도구 | 클래스 | 데이터 소스 | 단계 |
|------|--------|----------|------|
| `getTodaySchedules` | MedicationMcpTools | 내부 DB | Phase 1 |
| `getMedicineRemaining` | MedicineMcpTools | 내부 DB | Phase 1 |
| `getDrugInfo` | DrugInfoMcpTools | e약은요 API | Phase 2 |

## 6) 기술 설계

### 6-1) getTodaySchedules

```java
@Tool(description = "Get today's medication schedule for the user. Returns medicine names, scheduled times, and dosages.")
public List<ScheduleSummary> getTodaySchedules()
```

- SecurityContextHolder에서 userId 추출
- MedicineRepository.findByOwnerId(userId) → 활성 약물 목록
- MedicationScheduleRepository.findByMedicineId(each) → 오늘 요일 + 기간 필터
- ScheduleSummary record: {medicineName, scheduledTime, dosage}

### 6-2) getMedicineRemaining

```java
@Tool(description = "Get remaining amount of medicines for the user.")
public List<MedicineRemainingInfo> getMedicineRemaining(
    @ToolParam(description = "Optional medicine name filter") String medicineName)
```

- SecurityContextHolder에서 userId 추출
- MedicineRepository.findByOwnerId(userId) → medicineName 필터 적용
- MedicineRemainingInfo record: {medicineName, remainingAmount, totalAmount}

### 6-3) getDrugInfo (Phase 2)

```java
@Tool(description = "Get detailed drug information including efficacy, side effects, usage, precautions, and pill image.")
public DrugInfoResponse getDrugInfo(
    @ToolParam(description = "Drug name to search") String itemName)
```

- e약은요 API 호출: `getDrbEasyDrugList?itemName=...`
- 24h 캐시
- DrugInfoResponse record: {itemName, entpName, efficacy, usage, precautions, interactions, sideEffects, storageMethod, imageUrl}

## 7) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q-MCP-1 | 복용 기록(taken) 테이블 도입 시점 | Phase 1에서 스키마만 / Phase 2에서 전체 | @goohong |
| Q-MCP-2 | e약은요 API 승인 상태 | 승인 대기 중 | @goohong |
| Q-MCP-3 | getDrugInfo 캐시를 MfdsResponseCache와 통합할지 별도 캐시할지 | (a) 통합 / (b) 별도 | @goohong |

## 8) 결정 로그
- 2026-04-17: 초안 작성. DUR API와 e약은요 API의 역할 분리 확인 (DUR=안전성 점검, e약은요=환자 안내).
- 2026-04-17: Phase 1(내부 DB 도구) / Phase 2(e약은요 연동) 분리.
