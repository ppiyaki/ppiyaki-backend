---
feature: User.nickname NOT NULL 강제
slug: user-nickname-not-null
status: draft
owner: @goohong
scope: user
related_issues: []
related_prs: []
last_reviewed: 2026-05-28
---

# User.nickname NOT NULL 강제

> 발단: PR #414 (안부 알림) 구현 중 시니어 닉네임 NULL 가능성이 드러나 `WellbeingPingService`에 `nickname == null ? "" : nickname` 방어 코드가 들어갔다. 이 가드를 service에 두는 것은 [[feedback-no-service-null-guards]] 룰과 충돌하고, 본질적으로 도메인 불변식을 service에 누설한 것이다.

## 1) 개요 (What / Why)

`users.nickname`은 현재 코드/DB 양쪽에서 nullable로 선언되어 있다 (`@Column(name = "nickname")` + `nickname varchar(255)`). 그러나 모든 가입 경로(`signup`, `onboarding`, `senior add`)의 DTO는 `@NotBlank` 검증을 적용하고 있어 사실상 NULL이 들어올 수 없다.

**예외 한 곳**: 카카오 로그인 흐름(`AuthService.createNewUser(payload, ...)`)이 `KakaoIdTokenPayload.nickname`을 그대로 저장하는데, 카카오 OIDC profile 스코프는 nickname 미동의 가능 → null로 들어올 수 있다.

본 spec은 코드/DB/도메인 팩토리 전체에서 `User.nickname`을 NOT NULL로 강제하고, 카카오 흐름의 null 가능성을 명시적으로 메우는 것을 목표로 한다.

대상 액터: 백엔드.
해결 문제: 도메인 불변식 누설 + 코드/DB 선언과 실제 정책 불일치.

## 2) 사용자 시나리오 (개발자 관점)

- 카카오 로그인 사용자가 닉네임 동의를 거부 → 회원 가입은 성공하되 default 닉네임(예: `회원{userId}`)이 부여된다. 사용자는 가입 후 닉네임을 변경할 수 있다 (기존 `updateNickname` API).
- `User.createSenior(...)`로 시니어를 만들 때 nickname이 null이면 컴파일 흐름이 아닌 런타임에 `NullPointerException` 발생 → 코드 작성자가 즉시 인지.
- `WellbeingPingService`는 더 이상 `senior.getNickname() == null ? "" : senior.getNickname()`을 하지 않고 `senior.getNickname()` 그대로 사용한다.

## 3) 요구사항

### 기능 요구사항
- [ ] **DB 컬럼 NOT NULL**: `users.nickname`을 `VARCHAR(255) NOT NULL`로 변경. 마이그레이션 PR 별도.
- [ ] **운영 DB 사전 보강**: 운영 DB에 `nickname IS NULL`인 row가 존재하면 마이그레이션 직전 채워 넣어야 한다. 사용자 직접 확인 + 위임 (§8 Q1).
- [ ] **카카오 흐름 default 닉네임**: `AuthService.createNewUser(payload, ...)`에서 `payload.nickname()`이 null이면 default 값으로 대체 (§8 Q2에서 default 형식 결정).
- [ ] **User 생성자 가드**: `User` 생성자 (라인 80-96)에 `Objects.requireNonNull(nickname, ...)` 추가.
- [ ] **createSenior 팩토리 일관성**: `User.createSenior(String, LocalDate)`에도 `Objects.requireNonNull(nickname, ...)` 추가 (다른 오버로드 `createSenior(String, Gender)`에는 이미 있음).
- [ ] **schema.sql 갱신**: `nickname varchar(255) NOT NULL` 적용.
- [ ] **WellbeingPingService 가드 제거**: `senior.getNickname() == null ? "" : senior.getNickname()` → `senior.getNickname()`.
- [ ] **도메인 모델 문서 갱신**: `docs/ai-harness/06-domain-model.md` §5 users 표에 `nickname` 필드 NOT NULL 표기.

### 비기능 요구사항
- **호환성**: 운영 DB에 NULL row가 남아있는 상태로 마이그레이션 실행 시 실패. 사전 보강 필수.
- **관측성**: 카카오 default 닉네임 부여 시 INFO 로그 1줄(`kakao signup with default nickname (userId={}, providerUserId={})`).

## 4) 범위 / 비범위

