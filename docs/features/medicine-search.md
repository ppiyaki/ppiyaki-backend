---
feature: 약물 검색 및 매칭 (식약처 DUR 품목 기반)
slug: medicine-search
owner: @goohong
scope: medicine
related_issues: []
related_prs: []
status: draft
last_reviewed: 2026-04-16
---

# 약물 검색 및 매칭

## 1) 개요 (What / Why)
- 사용자(또는 AI)가 입력한 약물명·OCR 텍스트를 식약처 DUR 품목 정보(`getDurPrdlstInfoList03`)로 검색한다.
- 검색 결과 중 가장 적합한 것을 자동 매칭하거나, 사용자에게 후보 리스트를 제시한다.
- 다음 두 책임을 분리한다:
  - **`MedicineSearchService`** — 후보 검색 (단순)
  - **`MedicineMatchService`** — OCR 컨텍스트 기반 자동 매칭 (정책)
- 처방전 OCR 자동 등록, 사용자 수동 검색, MCP tool 등 여러 use case에서 활용.

## 2) 사용자 시나리오
- **AI 에이전트(MCP)** — 처방전 OCR 텍스트 "이부프로멘정 200mg" → `match_medicine_from_ocr` tool → 자동 매칭 시도 → 결과를 `prescription-ocr` 파이프라인에 반영.
- **사용자 수동 등록** — 검색창에 약물명 입력 → `search_medicine` API → 후보 리스트에서 선택 → Medicine 등록.
- **보호자 검토** — `prescription-ocr` (보호자 확인) 화면에서 NO_MATCH 항목을 직접 검색해 매칭.

## 3) 요구사항

### 기능 요구사항
- [ ] `GET /api/v1/medicines/search?q=...&form=...&limit=10` — 검색 API. 부분 일치, 정규화 매칭. 응답: `MedicineCandidate[]` (itemSeq, name, entpName, mainIngr, classCode, formName, etcOtcCode 등). limit 기본 10, 최대 50.
- [ ] `MedicineSearchService.search(query, options)` 인터페이스. 식약처 `getDurPrdlstInfoList03` 호출 + 결과 매핑.
- [ ] `MedicineMatchService.match(name, ingredientName?)` 인터페이스. 검색 결과 개수 기반 `MatchResult` 반환.
- [ ] **MatchResult.matchType** enum: `EXACT` / `CANDIDATES` / `NO_MATCH`.
- [ ] **MatchResult.reason** — 사람 가독 메모 (예: "정확 일치", "성분명 '클로피도그렐'(으)로 동일 성분 약물 5개 발견").
- [ ] name 검색 0건 시 ingredientName(처방전 원문 괄호 성분명)으로 재검색.
- [ ] 검색 결과 응답에 식약처 메타데이터 포함 (entpName, classCode 등) — 보호자 화면에서 약물 식별에 도움.
- [ ] 검색·매칭 외부 호출은 `MfdsResponseCache`(인메모리 24h, `medicine-dur` spec과 공유) 거쳐 쿼터 보호.

### 비기능 요구사항
- 검색 응답 < 500ms p95 (캐시 히트 시 < 50ms)
- 매칭 알고리즘은 결정적 (동일 입력 → 동일 출력)
- 임계치 변경은 재배포 없이 가능 (config refresh)
- 매칭 결과 분포(matchType별 비율) 메트릭

## 4) 범위 / 비범위

### 포함
- `MedicineSearchService` 인터페이스 + 식약처 어댑터 구현
- `MedicineMatchService` 인터페이스 + 기본 구현 (편집거리·정규화·컨텍스트 검증)
- 검색 결과 캐시 (`MfdsResponseCache` 재사용)
- 사용자 검색 REST API (`GET /api/v1/medicines/search`)
- MCP tool 노출은 `mcp-server-foundation`에서 wrapping

### 제외 (Out of Scope)
- **MCP 서버 인프라** → `mcp-server-foundation`
- **DUR 점검** → `medicine-dur`
- **OCR 처리** → `prescription-ocr`
- **보호자 확인 UX** → `prescription-ocr`
- **음성학적 매칭(한글 자모 기반)** — Phase 2
- **사용자 학습 기반 개인화 매칭** — 후속

## 5) 설계

### 5-1) 도메인 모델
- 신규 엔티티 없음 (식약처 호출 결과는 `MfdsResponseCache`에 캐시되며 별도 영속화 안 함, 단 medicine 등록 시점에 itemSeq·name이 `medicines` 테이블에 저장됨)
- `medicines.item_seq` 컬럼은 `medicine-dur` spec PR 2 (선행)에서 추가됨

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/v1/medicines/search?q=...` | 약물 검색. 후보 리스트 반환 | 필수 |

응답 예:
```json
{
  "responses": [
    {
      "itemSeq": "201405281",
      "itemName": "타이레놀정 500mg",
      "entpName": "한국얀센",
      "mainIngr": "아세트아미노펜 500mg",
      "formName": "필름코팅정",
      "etcOtcName": "전문의약품",
      "className": "해열·진통·소염제",
      "ingrCode": "D000893"
    }
  ]
}
```

### 5-3) 인터페이스

```java
public interface MedicineSearchService {
    List<MedicineCandidate> search(String query, SearchOptions options);
}

public interface MedicineMatchService {
    MatchResult match(String ocrText,
                      Optional<String> dosageHint,
                      Optional<String> formHint);
}

public record SearchOptions(
        Optional<String> formFilter,
        Optional<String> entpName,
        int limit
) {}

public record MatchResult(
        MatchType matchType,
        Optional<MedicineCandidate> recommended,
        List<MedicineCandidate> candidates,
        String reason
) {}

