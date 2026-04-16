---
feature: MCP 서버 도입 및 도메인 Tool 노출
slug: mcp-server-foundation
owner: @goohong
scope: infra
related_issues: []
related_prs: []
status: draft
last_reviewed: 2026-04-16
---

# MCP 서버 도입 및 도메인 Tool 노출

## 1) 개요 (What / Why)
- AI 에이전트(Claude·GPT 기반)가 우리 백엔드 도메인 기능(약물 검색·등록·DUR 점검 등)을 표준 프로토콜로 호출할 수 있도록 **MCP(Model Context Protocol) 서버를 ppiyaki-backend에 내장**한다.
- Spring AI의 `spring-ai-starter-mcp-server-webmvc`(1.1.4 기준)를 활용해 자체 프로토콜 구현 회피.
- 첫 번째 노출 도메인은 **약물 검색·매칭** + **DUR 점검**. 처방전 OCR 자동 등록 시나리오가 핵심 use case.

## 2) 사용자 시나리오
- AI 에이전트가 시니어와 대화 중 "이부프로펜이랑 같이 먹으면 안 되는 약 있어요?" 질문을 받음 → `check_dur` MCP tool 호출 → DUR 결과 기반 답변.
- 처방전 사진 분석 후 AI가 `match_medicine_from_ocr` → `register_medicine` → `register_medication_schedule` 순으로 호출해 자동 등록.

## 3) 요구사항

### 기능 요구사항
- [ ] `spring-ai-starter-mcp-server-webmvc:1.1.4` 의존성 추가.
- [ ] HTTP/SSE transport로 MCP 엔드포인트 노출 (경로: `/mcp`).
- [ ] 다음 MCP tool들 등록:
  - `search_medicine` — `MedicineSearchService.search` 래핑
  - `match_medicine_from_ocr` — `MedicineMatchService.match` 래핑
  - `register_medicine` — `MedicineService.create` 래핑 (itemSeq 필수)
  - `register_medication_schedule` — `MedicationScheduleService.create` 래핑
  - `check_dur` — `DurCheckService.check` 래핑
- [ ] **인증**: 기존 JWT 재사용. AI 에이전트는 사용자 JWT를 MCP 호출의 Authorization 헤더로 전달. 도메인 Service의 권한 검증(소유자/연동 보호자) 그대로 적용.
- [ ] 각 tool은 **JSON Schema 기반 입출력** (Spring AI MCP starter가 자동 생성).
- [ ] tool 호출도 일반 REST와 동일하게 `GlobalExceptionHandler` 통과. 응답에 `ErrorResponse` 호환 구조.
- [ ] **rate limit**: 사용자별 분당 호출 수 제한 (외부 API 쿼터 보호 + 비용 통제).

### 비기능 요구사항
- 도구 정의 변경 시 자동 schema 재생성, 별도 배포 사이클 불필요.
- MCP 호출 로그에 `userId`, `tool`, `duration`, `success` 기록.
- tool 호출 실패는 MCP 표준 에러 응답 형식 준수.

## 4) 범위 / 비범위

### 포함
- `spring-ai-starter-mcp-server-webmvc` 도입
- 위 5개 tool wrapping
- JWT 인증 통합 (Spring Security 기존 필터 체인 활용)
- rate limit (Bucket4j 등 단순 in-memory)
- tool별 단위·통합 테스트

### 제외 (Out of Scope)
- **MCP client 구현** — AI 에이전트는 외부(클라이언트 앱 또는 별도 AI 서비스)에서 도는 것 가정
- **STDIO transport** — HTTP/SSE만 지원
- **분산 rate limit** — in-memory로 시작, 다중 인스턴스 시 별도 PR
- **OAuth/OIDC for MCP** — JWT 재사용으로 충분
- **prompt/resource 노출** — tool만 지원 (MCP의 `@McpResource`, `@McpPrompt` 비사용)

## 5) 설계

### 5-1) 의존성

```gradle
implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc:1.1.4'
```

기존 `spring-boot-starter-web` + `spring-boot-starter-security`와 호환.

### 5-2) Tool 등록 — 어노테이션 기반

```java
@Component
public class MedicineMcpTools {

    private final MedicineSearchService searchService;
    private final MedicineMatchService matchService;
    private final MedicineService medicineService;

    public MedicineMcpTools(...) { ... }

    @McpTool(name = "search_medicine",
             description = "Search Korean MFDS DUR drug catalog by name.")
    public List<MedicineCandidate> searchMedicine(
            @McpToolParam(description = "Search query (drug name or partial)", required = true) String query,
            @McpToolParam(description = "Form filter (정/캡슐/시럽 etc)") String form,
            @McpToolParam(description = "Max results, default 10") Integer limit
    ) {
        return searchService.search(query, new SearchOptions(...));
    }

    @McpTool(name = "match_medicine_from_ocr",
             description = "Match an OCR-extracted drug text to MFDS itemSeq with auto-correction.")
    public MatchResult matchMedicineFromOcr(
            @McpToolParam(description = "OCR raw text", required = true) String ocrText,
            @McpToolParam(description = "Optional dosage hint") String dosage,
            @McpToolParam(description = "Optional form hint") String form
    ) {
        return matchService.match(ocrText, ...);
    }

    @McpTool(name = "register_medicine",
             description = "Register a medicine for the authenticated user. itemSeq required.")
    public Medicine registerMedicine(
            @McpToolParam(required = true) Long itemSeq,
            @McpToolParam(required = true) String name,
            @McpToolParam Integer totalAmount
    ) {
        Long userId = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return medicineService.create(userId, ...);
    }
}
```

