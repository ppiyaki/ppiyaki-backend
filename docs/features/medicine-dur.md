---
feature: 약물 DUR 점검 API
slug: medicine-dur
owner: @goohong
scope: health
related_issues: []
related_prs: []
status: approved
last_reviewed: 2026-04-15
---

# 약물 DUR 점검 API

## 1) 개요 (What / Why)
- 시니어가 복용 중인 **여러 약물 조합**에 대해 식약처 공공 DUR API로 병용금기·특정연령대금기·임부금기·노인주의·효능군중복 등의 경고를 점검한다.
- 결과는 `dur_checks` 테이블에 **immutable 로그**로 저장해 감사·회고에 활용한다 (ADR 0007 기반, 본 기능에서 TTL 캐시 도입 쪽으로 부분 개정 예정).
- **사용자 시나리오 + AI/MCP 자동 호출**을 동시에 지원 — MCP 서버가 대화 중 약물 안전 확인을 위해 이 API를 프로그래매틱하게 호출할 수 있다.

## 2) 사용자 시나리오
- **보호자**는 시니어 약물 등록 직후 "DUR 점검" 버튼을 눌러 기존 복용 약물과의 상호작용 경고를 확인한다.
- **시니어**는 본인이 추가 약물을 등록할 때 자동 점검 결과를 복약 앱에서 확인한다.
- **AI 에이전트(MCP)**는 복약 관련 질의응답 중 특정 약물/조합의 안전성을 묻기 위해 `POST /api/v1/medicines/{id}/dur-check`를 호출하고, 구조화된 응답(`warningLevel`, `warnings[]`)을 근거로 답변을 구성한다.
- **보호자**는 과거 DUR 점검 이력을 조회해 약물 변경 전후 경고 추이를 확인한다.

## 3) 요구사항

### 기능 요구사항
- [ ] 약물 단건에 대해 DUR 점검을 트리거할 수 있다 (`POST /api/v1/medicines/{id}/dur-check`).
- [ ] 점검은 해당 약물 + **같은 시니어가 복용 중인 다른 활성 약물 전체**를 대상으로 상호작용·금기를 종합 판정한다.
- [ ] **"활성 약물"** 판별 기준: `medication_schedules`에 활성 일정(`start_date <= today <= end_date OR end_date IS NULL`)이 존재하는 약물만 (Q-DUR-2 결정).
- [ ] 응답은 기계 가독성 있게 구조화된 JSON이다: `warningLevel(NONE/INFO/WARN/BLOCK)`, `warningText`, `warnings[]` 배열, `fromCache` boolean (Q-DUR-1 결정).
- [ ] `warnings[]` 각 항목: `type`, `withMedicine`, `severity`, `description`(정제된 한글), `rawText`(원문 발췌, nullable) (Q-DUR-5 결정).
- [ ] 점검 이력 조회 (`GET /api/v1/medicines/{id}/dur-checks?limit=10`) — limit 기본 10, 최대 50 (Q-DUR-3 결정).
- [ ] 최신 점검 결과 조회 (`GET /api/v1/medicines/{id}/dur-check/latest`).
- [ ] `force_refresh` 쿼리 파라미터 (기본 false) — false면 이중 캐시(Layer 1 + Layer 2) 활용해 외부 호출 최소화, true면 **Layer 2만 우회** (combo_hash 무시, 재분석 강제). Layer 1(MFDS 응답 캐시)은 force_refresh에서도 유지 — 식약처 원본 데이터는 24h TTL 만료에 일임, 쿼터 보호.
- [ ] **Layer 1 캐시**(사용자 공유, 인메모리): `(operation, itemSeq)` 키로 외부 API 응답 정제본 24h 보관. `MfdsResponseCache` 인터페이스 기반 — Redis 스왑 염두.
- [ ] **Layer 2 캐시**(사용자별, `dur_checks`): `(medicine_id, combo_hash, checked_at)` 기준. `combo_hash`는 시니어의 활성 약물 itemSeq 정렬·해시값. 약물 추가/제거 시 자동 무효화.
- [ ] 권한: medicine 접근권과 동일 (약물 소유자 + 활성 `care_relations` 보호자).
- [ ] 외부 API 호출 타임아웃 5초 (Q-DUR-4 결정). 초과 또는 에러 시 HTTP 503 + `DUR_UNAVAILABLE` 에러 코드로 투명하게 실패 보고. 실패도 `dur_checks` 레코드로 저장(감사용).

