---
feature: 안부 알림 (시니어 → 보호자 콕찌르기)
slug: wellbeing-ping
status: draft
owner: @goohong
scope: user
related_issues: []
related_prs: []
last_reviewed: 2026-05-28
---

# 안부 알림 (시니어 → 보호자 콕찌르기)

> 부모 spec: 없음. 신규 알림 카테고리(`WELLBEING_PING`)를 도입한다.
> 비교 대상: `FAMILY_SAFETY` (시니어 48시간 미접속 시 자동 발송) — 본 기능은 **시니어 능동 트리거**라 카테고리를 분리한다.

## 1) 개요 (What / Why)

시니어가 보호자 정보 화면에서 가벼운 신호 하나로 "잘 지내고 있어요"를 전달하고 싶을 때 사용하는 1-tap 푸시. 페이스북 옛 "콕찌르기" UX의 한국형 변형으로, **의미 없는 신호 1개로 안부를 전한다**.

대상 액터: 시니어.
해결 문제: 메시지 작성/통화 진입장벽 없이 보호자에게 "잘 있음" 시그널을 전달.

## 2) 사용자 시나리오

- 시니어 김장군은 보호자 화면을 열어 본인을 돌보는 보호자 목록을 본다. 특정 보호자 카드의 "안부 전하기" 버튼을 누른다. 보호자는 폰에서 "김장군 어르신이 안부를 전했어요." 푸시를 받는다.
- 시니어가 30초 뒤 같은 보호자에게 다시 안부를 보낸다 → 쿨다운 1분 미경과로 429 응답. 1분 뒤 재시도하면 성공.
- 시니어가 보호자 A, B에게 동시에 안부를 보낸다 → 각자 쿨다운이 독립적이므로 둘 다 발송된다.

## 3) 요구사항

### 기능 요구사항
- [ ] **단방향 발송**: 시니어 → 보호자 방향만 허용. 보호자가 시니어에게 보내는 endpoint는 만들지 않는다.
- [ ] **CareRelation 검증**: 발신 시니어와 수신 보호자 사이에 활성 `CareRelation` (`deletedAt IS NULL`)이 있어야 한다. 없으면 403.
- [ ] **쿨다운 1분**: 동일 `(seniorId, caregiverId)` 쌍에 대해 최근 60초 이내 발송 이력이 있으면 429 응답. Redis 기반.
- [ ] **푸시 발송**: 보호자의 활성 `DeviceToken` 전부에 FCM 푸시 1회 발송. `PushSender.send` 결과 `tokenInvalid` 시 토큰 비활성화 (기존 dispatcher 동일 패턴).
- [ ] **알림함 row 미생성**: `Notification` 테이블에 row를 만들지 않는다. 푸시만 전달 후 끝낸다.
- [ ] **푸시 본문**: 제목 `"안부 알림"`, 본문 `"{시니어 닉네임} 어르신이 안부를 전했어요."`. payload data: `{ "category": "WELLBEING_PING", "seniorId": "<id>" }`.
- [ ] **NotificationCategory enum 추가**: `WELLBEING_PING` 값을 `NotificationCategory` enum에 추가한다 (FCM payload의 `category` 식별자 일관성을 위해). 단, 본 카테고리는 알림함에 저장되지 않으므로 enum 추가는 푸시 식별자 + 향후 통계용 목적에 한정.

### 비기능 요구사항
- **응답 시간**: p95 ≤ 500ms. FCM 발송은 동기 호출이지만 토큰당 ~100ms 이내 예상.
- **관측성**: `WELLBEING_PING dispatched (sender={seniorId}, receiver={caregiverId}, tokens={count})` INFO 로그 1줄.
- **레이트리밋 관측성**: 쿨다운 컷오프 시 DEBUG 로그 1줄.
- **인증**: JWT 필수. 발신 시니어는 토큰의 사용자 ID에서 추출 (path/body에서 받지 않음).

## 4) 범위 / 비범위

### 포함
- 새 `WellbeingPingController` + `WellbeingPingService`
- `NotificationCategory.WELLBEING_PING` enum 값 추가
- Redis 기반 쿨다운 (TTL 60초)
- 기존 `PushSender` / `DeviceTokenRepository` / `CareRelationRepository` 재사용
- E2E 성공 케이스 + 쿨다운 차단 케이스

### 제외 (Out of Scope)
- 보호자 → 시니어 방향
- 시니어 → 시니어 또는 보호자 → 보호자 (동일 역할 간)
- 메시지 본문 사용자 입력 (고정 1종 본문만)
- 메시지 본문 다양성 (랜덤 멘트 풀) — 추후 검토
- 알림함 영속화 — `WELLBEING_PING` 카테고리는 `Notification` 테이블에 row 만들지 않음
- 보호자가 직접 "안부 알림 받지 않기" 설정 끄기 (`NotificationSettings`에 토글 추가) — 1차에선 무조건 발송, 후속 spec에서 검토
- 안부 발송 통계/이력 조회 API
- 안부 받은 직후 보호자가 회신("나도 잘 있어요") 흐름 — 별도 spec

## 5) 설계

### 5-1) 도메인 모델

**`NotificationCategory` enum 변경**:
- `WELLBEING_PING` 추가. 의미: 시니어가 보호자에게 보내는 능동적 안부 신호.
- 알림함 영속화는 하지 않지만 FCM payload `category` 식별자 + 향후 발송 통계용으로 enum 등재.