`@McpTool` 어노테이션을 가진 메서드는 Spring AI starter가 자동 등록 + JSON Schema 자동 생성.

### 5-3) 인증·권한

기존 Spring Security 필터 체인이 `/mcp` 경로에도 적용됨:
- JWT 검증 (`JwtAuthenticationFilter`)
- `@AuthenticationPrincipal` 또는 `SecurityContextHolder`로 userId 획득
- 도메인 Service의 권한 로직(소유자/보호자) 그대로 동작

### 5-4) Rate Limit

`Bucket4j`(인메모리)로 사용자별 토큰 버킷:
- 기본: 분당 60회 (tool 호출 수, 모든 tool 합산)
- 초과 시 HTTP 429 + 에러 응답
- 향후 Redis로 분산 가능

### 5-5) 에러 처리

- 도메인 예외(`BusinessException`) → `GlobalExceptionHandler`가 처리하면 MCP 응답에서 자동으로 에러 객체로 직렬화
- MCP 표준 에러 코드 (`-32000` ~ `-32099`)는 라이브러리가 매핑

### 5-6) DB 마이그레이션
- 없음 (인프라 변경, 도메인 모델 변경 없음)

## 6) 작업 분할 (예상 PR 리스트)

선행: `medicine-search` PR 머지

- [ ] PR 1 `docs(infra)`: 본 spec 커밋
- [ ] PR 2 `chore(infra)`: spring-ai-starter-mcp-server-webmvc 의존성 추가. `application*.yml`에 MCP 설정 (path, sync mode 등). `needs-human-review`.
- [ ] PR 3 `feat(infra)`: MedicineMcpTools (search/match) 노출 + 단위 테스트
- [ ] PR 4 `feat(infra)`: register_medicine·register_medication_schedule tool 노출
- [ ] PR 5 `feat(infra)`: check_dur tool 노출 (DUR spec 머지 후)
- [ ] PR 6 `feat(infra)`: Bucket4j 기반 rate limit + 단위 테스트
- [ ] PR 7 `test(infra)`: MCP E2E — tool 호출, 권한 실패, rate limit 검증

## 7) 테스트 전략

### 단위
- 각 tool 메서드 — 입력 파라미터 검증, Service 호출, 응답 매핑
- Rate limiter — 토큰 소진/회복

### 통합 (E2E)
- MCP HTTP/SSE endpoint를 RestAssured로 호출
- JWT 인증 통과/실패
- 각 tool 정상/실패 시나리오
- rate limit 초과 시 429

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q-MCP-4 | tool 호출 audit log 보관 기간 | (a) 30일 / (b) 90일 | @goohong / PR 7 전 |

## 9) 결정 로그
- 2026-04-16: 초안 작성
- 2026-04-16: **옵션 A — 앱 내장 + HTTP SSE + JWT 재사용** (이전 medicine-dur §9 결정 로그와 일치)
- 2026-04-16: Spring AI 1.1.4 starter 채택 — webmvc 변종 사용 (기존 스택 유지)
- 2026-04-16: 어노테이션 기반 (`@McpTool`/`@McpToolParam`) — Spring AI 권장 패턴
- 2026-04-16: rate limit는 in-memory 시작, Redis 마이그레이션 TODO (medicine-dur 캐시와 동일 패턴)
- 2026-04-16: **Q-MCP-1 결정** — MCP path `/mcp` (Spring AI 기본 경로) 채택. `/api/v1/mcp`는 REST 컨벤션이나 MCP는 별도 프로토콜이므로 기본 경로 유지.
- 2026-04-16: **Q-MCP-2 결정** — rate limit 기본 60회/분. 외부 API 쿼터(식약처 10K/일 ÷ 인당 평균 사용 고려) 및 비용 통제 기준. 초과 시 429 반환.
- 2026-04-16: **Q-MCP-3 결정** — SYNC (Spring MVC 기반). 기존 스택(spring-boot-starter-web) 유지, Reactor 도입 불필요. `spring-ai-starter-mcp-server-webmvc` 변종과 일치.
- 2026-04-16: **구현 순서 확정** — ① item_seq 컬럼 추가 → ② medicine-search (+ medicine-dur 병렬 가능) → ③ medicine-dur → ④ prescription-ocr 통합본 → ⑤ mcp-server-foundation (현재 spec).