### 비기능 요구사항
- **관측성**: 외부 호출 소요시간, 성공/실패/캐시히트 비율을 info/error 로그에 `correlationId`와 함께 기록.
- **쿼터 보호**: 공공 API 쿼터(10K/오퍼레이션/일) 초과 방지를 위해 Layer 1(사용자 공유) + Layer 2(사용자별) 이중 캐시. AI 호출 폭주 시에도 버틸 수 있도록 force_refresh 남용을 모니터링.
- **보안**: `rawResponse`는 감사용 원본이므로 민감 개인정보 포함 여부 점검 후 저장. 공공 DUR API는 개인정보를 요구하지 않으므로 현 시점은 단순 저장 허용.
- **응답 시간**: 캐시 히트 <50ms p95, 캐시 미스(외부 호출 포함) <3s p95 목표. 외부 API 응답이 더 느리면 타임아웃 설정.

## 4) 범위 / 비범위

### 포함
- `POST /api/v1/medicines/{id}/dur-check` (수동 + MCP 트리거 공용)
- `GET /api/v1/medicines/{id}/dur-check/latest`
- `GET /api/v1/medicines/{id}/dur-checks`
- 식약처 **DURPrdlstInfoService** 연동 어댑터 (병용금기/특정연령대금기/임부금기/노인주의/효능군중복 중 필수 오퍼레이션 선별 호출)
- 24h TTL 캐시 + `force_refresh` 플래그 로직
- ADR 0007 개정 (본 기능 범위 한정한 캐시 도입)
- `ErrorCode`에 `DUR_UNAVAILABLE`, `DUR_MEDICINE_NOT_FOUND` 등 추가

### 제외 (Out of Scope)
- **약물 등록 시 자동 DUR 호출** — 비즈니스 플로우 정리 이후 별도 이슈. 이번 스프린트는 명시적 호출만.
- **복약 일정 등록 시 자동 상호작용 점검** — 복잡도 큼, 이번 범위 밖.
- **MCP 서버 자체 구현** — MCP 친화적 응답만 제공. 실제 MCP tool 래핑은 별도 프로젝트.
- **캐시 invalidation 웹훅** — 외부 API 측 지원 불명. 24h TTL로 충분하다고 판단.
- **관리자용 DUR 이력 대시보드** — 후속 기능.
- **오프라인 DUR 파일 데이터(심평원) 임포트** — 외부 API 장애 시 fallback으로 쓸 수 있으나 이번 범위 밖. 필요 시 후속 ADR.

## 5) 설계

### 5-1) 도메인 모델
- **기존 재사용**:
  - `DurCheck`(엔티티), `DurWarningLevel`(enum) — `src/main/java/com/ppiyaki/health/`에 이미 존재
- **기존 `DurCheck` 엔티티의 의존 컬럼**: `id`, `medicine_id`, `checked_at`, `warning_level`, `warning_text`, `raw_response`(외부 API 원본, 감사용), `created_at`. 이 중 `raw_response`는 외부 호출 실패 시 에러 사유도 기록.
- **`DurCheck` 컬럼 추가**: `combo_hash`(varchar, indexed) — Layer 2 캐시 키 일부. 시니어의 활성 약물 itemSeq 정렬·해시값.
- **medicines 확장**: `item_seq`(varchar, indexed, nullable) 추가. 식약처 품목기준코드. **모든 DUR 호출의 입력 키**. 별도 PR로 선행.
- **Layer 1 캐시는 인메모리**(빈만 등록, DB 엔티티 아님). §5-3-A 참조.
- `medicines.dur_warning_text` 컬럼에 **최근 점검의 요약 텍스트를 복사** 저장 (기존 도메인 모델 §5 기재 사항).
- Repository/Service/Controller/외부 연동 어댑터·캐시 구현체는 `com.ppiyaki.health.*` 하위에 신설.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | `/api/v1/medicines/{medicineId}/dur-check` | DUR 점검 트리거 (수동/AI) | 필수 | `?force_refresh=false` | `DurCheckResponse` |
| GET | `/api/v1/medicines/{medicineId}/dur-check/latest` | 최신 점검 결과 | 필수 | - | `DurCheckResponse` |
| GET | `/api/v1/medicines/{medicineId}/dur-checks` | 점검 이력 (기본 10건, 최대 50) | 필수 | `?limit=10` | `DurCheckListResponse` |

