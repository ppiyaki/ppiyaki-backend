---
feature: 알약 외형 기반 식별 (식약처 낱알식별 정보 자체 인덱스)
slug: pill-identification
status: draft
owner: @goohong
scope: medicine
related_issues: []
related_prs: []
last_reviewed: 2026-05-07
---

# 알약 외형 기반 식별 (식약처 낱알식별 정보 자체 인덱스)

## 1) 개요 (What / Why)
채팅 사진 첨부(`POST /chat/photo-messages`) 흐름에서 vision LLM이 **약명 추정에 실패하면 전체 식별 흐름이 무너지는 갭**을 메운다. 약명 추정에 의존하지 않고, vision은 외형 묘사(각인·모양·색·분할선)만 추출하고, 자체 인덱스 DB에서 외형 기반으로 후보 약을 찾는다.

**왜 자체 인덱스인가?**
- 식약처 OpenAPI(`/getMdcinGrnIdntfcInfoList03`)는 약명/업체명/일련번호로만 검색 가능. **각인·색·모양은 응답 필드이지만 검색 키가 아님**.
- 약학정보원·약사회 등 알약 식별 사이트들도 같은 방식 — 식약처 데이터를 일괄 수집해 자체 인덱싱.
- vision 약명 추정 실패 케이스(이번 prod 검증의 두 번째 사진 "흰색 긴 알약")가 prod에 빈번할 것으로 예상.

**대상 사용자**: 시니어(약 봉투 잃었거나 사진만 있는 상황), 보호자(시니어가 보낸 약 사진 식별).

## 2) 사용자 시나리오
- **각인 명확**: 시니어가 알약 정면(각인 보임) 사진 + "이거 뭐야?" → vision이 `print_front="T"`, `colorClass1="하양"`, `drugShape="장방형"` 추출 → 자체 DB 검색 → 단일 후보 → "타이레놀정 500밀리그램으로 식별됩니다. 식후 30분 복용을 권장합니다." (`getDrugInfo` 보강)
- **각인 안 보임**: 알약 옆면/뒷면만 보여 vision이 각인 추출 실패 → 색·모양·분할선만으로 검색 → 후보 다수 → "각인이 잘 보이게 다시 찍어주세요" follow-up
- **다중 후보**: 흔한 외형(흰색 원형 분할선 없음)이라 후보 5건 이상 → LLM이 후보 약명 목록을 사용자에게 제시 + 추가 정보 요청

## 3) 요구사항
### 기능 요구사항
- [ ] `pill_identifications` 테이블 신설 — 식약처 낱알식별 데이터 마스터 적재
  - 컬럼: `item_seq` PK, `item_name`, `entp_name`, `print_front`, `print_back`, `drug_shape`, `color_class1`, `color_class2`, `line_front`, `line_back`, `leng_long`, `leng_short`, `thick`, `chart`, `item_image`, `class_no`, `class_name`, `etc_otc_name`, `mark_code_front`, `mark_code_back`, `edi_code`, `bizrno`, `change_date`(식약처 변경일), `synced_at`(우리 동기화 시각)
  - 인덱스: `print_front`, `(drug_shape, color_class1)`, `(color_class1, drug_shape, line_front)` 조합 (운영 데이터 보고 조정)
- [ ] `MdcinGrnIdntfcInfoClient` 신설 — 식약처 OpenAPI `/getMdcinGrnIdntfcInfoList03` 호출
  - base URL: `https://apis.data.go.kr/1471000/MdcinGrnIdntfcInfoService03`
  - 인증: `MFDS_API_SERVICE_KEY` 동일 키 사용 (1471000 service group). spec §5-3 검증
  - paging: `numOfRows=100`, `pageNo` 순회로 전체 수집
  - retry: 5xx/timeout 시 1회 재시도 (기존 `MfdsApiClient` 패턴 참조)
- [ ] `PillIdentificationSyncService.syncAll()` — 전체 페이지 paginate → idempotent upsert (item_seq 기준 INSERT…ON DUPLICATE KEY UPDATE)
- [ ] 동기화 트리거 두 가지:
  - 정기 cron `@Scheduled(cron = "0 0 2 * * SUN")` 주 1회 (KST 새벽 2시)
  - 운영 수동 `POST /api/v1/admin/pill-identifications/sync` (관리자 권한 — 별도 권한 인프라 도입 시점까지는 미공개)