**신규 빈**:
- `WellbeingPingService` — care relation 검증 + 쿨다운 체크 + 푸시 발송. `@Transactional` 부착 — `DeviceToken.deactivate()`가 JPA dirty checking으로 update SQL을 발생시키기 때문 (`notifications` row는 만들지 않지만 `device_tokens.is_active` 갱신은 발생).
- `WellbeingPingController` — endpoint 노출.
- `WellbeingPingCooldownStore` (or 같은 책임의 컴포넌트) — Redis 기반 쿨다운 SETNX.

**`docs/ai-harness/06-domain-model.md` 갱신 항목** (이번 PR과 동기):
- §4 유비쿼터스 랭귀지: "안부 알림 / Wellbeing Ping" 용어 등재.
- §알림 카테고리 표가 있다면 `WELLBEING_PING` 행 추가.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/notifications/wellbeing-pings | 시니어가 보호자에게 안부 신호 발송 | 필수 (SENIOR) | `WellbeingPingCreateRequest` | 204 No Content |

**`WellbeingPingCreateRequest`**:
```json
{ "caregiverId": 123 }
```

**응답 코드**:
- `204 No Content` — 발송 성공 (보호자 활성 토큰 0개인 경우도 204. 로그에 `tokens=0` 기록)
- `400 Bad Request` — `caregiverId` 누락/형식 오류
- `401 Unauthorized` — JWT 없음/만료
- `403 Forbidden` — 발신자가 SENIOR 역할이 아니거나, 수신자와 CareRelation 없음
- `404 Not Found` — `caregiverId`에 해당하는 사용자 없음
- `429 Too Many Requests` — 쿨다운 미경과 (Retry-After 헤더에 남은 초)

### 5-3) 외부 연동

- **FCM (PushSender)**: 기존 인터페이스 그대로 사용. `PushPayload(title, body, data)` 생성 후 토큰별 `send`.
- **Redis**: 쿨다운 키 `wellbeing-ping:cooldown:{seniorId}:{caregiverId}` TTL 60초. SETNX 패턴 (`SET key value NX EX 60`). 키 존재 시 TTL 조회해 Retry-After 계산.

### 5-4) 데이터 흐름

```
[시니어] POST /api/v1/notifications/wellbeing-pings { caregiverId }
   ↓
[Controller] JWT에서 seniorId 추출
   ↓
[Service] 
   1. 수신자 user 조회 (없으면 404)
   2. CareRelation 존재 확인 (없으면 403)
   3. Redis SETNX wellbeing-ping:cooldown:{seniorId}:{caregiverId} TTL=60
      → 실패 시 TTL 조회 → 429 Retry-After
   4. 보호자 활성 DeviceToken 조회
   5. 각 토큰에 FCM 발송 (tokenInvalid 시 deactivate)
   6. 204 응답
```

### 5-5) DB 마이그레이션

**없음**. enum 값 추가만 코드 변경. `Notification` 테이블에 row 안 만들기 때문에 스키마 변경 불필요.

## 6) 작업 분할 (예상 PR 리스트)

- [ ] PR 1: spec 초안 (`docs/features/wellbeing-ping.md`) — 본 문서
- [ ] PR 2: 구현 (`WellbeingPingController` + `WellbeingPingService` + Redis cooldown + enum 추가 + E2E)
- [ ] (PR 2와 같은 PR에서) Notion API 명세 갱신 + `docs/ai-harness/06-domain-model.md` 유비쿼터스 랭귀지 등재

## 7) 테스트 전략

- **단위 테스트**:
  - `WellbeingPingService`: care relation 없음 → `ForbiddenException`. 수신자 없음 → `NotFoundException`. 쿨다운 미경과 → `TooManyRequestsException` (또는 도메인 예외).
  - 쿨다운 store: SETNX 성공/실패 분기.
- **통합 테스트**: `@SpringBootTest` + embedded Redis(기존 redis-infra-setup이 어떤 구성이냐에 따라 결정 — verify 필요) + Mock FCM.
- **E2E (RestAssured) 필수**:
  - 성공 케이스: 시니어 JWT로 발송 → 204, 푸시 발송 횟수 = 토큰 수.
  - 쿨다운 케이스: 즉시 2회 호출 → 두 번째 429.
  - 권한 케이스: CareRelation 없는 보호자에게 발송 → 403.
  - 역할 케이스: 보호자 JWT로 호출 → 403.

## 8) 오픈 질문

> 모든 항목 합의 완료. §9 결정 로그 참조.

## 9) 결정 로그

- 2026-05-28: 초안 작성 (status=draft). 디스코드 백로그 4번 기반 [[backlog-2026-05-27]].
- 2026-05-28: 합의 완료 항목 — 방향=시니어→보호자, 쿨다운=1분, 영속화=푸시만(DB row X), 본문 톤=안부 알림.
- 2026-05-28: 오픈 질문 5건 합의 — Q1 (a) 1차에는 무조건 받기, Q2 (a) 보호자(수신자)별 쿨다운, Q3 (a) 429 + Retry-After, Q4 (a) 토큰 0개여도 204, Q5 (a) `NotificationCategory.WELLBEING_PING` enum 추가.
- 2026-05-28: §5-1 `@Transactional 불필요` 문구를 구현 사실에 맞게 정정 — `DeviceToken.deactivate()` dirty checking으로 update SQL이 발생하므로 `@Transactional` 부착 (구현 PR #414 머지 직후 정정).
