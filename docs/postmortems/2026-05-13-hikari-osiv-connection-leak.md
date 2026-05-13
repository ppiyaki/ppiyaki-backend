---
title: Hikari connection pool 고갈 (OSIV + SSE async leak)
date: 2026-05-13
severity: SEV-2 (prod 핵심 기능 마비, 자동 회복 없이 재시작 필요)
duration: ~71분 (12:14 KST 누수 시작 → 13:25 첫 timeout → 13:30 컨테이너 재시작 회복)
status: resolved (PR #351 fix 대기)
related:
  - PR #347 (Hikari leak-detection-threshold 진단 도구)
  - PR #351 (root cause fix: open-in-view=false)
  - Issue #350
---

# 2026-05-13 Hikari connection pool 고갈 사후 분석

## 1. 한 줄 요약

Spring Boot의 default OSIV(`spring.jpa.open-in-view=true`)와 SSE async 엔드포인트(`ChatController.quickMessage`)의 조합으로 DB connection이 응답 종료 시점까지 잡혀, 시연 직전 chat 트래픽 burst에 의해 connection pool(10개)이 ~70분 만에 고갈됨. 모든 DB 호출 API가 timeout으로 마비.

## 2. Timeline (KST)

| 시각 | 사건 |
|---|---|
| 약 12:14 | 첫 connection leak 발생 (사후 추정 — DB processlist의 가장 오래된 Sleep connection time 4300초 기준) |
| 12:14 ~ 13:20 | chat 트래픽 누적되며 pool 점유 connection 증가, 정상 트래픽엔 영향 없는 단계 |
| 13:20:55 | 첫 SQL 에러 `HikariPool-1 Connection is not available, request timed out after 30000ms (total=10, active=10, idle=0, waiting=4)` 발생. thread `[undedElastic-13]` |
| 13:20 ~ 13:25 | 후속 요청들이 모두 30초 timeout. waiting queue 최대 13건. dashboard/medication-log 등 정상 API 마비 |
| 13:25 | 사용자(시연 진행) "서버 죽었어?" 보고 |
| 13:27 | 진단 진행 (MySQL processlist 10개 Sleep, 1300~4300초) → idle connection 10개 kill 시도. Hikari 내부 카운터 stale로 회복 안 됨 |
| 13:28:46 | prod 컨테이너 재시작 → 즉시 회복 |
| 13:30 ~ 13:50 | 시연 종료. 추가 leak 없음 (chat 트래픽 없어서) |
| 14:00 ~ 14:15 | leak-detection-threshold 60s 활성화 PR #347 작성 + v0.12.1 release 머지 + prod 배포 |
| 15:50 | 재현 burst 테스트 (chat 15건 + 중도 disconnect) |
| 15:53:32 | **Hikari leak detection이 정확한 stack trace 출력** — `ChatController.quickMessage:54 → ChatSessionPersistenceService.createSession (@Transactional)` 확정 |
| 15:55 ~ 15:57 | 재현 burst로 pool 또 고갈. 추가 leak 9건 발견 |
| 16:00 | prod 컨테이너 재시작 (재현 부작용 정리) + OSIV 비활성화 fix PR #351 작성 |

## 3. 영향 (Impact)

- 서비스: prod 모든 DB 호출 API 30초 timeout. dashboard/medication-log/notification 등 핵심 기능 사용 불가
- 사용자: 시연 진행 중인 사용자(구홍) 직접 영향. 외부 사용자 트래픽 없는 상태였음
- 데이터: 손상 없음 (트랜잭션 begin만 되고 statement 실행 전 단계라 dirty data 없음)
- 회복: 자동 회복 X, 수동 컨테이너 재시작 필수

## 4. Root cause

### 표층 증상
HikariCP pool size 10 모두 active, MySQL processlist는 모두 Sleep 상태. 즉 Hikari 측에선 "사용 중"이지만 MySQL은 "쿼리 없음" = **connection leak**.

### 진짜 원인 (PR #347 leak-detection-threshold로 stack trace 확보 후 확정)

```
WARN ProxyLeakTask: Connection leak detection triggered on thread http-nio-8080-exec-4
  at com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:127)
  at org.hibernate.engine.transaction.internal.TransactionImpl.begin(TransactionImpl.java:83)
  at org.springframework.orm.jpa.JpaTransactionManager.doBegin(JpaTransactionManager.java:420)
  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(...)
  at com.ppiyaki.chat.service.ChatSessionPersistenceService$$SpringCGLIB$$0.createSession(...)
  at com.ppiyaki.chat.controller.ChatController.quickMessage(ChatController.java:54)
```

### Mechanism — OSIV(Open Session In View) + SSE async race

1. `ChatController.quickMessage`가 한 메서드에서 두 가지를 함:
   ```java
   @PostMapping("/messages")
   public SseEmitter quickMessage(...) {
       final ChatSession session = persistenceService.createSession(userId);  // (a) @Transactional 동기
       return chatSessionService.sendMessageStream(...);                       // (b) SseEmitter 반환 = async
   }
   ```
2. (b)의 `SseEmitter` 반환 → servlet container가 request를 **async mode**로 전환
3. Spring Boot default `spring.jpa.open-in-view=true`가 활성 상태였음. 효과:
   - 정상(동기) MVC: trans commit 후 EntityManager만 view rendering까지 살아있고 connection은 반환됨 → 대부분 무해
   - **async + SSE**: EntityManager가 async dispatch 종료(SSE 스트림 종료)까지 살아있음. connection이 EM에 묶여 반환 지연
4. SSE는 long-lived. client가 중도 disconnect / max-time hit / network drop 시:
   - 정상 흐름이면 SseEmitter completion callback → AsyncContext complete → OSIV가 EM close → connection 반환
   - **race 조건**: chatStreamExecutor / Reactor boundedElastic이 in-flight, 동시에 client disconnect, AsyncContext가 정리 hook을 호출 못 하거나 늦음
   - EntityManager + connection이 영구적으로 누적

### 왜 이전엔 안 터졌는가
- 일반 동기 API(`/dashboard/*`, `/medication-logs` 등)는 OSIV 영향이 미미 (view rendering이 짧은 JSON serialization)
- SSE 추가는 2026-05-05 PR #212 (`feat(chat): 단발 채팅 API 구현`)에서 도입됨
- 약 일주일간 chat 트래픽이 적어 누수 누적이 미미했음
- 시연 직전 chat 사용 burst가 처음으로 누적량을 넘김

## 5. 진단 (Detection)

| 단계 | 도구/방법 | 소요 시간 |
|---|---|---|
| 1차 인지 | 사용자 "서버 죽었어?" 보고 | 사용자가 시도 후 즉시 |
| 1차 진단 | `curl prod` + `docker logs` → "Connection is not available" 발견 | 5분 |
| 2차 진단 | MySQL processlist → 10개 Sleep, 1300~4300초 → leak 확정 | 10분 |
| 1차 가설 | 첫 에러 thread `[undedElastic-13]` → boundedElastic + MCP tool 흐름 추정 (틀린 가설) | 30분 |
| 가설 검증 한계 | 코드/설정/timeout 모두 정상. 정황 증거뿐. 단정 불가 | 30분 |
| 진단 도구 도입 | PR #347 `leak-detection-threshold: 60000` 활성화 + prod 배포 | 30분 |
| 재현 + 확정 | burst 테스트 → Hikari leak detection이 정확한 stack trace 출력 | 5분 |

## 6. 회복 / Fix

### 즉시 회복
- prod 컨테이너 재시작 (idempotent 데이터라 손실 없음). 평균 ~30초 다운타임.

### 영구 fix — PR #351
`application.yml`에 `spring.jpa.open-in-view: false` 1줄 추가.

```yaml
spring:
  jpa:
    open-in-view: false
```

효과:
- 트랜잭션 commit 직후 connection 즉시 반환
- async dispatch 종료 시점과 connection 생명주기 분리
- SSE/streaming 엔드포인트가 어떻게 동작하든 connection leak 차단

회귀 위험: Controller나 view에서 lazy collection 접근 시 `LazyInitializationException`. 본 프로젝트는 Service 레이어에서 DTO 변환 패턴이라 무관 (전체 테스트 슈트 통과로 검증).

## 7. What is OSIV?

**Open Session In View** (또는 Open EntityManager In View). Spring + JPA에서 사용하는 패턴.

### 동작
- HTTP request 시작 시 Hibernate Session(EntityManager) 열기
- request 처리 끝나고 view rendering까지 Session 유지
- response 완료 후 Session close

### 의도된 장점
- Controller/view에서 entity의 lazy collection을 자유롭게 접근 가능
- "그냥 `user.getOrders().size()`" 같은 코드가 LazyInitializationException 없이 동작
- 개발 편의성 ↑

### 실제 문제
- **N+1 query**: view rendering 도중 lazy loading 트리거 → 의도치 않은 추가 query
- **Connection 생명주기 비효율**: 트랜잭션 commit 후에도 connection을 유지 (HikariCP의 active count 증가)
- **async/streaming과의 race**: SSE/WebFlux 패턴과 결합 시 connection 누수 (본 사고)
- **트랜잭션 경계 모호화**: Service 레이어 트랜잭션 안에서만 DB 접근해야 한다는 원칙이 무너짐

### 업계 합의
- Hibernate 핵심 컨트리뷰터들이 anti-pattern으로 규정 (Vlad Mihalcea 등)
- Spring Boot 자체도 startup 시 WARN 로그를 띄움:
  > "spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning"
- 그럼에도 Spring Boot가 default=true로 유지하는 이유: 기존 프로젝트와의 호환성 + 신규 개발자 진입 장벽 낮춤
- **신규 프로젝트는 항상 `open-in-view: false`로 시작하는 것이 best practice**

## 8. What went well / wrong

### Well
- 사용자 보고가 빠름 (시연 진행 중 즉시 인지)
- prod ssh + DB 직접 접근 인프라 갖춰져 있어 진단 속도 빠름
- Hikari leak-detection-threshold가 정확한 stack trace를 제공 → 재현 한 번으로 root cause 확정
- 재현 가능 (burst 패턴 정의됨)

### Wrong
- **모니터링 부재**: pool 고갈을 사용자 보고로 처음 안 것. prod 메트릭 alert 없음
- **OSIV default 인지 부족**: 신규 프로젝트 init 시 비활성화 명시 안 함. 팀 컨벤션에 없었음
- **async 엔드포인트 도입 PR(#212)에 OSIV 영향 검토 없음**: SSE는 OSIV와 상극임을 인지하지 못함
- **leak-detection 사전 활성화 안 함**: prod 진단 도구로 갖췄어야 함 (사고 후에야 PR #347로 도입)
- **첫 가설(boundedElastic / MCP tool) 추정으로 30분 소비**: leak-detection 도구 없는 상태에서 정황 추론으로 길을 헤맴

## 9. Action items

| # | 항목 | 우선순위 | 담당/기한 |
|---|---|---|---|
| 1 | PR #351 머지 + main 배포 — OSIV 비활성화 | 🔴 P0 | 사용자 컨펌 후 즉시 |
| 2 | leak-detection-threshold prod 적용 검증 (PR #347 이미 머지 완료) | ✅ 완료 | — |
| 3 | prod 모니터링 alert 추가 — HikariCP active connection 비율 임계치 | 🟠 P1 | 추후 spec 작성 |
| 4 | async 엔드포인트(SSE/WebFlux) 추가 PR 체크리스트에 "OSIV 영향 검토" 항목 추가 | 🟡 P2 | `docs/ai-harness/03-quality-gates.md` 업데이트 |
| 5 | Hikari pool size 검토 (default 10 → 20~30 buffer) | 🟢 P3 | OSIV fix 후 트래픽 데이터 보고 결정 |
| 6 | spring-boot 신규 프로젝트 template에 `open-in-view: false` 기본 포함 | 🟢 P3 | 다음 프로젝트 init 시 |

## 10. 시간 비용

| 단계 | 시간 |
|---|---|
| 사고 인지 → prod 회복 | ~10분 (사용자 보고 → 컨테이너 재시작) |
| 1차 진단 (가설 단계, 정황 추론) | ~50분 |
| 진단 도구 추가 (PR #347) + prod 배포 | ~30분 |
| 재현 + root cause 확정 + fix PR | ~30분 |
| **총 사고 처리 시간** | **약 2시간** |

leak-detection이 사전 활성화돼 있었다면 1차 진단 50분이 5분 이내로 단축 가능했음. action item #3, #4의 가치.