- [ ] `PillIdentificationRepository.searchByAppearance(printFront?, drugShape?, colorClass1?, lineFront?, ...)` — 동적 쿼리 (Querydsl 또는 JPA Specification)
  - null 파라미터는 검색 조건 제외
  - 결과 limit (예: 10건)
- [ ] `PillIdentificationMcpTools.identifyPillByAppearance(...)` — 새 MCP 도구
  - 입력: 각인(printFront/printBack)·색(colorClass1) (모두 optional). 모양(drugShape)·분할선(lineFront)은 issue #251로 제외 — vision 추출 정확도 한계(§9 결정 로그).
  - 출력: `PillIdentifyResult { totalMatches, candidates[] }`. candidates는 최대 10건 (itemSeq, itemName, entpName, drugShape, colorClass1, printFront, printBack, lineFront, etcOtcName, itemImage). 응답엔 모양/분할선 포함되어 LLM 자연어 응답에 활용 가능.
  - 0건 / 1건 / 2건 이상 / truncated(`totalMatches > candidates.size()`) 분기는 LLM이 자연어로 처리
- [ ] `ChatSessionService.PHOTO_SYSTEM_PROMPT` 수정 — 약 사진이면 약명 추정 X, 외형 묘사 추출 후 즉시 `identifyPillByAppearance` 호출
- [ ] 색·모양 enum 정규화 매핑
  - 첫 동기화 후 식약처 enum 분포 확인 → vision 출력값과 매칭하는 정규화 매핑(예: vision "흰색" → DB "하양")
  - 매핑은 코드 상수 또는 별도 매핑 테이블 (단순화 우선 — 코드 상수)
- [ ] 동기화 결과 로깅: `pill_identifications.upserted=N, deleted=0, elapsed=Xs` (식약처 데이터 삭제 정책 미상이라 본 spec은 삭제 안 함)
- [ ] 도메인 문서 §5에 `pill_identifications` 테이블 추가
- [ ] 채팅 사진 spec(`docs/features/chat-photo-messages.md`) 시스템 프롬프트 변경 노트 추가

### 비기능 요구사항
- **성능**:
  - 동기화 batch: 식약처 데이터 약 2~3만 건 추정. `numOfRows=100` × ~250 페이지 × 평균 400ms = ~100s. 야간 cron이라 운영 부하 낮음.
  - 런타임 검색: `WHERE print_front=? AND drug_shape=?` 인덱스 lookup 수십 ms 이내.
- **API 호출 한도** (공공데이터포털 정책):
  - 개발계정 = **일 10,000 호출 / `service-key + 활용신청 API` 조합**.
  - 동기화 1회 = ~250 호출 (numOfRows=100 가정). 주 1회 cron → **일 평균 36 호출, 한도 여유 큼**.
  - `MdcinGrnIdntfcInfoService03`는 기존 `MfdsApiClient`(DUR 등) 활용신청과 **별도 신청 필요** → 한도 분리. 같은 키로 두 API에 활용신청 등록.
  - 한도 초과 fallback: 다음 cron까지 stale L1 데이터로 식별 계속. 사고 알림(slack 또는 로그 모니터링) 후속.
  - prod 트래픽 증가 시 운영계정 승격 신청(활용사례 등록) — 본 spec 외.
- **신뢰성**:
  - batch 부분 실패 허용 — item_seq 단위 idempotent upsert. 다음 cron 또는 수동 트리거로 복구.
  - 페이지 단위 부분 재시도 가능(이미 upsert된 페이지는 다음 회차에 동일 결과).
  - 식약처 API 일시 장애 시: 마지막 성공 동기화 데이터로 식별 계속 가능 (24h~1주 stale 허용).
- **보안**:
  - 식별 도구는 인증된 채팅 흐름에서만 호출. 직접 외부 노출 없음.
  - 관리자 동기화 endpoint는 권한 인프라 도입까지 비공개 (개발자 수동 호출만).