**`DurCheckResponse`** (record):
```
Long id
Long medicineId
Instant checkedAt
String warningLevel              // NONE / INFO / WARN / BLOCK
String warningText               // 사람 가독용 요약
List<DurWarningItem> warnings    // 구조화된 경고 항목
boolean fromCache                // 캐시 히트 여부 (Q-DUR-1)
```

**`DurWarningItem`** (record):
```
String type           // INTERACTION / AGE / PREGNANCY / ELDERLY / DUPLICATE / ...
String withMedicine   // 상호작용 상대 약물명 (단독 경고면 null)
String severity       // INFO / WARN / BLOCK
String description    // 정제된 한글 요약 (UI 가독성 우선)
String rawText        // 공공 API 원문 발췌 (nullable, AI 인용 정확성용)
```

### 5-3) 외부 연동 — 식약처 DURPrdlstInfoService03
- **공공데이터포털**: [data.go.kr DURPrdlstInfoService](https://www.data.go.kr/dataset/15020627/openapi.do) (publicDataPk: 15059486)
- **Base host**: `apis.data.go.kr/1471000/DURPrdlstInfoService03` (https/http 모두 지원, https 사용)
- **OpenAPI 스펙**: `docs/features/medicine-dur/openapi-mfds.json` (이번 PR에 함께 커밋)
- 인증: `serviceKey` 쿼리 파라미터 (URL Encode 형태). 환경변수 `DUR_API_SERVICE_KEY`로 주입
- 응답 포맷: `type=json` 사용 (default xml)
- HTTP 클라이언트 타임아웃: **연결 2s, 읽기 5s** (Q-DUR-4 결정. 운영 데이터 보고 추후 조정)
- **쿼터**: 각 오퍼레이션 **10,000 호출/일** (오퍼레이션별 별도)

#### 사용 오퍼레이션 (확정)

MVP — Phase 1:
- `getUsjntTabooInfoList03` — 병용금기 (다제 핵심)
- `getOdsnAtentInfoList03` — 노인주의 (시니어 대상이라 매우 중요)
- `getEfcyDplctInfoList03` — 효능군중복 (다제 복용 시 중복)

Phase 2 (후속):
- `getCpctyAtentInfoList03` (용량주의), `getMdctnPdAtentInfoList03` (투여기간주의), `getSeobangjeongPartitnAtentInfoList03` (서방정분할주의)

본 spec 범위에서 제외 (시니어 무관):
- `getPwnmTabooInfoList03` (임부금기), `getSpcifyAgrdeTabooInfoList03` (특정연령대금기 — 대부분 소아·청소년 대상)

보조:
- `getDurPrdlstInfoList03` — DUR 품목 메타데이터. 본 spec에서는 직접 호출 안 함. **약물 검색·매칭 별도 spec(`medicine-search`)에서 활용 예정**

#### 응답 핵심 필드

병용금기(`getUsjntTabooInfoList03`):
- 입력 약물: `ITEM_SEQ`, `ITEM_NAME`, `INGR_KOR_NAME`
- **상대 약물(다제 매칭 키)**: `MIXTURE_ITEM_SEQ`, `MIXTURE_ITEM_NAME`, `MIXTURE_INGR_KOR_NAME`
- 사유: `PROHBT_CONTENT` (사용자 표시), `REMARK`

단일 약물 경고(노인주의/효능군중복 등):
- `ITEM_SEQ`, `ITEM_NAME`, `INGR_NAME`
- `PROHBT_CONTENT` (주의 내용), `REMARK`
- 효능군중복은 추가로 `EFFECT_NAME`, `SERS_NAME`(계열명) 제공

#### 응답 결과 코드 (header.resultCode)
- `00` — 정상 (totalCount=0이면 경고 없음)
- `01` — APPLICATION_ERROR
- `11` — NO_MANDATORY_REQUEST_PARAMETER_ERROR

#### 실패 처리
- 5xx / 네트워크 에러 / 타임아웃 → 503 + `DUR_UNAVAILABLE`. `dur_checks`에 실패 레코드 저장(`warningLevel=null`, `rawResponse="ERROR: ..."`)
- header.resultCode != "00" → 동일하게 실패 처리
- 쿼터 초과(401 또는 결과코드 별도) → 동일 처리 + 경보 로그
- 응답 파싱 실패 → 실패 레코드 + 500 fallback

### 5-3-A) 캐시 설계 (이중 레이어)

DUR 점검은 두 종류의 데이터를 다룸. 각각 다른 캐시 단위가 필요:

#### Layer 1 — 외부 API 응답 캐시 (사용자 공유)

**저장 방식**: **인메모리 우선** (`ConcurrentHashMap` 기반), Redis 스왑을 염두에 둔 인터페이스 추상화.

```java
public interface MfdsResponseCache {
    Optional<CachedMfdsResponse> get(String operation, String itemSeq);
    void put(String operation, String itemSeq, CachedMfdsResponse payload);
    void invalidate(String operation, String itemSeq);
}

// Phase 1 구현체
@Component
@ConditionalOnMissingBean(MfdsResponseCache.class)
class InMemoryMfdsResponseCache implements MfdsResponseCache { ... }

// Phase 2 (TODO): Redis 도입 시
// @Component @ConditionalOnProperty("ncp.redis.host")
// class RedisMfdsResponseCache implements MfdsResponseCache { ... }
```

**키**: `(operation, item_seq)`
**값** (`CachedMfdsResponse`):
```
String operation
String itemSeq
Instant fetchedAt
int totalCount
List<MfdsItem> items   // 응답 items 정제본 (DUR 산정에 필요한 필드만)
```

**TTL**: 24h (조회 시 `fetchedAt + 24h > now()` 검사)
**공유**: 모든 사용자가 동일 itemSeq 쿼리 시 캐시 재사용
**무효화**: 단순 TTL. 약물 정보 변경 빈도(주~월 단위)에 비해 24h는 충분히 보수적

> **TODO (Phase 2)**: 다음 조건 중 하나라도 충족 시 Redis로 마이그레이션
> - 백엔드 인스턴스 다중 배포 (현재 단일) → 캐시 분산 필요
> - 인메모리 엔트리 수가 1만+로 증가 → 힙 부담 가시화
> - 콜드 캐시 비용 (앱 재시작 시 쿼터 폭증)이 운영상 문제
> - 인터페이스 추상화돼 있으므로 구현체 교체만으로 가능 (호출부 무수정)

#### Layer 2 — 다제 점검 결과 (사용자별)
- **저장 위치**: `dur_checks` (기존 엔티티)
- **키**: `(medicine_id, combo_hash, checked_at)` — `combo_hash`는 시니어가 그 시점에 복용 중인 활성 약물 itemSeq의 정렬·해시값
- **추가 컬럼 필요**: `dur_checks.combo_hash` (varchar, indexed)
- **TTL**: 24h **AND** 동일 combo_hash. 약물 추가/제거로 combo가 바뀌면 자동으로 캐시 미스
- **immutable**: 매 호출(외부 호출 + force_refresh 포함)마다 새 row. 캐시 히트 시에는 row 생성하지 않고 기존 row 반환

#### 캐시 동작 요약
| 시나리오 | Layer 1 | Layer 2 | 외부 호출 |
|---|---|---|---|
| 사용자 A가 처음 점검 | miss | miss | ✅ 발생 |
| 사용자 A가 24h 내 같은 medicine·같은 combo로 재점검 | hit | hit | ❌ 안 함 |
| 사용자 A가 약물 추가 후 재점검 | hit (기존 약물들) + 신규 1개만 miss | miss(combo_hash 변경) | ✅ 신규 약물에 한해 1회 |
| 사용자 B가 사용자 A와 동일 itemSeq 약물 점검 | hit (Layer 1 공유) | miss (B의 첫 점검) | ❌ 안 함 |
| force_refresh=true | bypass | bypass | ✅ 항상 |

#### 무효화 정책
- Layer 1: TTL 24h 만료 또는 운영자가 수동 invalidate (DB row 삭제)
- Layer 2: combo_hash 변경 시 자동 미스. 명시적 invalidate 없음 (immutable 로그 유지)
- force_refresh=true는 **Layer 2만 우회**. Layer 1은 TTL 만료에 일임 (식약처 원본 데이터는 사용자 조합과 무관, 쿼터 보호)

### 5-4) 데이터 흐름

```
client/MCP
  │
  │ POST /medicines/{id}/dur-check?force_refresh=false
  ▼
DurCheckController
  │
  ▼
DurCheckService
  ├─ validateAccess(userId, medicineId)              ← medicine 권한 재사용
  ├─ activeMedicines = medicationScheduleService
  │       .findActiveMedicines(seniorOf(medicineId)) ← Q-DUR-2: 활성 일정 기준
  ├─ comboHash = sha256(sorted(itemSeqs(activeMedicines)))
  │
  ├─ IF !force_refresh:
  │     latest = durCheckRepository
  │       .findLatestSuccess(medicineId, comboHash, within=24h)
  │     IF latest exists → return latest (fromCache=true)
  │
  └─ ELSE / cache miss:
       FOR each operation in [병용금기, 노인주의, 효능군중복]:
         FOR each medicine M in activeMedicines:
           ┌─ Layer 1 lookup ──────────────────────────┐
           │ cached = mfdsResponseCache.get(op, M.itemSeq)
           │ IF cached and not expired (24h):
           │     responses[op][M] = cached
           │ ELSE:
           │     resp = mfdsDurClient.call(op, M.itemSeq)  ← 외부 (timeout 5s)
           │     mfdsResponseCache.put(op, M.itemSeq, resp)
           │     responses[op][M] = resp
           └────────────────────────────────────────────┘
       │
       ├─ analyzeWarnings(responses, activeMedicines)
       │     • 병용금기: MIXTURE_ITEM_SEQ ∈ {다른 활성 약물 itemSeq}? → match
       │     • 노인주의: totalCount > 0? → 단독 경고
       │     • 효능군중복: 같은 EFFECT_NAME 가진 약물 그룹화 → 매칭
       │
       ├─ warningLevel = max(individual severities)
       ├─ durCheckRepository.save(new DurCheck(medicineId, comboHash, warningLevel, ...))
       ├─ medicine.updateDurWarningText(요약)
       └─ return response (fromCache=false)
```

### 5-5) DB 마이그레이션
- `dur_checks.combo_hash` (varchar(64), nullable) 컬럼 추가
- `dur_checks` 인덱스 추가: `(medicine_id, combo_hash, checked_at DESC)` — Layer 2 캐시 조회용
- `medicines.item_seq` (varchar, indexed, nullable) 컬럼 추가 (선행 PR)
- Layer 1은 인메모리이므로 DB 마이그레이션 없음

## 6) 작업 분할 (예상 PR 리스트)

- [ ] PR 1 `docs(infra)`: **본 Feature Spec + `docs/features/medicine-dur/openapi-mfds.json`** 커밋. ADR 0007 개정(24h TTL + force_refresh 허용 + 이중 캐시 도입). 이번 PR에서 후속 ErrorCode 후보(`DUR_UNAVAILABLE`, `DUR_MEDICINE_NOT_FOUND`)도 명시.
- [ ] PR 2 `feat(medicine)` (선행): `medicines.item_seq` 컬럼 추가 + 엔티티/DTO 갱신. 약물 자동 매칭은 후속 spec(`medicine-search`)에서. 이번엔 사용자가 수동 입력 가능하도록 노출만.
- [ ] PR 3 `chore(infra)`: `DUR_API_SERVICE_KEY` 환경변수 추가 — `application*.yml`, `.env.example`, `backend-cd.yml`. `DurApiProperties`(`@Validated` + `@ConditionalOnProperty`) 신설. `needs-human-review`.
- [ ] PR 4 `feat(health)`: 핵심 구현
  - `MfdsDurClient` (외부 호출 어댑터, RestClient 기반)
  - `MfdsResponseCache` 인터페이스 + `InMemoryMfdsResponseCache` 구현 (TTL 24h ConcurrentHashMap)
  - `DurCheckRepository` + `combo_hash` 컬럼 마이그레이션
  - `DurCheckService` (권한·이중 캐시·다제 분석·저장)
  - `DurCheckController` (3개 엔드포인트)
  - DTO + `ErrorCode` 추가
  - 단위 테스트 (Service 캐시 분기, Client 파싱, 다제 매칭 알고리즘)
- [ ] PR 5 `test(health)`: 통합(E2E) — 성공/권한 실패(403)/외부 장애(503)/Layer 1 히트(사용자 공유)/Layer 2 히트(combo_hash 동일)/약물 추가 시 미스. `MfdsDurClient`·`MfdsResponseCache` mock.
- [ ] PR 6 `feat(medicine)`: `medicines.dur_warning_text` 최신 요약 복사 연동 + 테스트.

> **별도 spec (작성 완료, 의존성)**:
> - `medicine-search.md` — `MedicineSearchService` + `MedicineMatchService`. 본 spec의 itemSeq 매칭은 이쪽에 위임
> - `mcp-server-foundation.md` — Spring AI MCP starter. `check_dur` tool 노출은 본 spec 머지 후 등록
> - `prescription-ocr.md` — 처방전 등록 및 보호자 확인 통합 흐름. medicine 엔티티에 itemSeq를 채워주는 주된 경로

## 7) 테스트 전략
- **단위**:
  - `DurCheckService` — Layer 1·2 캐시 분기, 권한 검증, DTO 변환, combo_hash 산출
  - `MfdsDurClient` — 요청 URL/파라미터 구성, 응답 파싱, 실패 분기, header.resultCode 처리
  - `InMemoryMfdsResponseCache` — TTL 만료, 동시성, invalidate
  - 다제 분석 로직 — 병용금기 매칭(MIXTURE_ITEM_SEQ ↔ activeMedicines), 효능군중복 그룹화
  - `DurWarningLevel` 산정 — 여러 경고 중 최고 심각도 채택
- **Controller 슬라이스**:
  - 인증 없음 → 401
  - 권한 없음(타인 약물) → 403
  - `force_refresh=true` 동작 (캐시 우회 검증)
- **통합(E2E)**:
  - 정상: 첫 점검 → 외부 호출 발생 → 두 번째 점검(같은 사용자, 24h 내) → 캐시 히트
  - **사용자 공유 캐시**: 사용자 A 점검 후 사용자 B가 동일 itemSeq 약물 등록·점검 → Layer 1 히트, 외부 호출 없음
  - 약물 추가: combo_hash 변경 → Layer 2 미스, 신규 itemSeq에 한해 외부 호출 1회
  - 외부 API 장애 → 503 + `DUR_UNAVAILABLE` + `dur_checks` 실패 레코드
  - 이력 조회 `limit` 동작
- **mock 전략**: `MfdsDurClient`(RestClient 기반)와 `MfdsResponseCache` 둘 다 `@TestConfiguration`으로 mock 빈 주입 가능. upload 테스트 패턴 재사용.

## 8) 오픈 질문
> 해소된 질문은 §9 결정 로그로 이동.

(현재 없음)

## 9) 결정 로그
- 2026-04-15: 초안 작성 (status=draft)
- 2026-04-15: Q1 결정 — 식약처 **DURPrdlstInfoService** 채택. 심평원 파일 데이터는 후속 fallback 후보.
- 2026-04-15: Q3 결정 — **다제 상호작용** (시니어가 복용 중인 전체 약물 조합 점검)
- 2026-04-15: Q5 결정 — 외부 장애 시 **503 + `DUR_UNAVAILABLE`** (투명성·안전성 우선; 마지막 성공 결과 fallback은 잘못된 의료 판단 유발 우려로 기각)
- 2026-04-15: Q6 결정 — medicine 접근권과 동일 (소유자 + 연동 보호자)
- 2026-04-15: Q7 결정 — **24h TTL 캐시 + `force_refresh` 플래그**. ADR 0007 부분 개정 예정.
- 2026-04-15: MCP 통합 방향 — **옵션 A (앱 내장) + HTTP SSE + JWT 재사용**. 단, MCP 서버 자체 도입은 별도 spec(`mcp-server-foundation`)에서 다루며 본 spec 범위 밖.
- 2026-04-15: Q-DUR-1 결정 — `DurCheckResponse.fromCache` **항상 응답에 노출**. 이유: AI/MCP 클라이언트가 신선도를 인지해 후속 동작(force_refresh 결정, 답변 표현) 결정 가능. 보안 부담 없음.
- 2026-04-15: Q-DUR-2 결정 — **`medication_schedules` 활성 일정 있는 약물만 점검 대상**. 단순 등록만 된 약물은 제외 → 노이즈 경고 방지. 활성 판별 헬퍼는 PR 3에서 함께 구현.
- 2026-04-15: Q-DUR-3 결정 — `limit` **기본 10, 최대 50**. 페이로드 보수적, 페이지네이션은 후속.
- 2026-04-15: Q-DUR-4 결정 — 외부 API HTTP 클라이언트 **연결 2s / 읽기 5s 타임아웃**. AI/MCP tool call 타임아웃(보통 30s)보다 충분히 짧고, 사용자 대기 허용 범위 내. 운영 후 조정.
- 2026-04-15: Q-DUR-5 결정 — `warnings[].description`(정제된 한글 요약) + `rawText`(원문 발췌, nullable) **둘 다 제공**. UI는 description 사용, AI는 정확한 인용에 rawText 활용. `dur_checks.raw_response`에 전체 원본은 별도 저장.
- 2026-04-15: OpenAPI 스펙 수집 완료 — `docs/features/medicine-dur/openapi-mfds.json`에 Swagger 2.0 전문 보관. 9개 오퍼레이션·응답 스키마·에러 코드 모두 확정.
- 2026-04-15: **MVP 오퍼레이션 3개 확정** — 병용금기(`getUsjntTabooInfoList03`) + 노인주의(`getOdsnAtentInfoList03`) + 효능군중복(`getEfcyDplctInfoList03`). Phase 2 후보: 용량주의·투여기간주의·서방정분할주의. 시니어 무관 제외: 임부금기·특정연령대금기.
- 2026-04-15: **medicine 엔티티 확장** — `item_seq`(식약처 품목기준코드) 컬럼 추가 선행 PR. 약물명 → itemSeq 자동 매칭은 별도 spec `medicine-search`로 분리.
- 2026-04-16: **선행 spec/PR 순서 확정** — `medicine-search` 머지 → `medicines.item_seq` NOT NULL 마이그레이션 → 본 spec(DUR) 구현. 처방전 OCR 흐름이 itemSeq를 채워주므로 "itemSeq null 약물" 케이스 처리 로직 불필요. 관련 오픈 질문(원래 Q-DUR-itemSeqNull)은 자동 해소.
- 2026-04-16: **MCP tool로 DUR 노출은 별도 spec(`mcp-server-foundation`)** PR 5에서 진행. 본 spec은 REST API에 집중.
- 2026-04-15: **이중 캐시 구조 도입** — Layer 1(사용자 공유, `(operation, itemSeq)` 키, 외부 API 응답 정제본) + Layer 2(사용자별, `dur_checks` + `combo_hash`, 다제 점검 결과). 단일 캐시로는 쿼터 초과·무효화 부정확 문제 해결 불가.
- 2026-04-15: **Layer 1 저장 방식 — 인메모리 우선, Redis TODO**. 이유: Redis 인프라 부담 회피, 단일 인스턴스 운영 중, 인터페이스 추상화(`MfdsResponseCache`)로 후속 Redis 스왑 용이. 마이그레이션 트리거: 다중 인스턴스 배포 / 엔트리 1만+ / 콜드 캐시 운영 비용 가시화. **TODO는 spec §5-3-A에 명시 유지**.
- 2026-04-15: **Layer 2 무효화 방식 — `combo_hash` 포함 키**. 시니어가 약물을 추가/제거할 때 자동으로 캐시 미스. 단순 TTL만으로는 부정확하다는 판단.
- 2026-04-16: **OpenAI 모델 맥락 공유** — prescription-ocr spec의 AI 모델이 gpt-4o-mini에서 gpt-5.4-nano로 변경됨 (OpenAI 라인업 변경, 단가: 입력 $0.20/1M, 출력 $1.25/1M). 본 spec(DUR)은 OpenAI를 직접 호출하지 않으나, prescription-ocr 통합 파이프라인과의 연계 구현 시 참고.
- 2026-04-16: **구현 순서 확정** — ① item_seq 컬럼 추가 → ② medicine-search (+ medicine-dur 병렬 가능) → ③ medicine-dur (현재 spec) → ④ prescription-ocr 통합본 → ⑤ mcp-server-foundation. medicine-search와 병렬 진행 가능.