public enum MatchType {
    EXACT,       // 정규화 후 정확 일치 — 기본 체크
    CANDIDATES,  // 후보 있으나 정확 일치 없음 — 보호자 선택
    NO_MATCH     // 매칭 실패 — 수동 검색
}
```

### 5-4) 매칭 알고리즘

```
input: name (GPT 추출 약물명), ingredientName? (처방전 원문 괄호 성분명)

1. 검색 쿼리 정제
   searchQuery = extractSearchQuery(name)  // 괄호·용량 제거

2. 식약처 API 검색
   candidates = search(searchQuery, 20)

3. 결과 있으면 분류
   if candidates not empty:
       for each c in candidates:
           if normalizeForComparison(c.itemName) == normalizeForComparison(name):
               return MatchResult(EXACT, c, [], "정확 일치")
       return MatchResult(CANDIDATES, empty, candidates[0..5], "후보 약물 N개")

4. 성분명 재검색 (원문에 성분명이 있는 경우만)
   if ingredientName present:
       ingredientCandidates = search(normalize(ingredientName), 20)
       if not empty:
           return MatchResult(CANDIDATES, empty, ingredientCandidates[0..5],
               "성분명 '{ingredientName}'(으)로 동일 성분 약물 N개 발견")

5. 매칭 실패
   return MatchResult(NO_MATCH, empty, [], "유사한 약물을 찾지 못함")
```

#### 정규화 규칙
- 공백·괄호·하이픈 제거
- 용량 표기 통일 (예: "500밀리그람" → "500mg")
- `normalizeForComparison`: 추가로 괄호 안 내용(성분명) 제거 후 비교
- 한글 자모 단순화 (선택, Phase 2)

#### 사유 메모 자동 생성
```
generateReason(ocrText, matchedCandidate):
  diff = compareStrings(ocrText, matchedCandidate.itemName)
  if diff.length == 1:
      "OCR '{ocrText}'와 1글자 차이. 식약처 등록 약물 중 가장 유사한 항목으로 자동 매칭."
  if diff.length <= 3:
      "OCR '{ocrText}'와 {N}글자 차이. 식약처 등록 약물 중 가장 유사한 항목으로 자동 매칭."
  if dosage matches:
      append " 용량({dosage}) 일치."
```

### 5-5) 외부 연동

`getDurPrdlstInfoList03` 호출 (식약처 — `medicine-dur` spec §5-3과 동일 어댑터 재사용).

캐시 키: `(operation, query)` 또는 `(operation, itemSeq)`. 사용자 검색은 query 기반 → 캐시 효율 떨어질 수 있음. 정규화된 query를 키로 쓰는 게 좋음.

### 5-6) DB 마이그레이션
- 없음 (검색 결과는 캐시에만 저장, 영속화는 medicine 등록 시점에)

## 6) 작업 분할 (예상 PR 리스트)

- [ ] PR 1 `docs(medicine)`: 본 spec 커밋
- [ ] PR 2 `feat(medicine)`: `MedicineSearchService` 인터페이스 + 식약처 어댑터 구현. 캐시 통합.
- [ ] PR 3 `feat(medicine)`: `MedicineMatchService` 인터페이스 + 기본 구현 (정규화·편집거리·임계치)
- [ ] PR 4 `feat(medicine)`: `GET /api/v1/medicines/search` REST API + Controller + 권한 검증
- [ ] PR 5 `test(medicine)`: 단위 (정규화/매칭) + E2E (검색)
- [ ] PR 6 `chore(infra)`: 매칭 임계치 config 노출 (`medicine.matching.fuzzy-auto-threshold` 등)

## 7) 테스트 전략

### 단위
- 정규화 — 공백·괄호·제형 표기 케이스 100건+ 데이터셋
- 편집거리 — 정확/근사/실패 케이스
- 매칭 정책 — matchType 분류 정확도, 임계치 경계 검증
- 사유 메모 생성

### 통합 (E2E)
- 검색 API 정상 응답
- 캐시 히트/미스
- 외부 API 장애 시 503 + `MFDS_UNAVAILABLE`
- 검색 결과 0건 처리

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q-SEARCH-2 | 음성학적 매칭 도입 시점 | (a) Phase 1 포함 / (b) Phase 2 | @goohong / PR 3 전 |
| Q-SEARCH-3 | 사용자 수동 검색 결과에 cancelDate(취소된 약물) 표시 여부 | (a) 표시 / (b) 제외 / (c) 별도 필터 | @goohong / PR 4 전 |

## 9) 결정 로그
- 2026-04-16: 초안 작성
- 2026-04-16: **`MedicineSearchService`와 `MedicineMatchService` 분리** — search는 다양한 use case에서 재사용, match는 정책 결정 책임 분리
- 2026-04-16: **fuzzy 매칭 임계치 0.90 + 용량/제형 일치 조건** — 한국 약물명 1글자 차이가 완전히 다른 성분일 위험을 감안한 보수적 설정
- 2026-04-16: **EXACT 후보 자동 매칭, FUZZY_AUTO는 사유 메모 동반** — 보호자가 신경 쓸 항목을 자연스럽게 분리
- 2026-04-16: 검색 결과 캐시는 `medicine-dur`의 `MfdsResponseCache` 재사용 (인메모리 24h, Redis 마이그레이션 TODO)
- 2026-04-16: **Q-SEARCH-1 결정** — 검색 limit 기본 10, 최대 50. 페이로드 보수적, `medicine-dur` 이력 조회와 동일 기준. `SearchOptions.limit` 기본값 10, 상한 50으로 구현.
- 2026-04-16: **구현 순서 확정** — ① item_seq 컬럼 추가 → ② medicine-search (+ medicine-dur 병렬 가능) → ③ medicine-dur → ④ prescription-ocr 통합본 → ⑤ mcp-server-foundation.