- **관측성**:
  - 동기화 시작/완료/실패 로그 (operation, elapsed, count).
  - 식별 도구 hit/miss 분포 후속 메트릭 검토.

## 4) 범위 / 비범위 (중요)

### 포함
- `pill_identifications` 테이블 + 동기화 client/service
- 정기 cron + 수동 트리거(개발자용)
- `identifyPillByAppearance` MCP 도구
- vision 시스템 프롬프트 갱신(외형 묘사 추출 + 도구 호출)
- 색·모양 정규화 매핑 코드 상수
- 단위/통합 테스트 (mock 식약처 client)
- 도메인 문서 §5 갱신

### 제외 (Out of Scope)
- **Phase B — vision 재판정**: 후보 N건 reference 이미지(`ITEM_IMAGE`)를 vision LLM에 재전달해 시각 비교 결정. 별도 spec(`pill-identification-phase2.md`)로 분리. Phase A의 prod 식별률 보고 도입 결정.
- **Embedding/vector DB 기반 검색** (CLIP·ViT): 옵션 B'. 2TB 식약처 AI 데이터셋 입수(오프라인) + ML 인프라 도입 부담 큼. Phase B에서도 부족할 때 검토.
- **AI 알약 이미지 데이터셋(15112582) 활용**: 라벨링 매핑은 있으나 입수가 오프라인 + boostcamp 사례 Top-1 43%로 정확도 한계 → 본 spec 외.
- **알고리즘 소스 코드(15112583) 활용**: 자체 모델 학습은 GPU/MLOps 부담. 본 spec 외.
- **외부 사이트(약학정보원/약사회) backend scraping**: ToS 회색지대. 비채택.
- **이미지 검색 일관성을 위한 추가 캐시**: L1(자체 DB)이 이미 fast lookup. 추가 캐시는 stale 위험만 증가.
- **`DrugInfoClient` 캐시를 `MfdsResponseCache` 인터페이스로 통합**: 별도 follow-up.
- **관리자 권한 인프라**: 동기화 수동 트리거를 외부 노출하려면 admin role 필요. 본 spec은 비공개 endpoint로만.
- **신규 약물 즉시 반영**: 동기화 주기(주 1회) 사이 갭은 수용. 사용자가 신약 사진 보내면 fallback("식별 어렵습니다, 약사 문의").
- **알약 외 의약품(시럽·연고·주사제 등) 식별**: pill_identifications 테이블은 알약 한정.

## 5) 설계

### 5-1) 도메인 모델 — `pill_identifications`

`docs/ai-harness/06-domain-model.md §5`에 추가.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| item_seq | varchar(20) PK | 식약처 품목일련번호 |
| item_name | varchar(255) NOT NULL | 품목명 (예: "타이레놀정500밀리그람") |
| entp_name | varchar(255) | 업체명 |
| print_front | varchar(64) | 앞면 각인 (예: "T") |
| print_back | varchar(64) | 뒷면 각인 |
| drug_shape | varchar(32) | 모양 (예: "원형", "장방형", "타원형") |
| color_class1 | varchar(32) | 1차 색 (예: "하양") |
| color_class2 | varchar(32) | 2차 색 (대부분 null) |
| line_front | varchar(32) | 앞면 분할선 (예: "(+)형", "(-)형", null) |
| line_back | varchar(32) | 뒷면 분할선 |
| leng_long | varchar(16) | 장축 길이 (mm) |
| leng_short | varchar(16) | 단축 길이 |
| thick | varchar(16) | 두께 |
| chart | text | 형태 설명 |
| item_image | varchar(512) | 식약처 호스팅 알약 이미지 URL |
| class_no | varchar(16) | 분류 번호 |
| class_name | varchar(128) | 분류명 |
| etc_otc_name | varchar(32) | 전문/일반 구분 |
| mark_code_front | varchar(64) | 표준 각인 코드 |
| mark_code_back | varchar(64) | 표준 각인 코드 |
| edi_code | varchar(32) | 보험코드 |
| bizrno | varchar(32) | 사업자등록번호 |
| change_date | varchar(20) | 식약처 변경일자 (yyyymmdd) |
| synced_at | datetime(6) NOT NULL | 우리 동기화 시각 |