### 포함
- DB 컬럼 NOT NULL + 마이그레이션
- User 생성자/팩토리 가드 추가
- 카카오 흐름 null fallback
- WellbeingPingService 가드 제거
- 도메인 모델 문서 갱신

### 제외 (Out of Scope)
- 다른 nullable 컬럼 (`login_id`, `gender`, `birth_date` 등) NOT NULL 강제 — 본 spec은 nickname 한정
- 닉네임 길이/금칙어 검증 — 별도 spec
- 닉네임 중복 방지 — 별도 spec
- 기존 카카오 가입 사용자의 닉네임 일괄 재발부 — 사전 보강 SQL로 1회성 처리

## 5) 설계

### 5-1) 도메인 모델

**`User` 엔티티 변경**:
- 생성자 (`User(...)`)에 `Objects.requireNonNull(nickname, "nickname must not be null")` 추가.
- `createSenior(String, LocalDate)` 팩토리에 동일 가드 추가.
- `@Column(name = "nickname", nullable = false, length = 255)` 으로 명시.

**`AuthService.createNewUser` 변경 (카카오)**:
```java
final String nickname = payload.nickname() != null && !payload.nickname().isBlank()
        ? payload.nickname()
        : DEFAULT_KAKAO_NICKNAME_PREFIX + savedUserId; // 또는 sub 기반
```
default 형식은 §8 Q2.

**`WellbeingPingService` 변경**:
- `senior.getNickname() == null ? "" : senior.getNickname()` 가드 제거.
- `senior.getNickname()` 직접 사용.

**`docs/ai-harness/06-domain-model.md` §5 users 표**:
- `nickname` 행을 `varchar(255) nullable` → `varchar(255) NOT NULL` 로 갱신.

### 5-2) API 엔드포인트
변경 없음.

### 5-3) 외부 연동
- **카카오 OIDC**: nickname 미제공 케이스 처리 명시화.

### 5-4) DB 마이그레이션

```sql
-- 1) NULL row 보강 (운영에서 사전 실행)
UPDATE users
SET nickname = CONCAT('회원', id)
WHERE nickname IS NULL;

-- 2) NOT NULL 적용
ALTER TABLE users
MODIFY COLUMN nickname VARCHAR(255) NOT NULL;
```

`schema.sql`도 함께 갱신. 마이그레이션은 보호 영역(`**/db/migration/**` 또는 `src/main/resources/**`) — `needs-human-review` 라벨 필수.

## 6) 작업 분할 (예상 PR 리스트)

- [ ] PR 1: spec 초안 (본 문서)
- [ ] PR 2: 카카오 흐름 default 닉네임 + User 생성자/팩토리 가드 + 도메인 모델 문서 갱신 (`scope:user`, 코드만)
- [ ] PR 3: schema.sql NOT NULL 적용 + `WellbeingPingService` 가드 제거 (`scope:user`, **사전: 운영 DB NULL row 보강 완료 + PR 2 머지 완료**)
- [ ] (사용자 위임) 운영 DB `UPDATE users SET nickname = ... WHERE nickname IS NULL` 실행

## 7) 테스트 전략

- **단위 테스트**:
  - `User` 생성자 nickname null 입력 시 `NullPointerException`.
  - `AuthService.createNewUser` 카카오 payload nickname null → default 적용.
- **E2E**:
  - 카카오 로그인 (mock된 verifier가 nickname null payload 반환) → 회원 생성 + default 닉네임 부여 검증.

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q1 | 운영 DB `users.nickname IS NULL` row가 실제로 존재하는가? | (a) 0건 — 마이그레이션 바로 가능 / (b) 존재 — 사전 UPDATE 후 진행 | @goohong (사용자 직접 SQL 확인) |
| Q2 | 카카오 default 닉네임 형식 | (a) `회원{userId}` / (b) `회원{sub}` (Kakao provider user id) / (c) `사용자` (고정) / (d) 가입 거부 (400 응답) | @goohong / spec 합의 시점 |
| Q3 | PR 2 머지와 PR 3 머지 사이 운영에 카카오 가입자가 nickname null로 들어올 위험 | (a) PR 2 머지 즉시 PR 3 머지 / (b) PR 2 머지 → 운영 배포 검증 → PR 3 머지 | @goohong / spec 합의 시점 |

## 9) 결정 로그

- 2026-05-28: 초안 작성 (status=draft). 발단은 PR #414 안부 알림 구현에서 노출된 `WellbeingPingService` nickname null 가드.