**인덱스**:
- `idx_pill_print_front` (print_front)
- `idx_pill_shape_color` (drug_shape, color_class1)
- `idx_pill_color_shape_line` (color_class1, drug_shape, line_front)
- 단일 컬럼 `idx_pill_item_name` (LIKE 검색용)

추후 운영 데이터 hit 분포 보고 인덱스 조정.

### 5-2) API / 도구 엔드포인트

| Type | 위치 | 설명 |
|---|---|---|
| MCP Tool | `PillIdentificationMcpTools.identifyPillByAppearance` | 외형 기반 식별. ToolContext 패턴(PR #232) — 인증 컨텍스트 전달 |
| 운영 endpoint | `POST /api/v1/admin/pill-identifications/sync` | 수동 동기화 트리거. 본 spec 한정 비공개 (개발자 직접 호출만, public route X) |

**도구 시그니처 (의사 코드)** — issue #251 이후 색깔/각인만:
```java
@Tool(description = "Identify a pill by its physical appearance — imprint and color. Use this when the user sends a photo and you can extract the pill's visual features but cannot determine the drug name from the image alone.")
public PillIdentifyResult identifyPillByAppearance(
    @ToolParam(description = "Front imprint text/symbol (예: 'T', 'AT500'). null if not visible.") String printFront,
    @ToolParam(description = "Back imprint. null if not visible.") String printBack,
    @ToolParam(description = "Primary color (예: '하양', '노랑', '빨강'). null if uncertain.") String colorClass1
)
```
- 응답 record: `PillIdentifyResult(totalMatches, candidates[])`. candidates 항목 = `PillCandidate(itemSeq, itemName, entpName, drugShape, colorClass1, printFront, printBack, lineFront, etcOtcName, itemImage)` 최대 10건. drugShape/lineFront는 응답엔 포함되어 LLM 자연어 응답에 활용 가능 (입력에서만 제외).
- 0건이면 빈 리스트 — LLM이 follow-up 질의 자율 결정
- truncation 신호: `totalMatches > candidates.size()`이면 시니어 친화 follow-up
- (history) 초안에는 `drugShape`/`lineFront`도 입력 파라미터였으나, 운영 진단(issue #251) 결과 vision 추출 정확도 한계로 제외. 자세한 사유는 §9 결정 로그.

#### 외부 식약처 API 호출 (동기화 batch)
- URL: `https://apis.data.go.kr/1471000/MdcinGrnIdntfcInfoService03/getMdcinGrnIdntfcInfoList03`
- 파라미터: `serviceKey={MFDS_API_SERVICE_KEY}`, `numOfRows=100`, `pageNo={i}`, `type=json`
- 응답 파싱: `body.items.item[]` (단일 객체일 가능성도 처리 — `MfdsApiClient` 기존 패턴 참조)

### 5-3) 외부 연동
- **식약처 OpenAPI** (낱알식별 정보 서비스 v3)
- **인증키 검증 필요**: 동일 `MFDS_API_SERVICE_KEY`로 1471000 service group 모든 API에 통하는지 확인. 별도 키 발급 필요면 spec §9 결정 로그에 추가.
- 동기화 시 retry 1회 (기존 `MfdsApiClient` 패턴), connect 5s / read 10s timeout.

### 5-4) 데이터 흐름

#### 동기화 batch
```
@Scheduled cron 또는 POST /admin/sync
  ↓
PillIdentificationSyncService.syncAll()
  ├─ pageNo=1
  └─ loop:
       ├─ MdcinGrnIdntfcInfoClient.fetchPage(pageNo, numOfRows=100)
       ├─ items.forEach: pill_identifications upsert (item_seq PK)
       └─ pageNo++ until totalCount 도달
  ↓
log "synced: {N} items, elapsed: {sec}"
```

#### 런타임 식별 (chat photo flow에서)
```
사용자 사진 + 텍스트
  ↓ ChatSessionService.sendPhotoMessageStream
  ↓ Vision LLM (시스템 프롬프트 갱신 — 약명 추정 X, 외형 묘사 추출 우선)
  ↓ LLM이 도구 호출 결정: identifyPillByAppearance(printFront, drugShape, colorClass1, ...)
  ↓ PillIdentificationMcpTools — Repository.searchByAppearance
  ↓ pill_identifications SQL: WHERE 매칭 인덱스 lookup
  ↓ 후보 0~10건 반환
  ↓ LLM이 후보로 응답 결정:
      0건  → "외형으로 일치 약 없음. 각인 다시 보여달라" 또는 "식별 어렵다" 안내
      1건  → 단일 정답 + 후속 도구 chain (getDrugInfo, checkDur)으로 정보 보강
      ≥2건 → 후보 약명 목록 제시 + 사용자에게 추가 정보 (각인·분할선) 요청
```

### 5-5) DB 마이그레이션

```sql
-- prod
CREATE TABLE pill_identifications (
    item_seq VARCHAR(20) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    entp_name VARCHAR(255),
    print_front VARCHAR(64),
    print_back VARCHAR(64),
    drug_shape VARCHAR(32),
    color_class1 VARCHAR(32),
    color_class2 VARCHAR(32),
    line_front VARCHAR(32),
    line_back VARCHAR(32),
    leng_long VARCHAR(16),
    leng_short VARCHAR(16),
    thick VARCHAR(16),
    chart TEXT,
    item_image VARCHAR(512),
    class_no VARCHAR(16),
    class_name VARCHAR(128),
    etc_otc_name VARCHAR(32),
    mark_code_front VARCHAR(64),
    mark_code_back VARCHAR(64),
    edi_code VARCHAR(32),
    bizrno VARCHAR(32),
    change_date VARCHAR(20),
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (item_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_pill_print_front ON pill_identifications (print_front);
CREATE INDEX idx_pill_shape_color ON pill_identifications (drug_shape, color_class1);
CREATE INDEX idx_pill_color_shape_line ON pill_identifications (color_class1, drug_shape, line_front);
CREATE INDEX idx_pill_item_name ON pill_identifications (item_name);
```

`schema.sql` 동기화 (Hibernate ddl-auto=update가 dev에서 자동 생성. prod는 위 SQL 수동 적용).

### 5-6) 캐시 계층 정리

| Layer | 위치 | TTL | 책임 |
|---|---|---|---|
| L1 (신규) | `pill_identifications` 테이블 | 영구 (주 1회 batch 갱신) | 식별 결정 (외형 → 후보 약) |
| L2 (기존) | `MfdsApiClient`/`InMemoryMfdsResponseCache` | 24h | 식별 후 보강 (`searchMedicine`/DUR) |
| L2 (기존) | `DrugInfoClient` (e약은요) | 24h | 효능·부작용 보강 |
| L3 | Vision LLM 호출 | 캐시 X | 사진 묘사 추출 + 응답 |

**조화**: L1과 L2(`MfdsApiClient`)는 같은 식약처 데이터 출처지만 책임 분리 — L1은 외형 인덱싱, L2는 실시간 약명 검색·DUR. 신규 약물은 L1 동기화 주기(주 1회) 갭이 있으나 의약품 등록 빈도 낮아 수용.

### 5-7) 코드 영향 범위

| 파일 | 변경 |
|---|---|
| `medicine/PillIdentification.java` | **신규** 엔티티 |
| `medicine/repository/PillIdentificationRepository.java` | **신규** + searchByAppearance 동적 쿼리 |
| `common/mfds/MdcinGrnIdntfcInfoClient.java` | **신규** 식약처 OpenAPI 클라이언트 |
| `medicine/service/PillIdentificationSyncService.java` | **신규** batch 동기화 |
| `medicine/scheduler/PillIdentificationSyncScheduler.java` | **신규** `@Scheduled` cron |
| `medicine/controller/AdminPillSyncController.java` | **신규** 수동 트리거 (비공개) |
| `common/mcp/PillIdentificationMcpTools.java` | **신규** identifyPillByAppearance |
| `chat/ChatToolCallbackConfig.java` | PillIdentificationMcpTools 등록 |
| `chat/service/ChatSessionService.java` | `PHOTO_SYSTEM_PROMPT` 갱신 (외형 묘사 추출 + 도구 호출 유도) |
| `resources/schema.sql` | 테이블 + 인덱스 |
| `docs/ai-harness/06-domain-model.md` | §5 pill_identifications 등재 |
| `docs/features/chat-photo-messages.md` | 시스템 프롬프트 변경 노트 |
| 테스트 — sync mock·repository·MCP tool·시스템 프롬프트 동작 | 다수 신규 |

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1 `docs(medicine)`: 본 spec
- [ ] PR 2 `feat(medicine)`: 엔티티 + 마이그레이션 + 식약처 클라이언트 + Sync 서비스 + cron + 수동 endpoint + 도메인 문서
- [ ] PR 3 `feat(chat)`: PillIdentificationMcpTools + ToolCallbackConfig 등록 + 시스템 프롬프트 갱신 + 단위/E2E 테스트

PR 2/3 분리 이유:
- PR 2는 prod 동작 변경 없이 데이터 적재만 (chat photo 흐름 그대로)
- PR 3는 도구 등록 + 시스템 프롬프트 변경으로 prod 동작 영향
- PR 2 머지·배포·동기화 1회 실행 후 PR 3 진행 → 데이터 없이 도구 호출되어 빈 결과 반환되는 사고 방지

## 7) 테스트 전략

### 단위
- `MdcinGrnIdntfcInfoClient`: paging 응답 파싱, 단일 item / item 배열 둘 다 처리
- `PillIdentificationSyncService`: 두 페이지 mock → upsert 카운트 검증 (idempotent: 동일 데이터 재실행 시 update만)
- `PillIdentificationRepository.searchByAppearance`: null 파라미터 제외, 인덱스 사용 검증
- `PillIdentificationMcpTools.identifyPillByAppearance`: ToolContext userId 추출 + repository mock + 0/1/N 케이스
- 색·모양 정규화 매핑 (vision "흰색" → DB "하양")

### 통합/E2E
- `@SpringBootTest` mock `MdcinGrnIdntfcInfoClient` → `PillIdentificationSyncService.syncAll()` 호출 → DB 상태 검증
- `ChatPhotoE2ETest` 보강: 외형 추출 mock → identifyPillByAppearance 호출 검증 (mock ChatClient stream에 도구 호출 stub)

## 8) 오픈 질문
| # | 질문 | 선택지 | 잠정 결정 |
|---|---|---|---|
| Q1 | `MFDS_API_SERVICE_KEY`로 `MdcinGrnIdntfcInfoService03` 호출 가능? | (a) 동일 키 사용 / (b) 별도 키 발급 | (a) 가정. PR 2 PoC에서 검증 후 (b) 필요 시 spec 갱신 |
| Q2 | 색 enum 정규화 매핑 방식 | (a) 코드 상수 (Recommended, MVP) / (b) 매핑 테이블 / (c) LLM에 식약처 enum 값 직접 알려주고 그대로 출력하게 | (a) — 첫 동기화 후 enum 분포 확인하고 코드 상수로 매핑 |
| Q3 | 동기화 cron 주기 | (a) 주 1회(SUN 02:00 KST) / (b) 일 1회 / (c) 월 1회 | (a) — 의약품 등록 빈도 낮음. 운영 보고 조정 |
| Q4 | 수동 동기화 endpoint 보호 방식 | (a) 비공개(개발자만) / (b) admin role 도입 / (c) IP allowlist | (a) — admin role 인프라 도입까지 |
| Q5 | 검색 결과 limit | (a) 10건 / (b) 5건 / (c) 무제한 | (a) — LLM 토큰 부담 + UX |
| Q6 | 식약처 데이터 삭제 처리 | (a) DB 보존 (정책 미상) / (b) hard delete / (c) soft delete `deleted_at` | (a) — 본 spec 외, 정책 확인 후 follow-up |
| Q7 | Vision 약명 추정도 병행할지 | (a) 추정 X, 외형 묘사만 (Recommended) / (b) 둘 다 시도 | (a) — 추정 의존 제거가 본 spec 동기 |
| Q8 | `numOfRows` 최대값 | (a) 100 가정 / (b) 1000 가능 시 호출 25배 절감 | PR 2 PoC에서 식약처 응답 헤더·문서 확인 후 채택 |
| Q9 | 운영계정 승격 신청 시점 | (a) 본 spec 외 (Recommended) / (b) 동시 진행 | (a) — prod 트래픽·식별 호출수 보고 결정. 개발계정 10,000/일로 본 사이클 충분 |

## 9) 결정 로그
- 2026-05-07: 초안 작성. v0.9.3 chat photo 검증에서 vision 약명 추정 실패 케이스(흰색 긴 알약) 발견 → 외형 기반 식별 도입.
- 2026-05-07: **자체 인덱스 채택**. 식약처 OpenAPI는 색·모양 검색 키 없음 + 약학정보원/약사회도 같은 방식. backend scraping은 ToS 회색지대 → 비채택.
- 2026-05-07: **AI 알약 이미지 데이터셋(15112582) 비채택**. 라벨링 매핑 존재하지만 입수가 오프라인(2TB) + boostcamp 사례 Top-1 43%로 단독 정확도 한계. 정확도 부족 시 Phase B에서 검토.
- 2026-05-07: **embedding/vector DB 비채택 (Phase B 후보)**. ML 인프라 도입 부담 — Phase A prod 식별률 측정 후 ROI 결정.
- 2026-05-07: **Vision은 묘사만 추출, 약명 추정 X**. 추정 의존 제거가 핵심 가치.
- 2026-05-07: **L1/L2/L3 캐시 계층 분리**. L1=자체 DB(영구, 주 1회 batch), L2=기존 MfdsApiClient·DrugInfoClient(24h), L3=Vision(캐시 X). 책임 분리로 충돌 없음.
- 2026-05-07: **Phase A 한정**. Phase B(vision 재판정·embedding) 별도 spec.
- 2026-05-07: **API 호출 한도 분석 추가**. 개발계정 일 10,000/`service-key+API`. 동기화 1회 ~250 호출, 주 1회 cron이라 한도 안전. `MdcinGrnIdntfcInfoService03`는 기존 `MfdsApiClient` 활용신청과 별도 신청해야 한도 분리. 한도 초과 fallback은 stale L1 데이터로 식별 계속.
- 2026-05-08: **`identifyPillByAppearance` 도구 시그니처에서 `drugShape`/`lineFront` 제거** (issue #251). 운영 진단:
  - **AND 검색 갭**: `Specification.allOf` 구조에서 부정확 필드 한 줄이 결과를 0건으로 무력화. 흰색+타원형 단독 2,176건 → +TYLENOL/500 추가 시 0건 (실제 타이레놀=장방형이라 모양 조건 충돌).
  - **Vision 모양 추출 정확도 4/10 (실측)**: 식약처 reference 이미지 10개 모양 PoC. 정확=원형/타원형/삼각형/사각형. 부정확=장방형↔타원형, 마름모/오각/육각/팔각/반원→단순 모양으로 reduce. **사진 품질과 무관한 vision 본질 한계**(reference에서도 잘못).
  - **Vision 각인 추출은 사진 품질이 거의 좌우**: reference에선 `TYLENOL` 5/5 정확, 사용자 흐릿 사진에선 `IYHT/181/I0917/101/IHT` 매번 hallucinate. 안전 규칙("자신 없으면 null") 100% 무시.
  - **`lineFront`도 hallucinate 빈도 높음** (분할선 없는 reference 호출에서 "−형" 추가 빈번).
  - **결정**: 도구 시그니처는 `printFront`/`printBack`/`colorClass1`만. drugShape/lineFront는 LLM 자연어 응답에는 사용 가능(사용자 묘사). 색깔(10가지) + 정확한 각인이 매칭 정확도 거의 결정.
  - 후속 후보: vision 재판정(옵션 D, Phase B) — 단일 매칭 시 candidate ITEM_IMAGE를 vision으로 재대조해 false-match 자체 차단. 운영 데이터 보고 ROI 판단.
