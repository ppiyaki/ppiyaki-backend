---
feature: 시니어 식사 시간대 기본값
slug: meal-time-defaults
status: draft
owner: @goohong
scope: user
related_issues: [#221]
related_prs: []
last_reviewed: 2026-05-07
---

# 시니어 식사 시간대 기본값

## 1) 개요 (What / Why)
시니어가 평소 아침/점심/저녁 식사 시간을 사전 설정해두고, 처방전 기반 복약 일정 등록 시 이 시간대를 활용한다. 매번 약마다 수기로 시각을 입력하는 마찰을 줄이고, 향후 처방전 confirm 시 자동 schedule 생성의 기반 데이터를 마련한다.

대상 사용자: 시니어 (본인 시간대 설정), 보호자 (조회만, 갱신은 시니어 본인).

## 2) 사용자 시나리오
- **온보딩**: 시니어가 회원가입 직후 아침 8시 / 점심 12시 30분 / 저녁 6시 30분으로 식사 시간을 설정한다.
- **약 등록 흐름**: 시니어/보호자가 처방전 confirm 후 schedule 등록 화면에서, 약마다 "아침/점심/저녁" 슬롯을 고르면 프론트가 시니어의 mealTimes 값을 읽어 LocalTime을 채워 백엔드에 보낸다 (Phase 1 한정 — 백엔드는 슬롯 개념을 모름).
- **수정**: 시니어가 식사 시간이 바뀌면 `PUT /api/v1/users/me/meal-times`로 3개 시각을 한 번에 갱신한다.

## 3) 요구사항
### 기능 요구사항
- [ ] `User` 엔티티에 `breakfastTime` / `lunchTime` / `dinnerTime` (LocalTime, nullable) 필드 추가
- [ ] `PUT /api/v1/users/me/meal-times` — 시니어 본인이 3개 시각을 한 번에 갱신 (전체 PUT, 3개 모두 필수)
- [ ] `GET /api/v1/users/me` 응답에 `mealTimes` 객체 추가. 모두 미설정이면 `null`, 일부만 설정 가능
- [ ] 권한: 시니어 본인만 갱신 (보호자 대리 갱신 불가, Phase 2 이후 검토)
- [ ] DB 마이그레이션 SQL 준비, `schema.sql` 동기화

### 비기능 요구사항
- **성능**: 갱신/조회 모두 단일 user row 작업. 추가 인덱스 불필요.
- **신뢰성**: 입력값 검증 — `LocalTime` 형식만 (00:00:00 ~ 23:59:59). 시각 간 순서(`breakfast < lunch < dinner`) 강제하지 않음 — 사용자 자율.
- **보안**: 의료정보 아니므로 일반 사용자 데이터 정책. 로그에 시각 노출 OK.
- **관측성**: 별도 메트릭 불필요. 일반 access log로 충분.

## 4) 범위 / 비범위 (중요)

### 포함
- User에 시간대 컬럼 3개
- 갱신/조회 API
- 시니어 본인 권한 체크
- 도메인 문서 + Notion API 명세 동기화
- E2E 테스트 (성공 케이스 1개 이상)

### 제외 (Out of Scope)
- **`MealSlot` enum 도입** → Phase 2
- **`MedicationSchedule`에 슬롯 매핑** → Phase 2
- **시니어 시간 변경 시 기존 schedule cascade 갱신** → Phase 2
- **처방전 confirm 시 schedule 자동 생성** → Phase 3
- **OCR schedule 텍스트 → 슬롯 매핑 파서** → Phase 3
- **보호자 대리 갱신** → 후속 결정
- **`isOnboarded` 정의 변경** (mealTimes 미설정도 onboarded로 유지)
- **시각 간 순서 검증** — 사용자 자율
- **부분 갱신 (PATCH)** — Phase 1은 전체 PUT만

## 5) 설계

### 5-1) 도메인 모델
- 컨텍스트: `user` (참조: `docs/ai-harness/06-domain-model.md §5 users`)
- `User`에 LocalTime 컬럼 3개 (`breakfast_time`, `lunch_time`, `dinner_time`, 모두 nullable)
- 별도 테이블 도입 X — MVP 과잉. 향후 슬롯 추가(야간/간식) 필요 시 별도 테이블로 마이그레이션

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| PUT | `/api/v1/users/me/meal-times` | 시니어 본인의 식사 시간대 3개 일괄 갱신 | 필수 (시니어) | `MealTimesUpdateRequest` | `UserMeResponse` |
| GET | `/api/v1/users/me` | 내 정보 + mealTimes (기존 엔드포인트 응답 확장) | 필수 | — | `UserMeResponse` |

#### MealTimesUpdateRequest (전체 PUT, 3개 모두 필수)
```json
{
  "breakfast": "08:00:00",
  "lunch": "12:30:00",
  "dinner": "18:30:00"
}
```
- 모두 `@NotNull LocalTime` (jakarta.validation)
- 형식: ISO-8601 LocalTime (`HH:mm:ss`)

#### UserMeResponse (확장)
```json
{
  "id": 16,
  "nickname": "테스트시니어",
  "role": "SENIOR",
  "isOnboarded": true,
  "mealTimes": {
    "breakfast": "08:00:00",
    "lunch": "12:30:00",
    "dinner": "18:30:00"
  }
}
```
- `mealTimes`는 객체 — 3개 모두 미설정이면 `null` (객체 자체 누락), 일부만 설정 가능

#### 에러 응답
| 상황 | HTTP | code |
|---|---|---|
| 입력 누락/형식 오류 | 400 | `COMMON_001` |
| 인증 없음/만료 | 401 | `AUTH_001` |
| 권한 없음 (시니어가 아닌 경우 — Phase 1 한정) | 403 | `FORBIDDEN` (별도 로직 필요 시) |

> **권한 검증 메모**: 본 PR 범위에서는 `@AuthenticationPrincipal Long userId`로 받은 사용자의 `me` 엔드포인트라 별도 시니어 검증 불필요. 보호자가 호출해도 자기 자신을 갱신하므로 무해. 단 보호자 mealTimes는 의미 없음 — Phase 2에서 슬롯 도입 시 시니어로 한정 검토.

### 5-3) 외부 연동
없음.

### 5-4) 데이터 흐름
```
시니어 클라이언트
  ↓ PUT /users/me/meal-times { breakfast, lunch, dinner }
UserController.updateMealTimes(userId, request)
  ↓
UserService.updateMealTimes(userId, request)
  ↓
User.updateMealTimes(breakfast, lunch, dinner)
  ↓ JPA dirty checking
DB: UPDATE users SET breakfast_time=?, lunch_time=?, dinner_time=? WHERE id=?
  ↓
UserMeResponse.from(user)
```

### 5-5) DB 마이그레이션
```sql
ALTER TABLE users
    ADD COLUMN breakfast_time TIME(6) NULL,
    ADD COLUMN lunch_time TIME(6) NULL,
    ADD COLUMN dinner_time TIME(6) NULL;
```
- prod NCP MySQL에 머지 직후 수동 실행 (보호 영역)
- `src/main/resources/schema.sql`은 Hibernate가 생성한 `time(6)` 형식과 동일

## 6) 작업 분할
단일 PR로 진행 (Phase 1 자체가 작은 범위).

- [ ] PR `feat(user)`:
  - 엔티티 + 도메인 메서드
  - DTO (request, response 갱신)
  - Service + Controller 메서드
  - 마이그레이션 SQL + schema.sql
  - 도메인 문서 §5 갱신
  - Notion API 명세 항목 (PUT 신규 / GET 갱신)
  - E2E + 단위 테스트

## 7) 테스트 전략
- **단위 테스트**: `User.updateMealTimes` — null 거부, LocalTime 보관, 부분 변경 케이스
- **E2E (RestAssured)**:
  - 성공: 인증된 사용자 PUT → 200 → GET /users/me로 mealTimes 확인
  - 입력 누락: 한 필드 빠지면 400
  - 인증 없음: 401
- 외부 연동 mock 불필요

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q1 | 보호자 대리 갱신 허용? | (a) Phase 1부터 보호자도 가능 / (b) Phase 1은 본인만, Phase 2에서 검토 | @goohong / Phase 2 진입 전 — **(b)로 잠정 결정** |
| Q2 | 시각 간 순서 강제? | (a) breakfast<lunch<dinner / (b) 자율 | **(b) 자율 결정** — 사용자가 야간 근무 등 비전형 패턴 가질 수 있음 |
| Q3 | 미설정 상태 표현 | (a) 3개 모두 null이면 mealTimes=null / (b) 항상 객체 반환 (필드별 null) | **(a) 결정** — 클라이언트 분기 단순 |

## 9) 결정 로그
- 2026-05-07: 초안 작성 (status=draft). Phase 1 범위 한정. Phase 2~3는 후속 spec.
- 2026-05-07: **데이터 모델 = User 컬럼 3개**. 별도 테이블 도입 X. 이유: MVP 과잉. 향후 슬롯 확장(야간/간식) 시점에 마이그레이션 검토.
- 2026-05-07: **전체 PUT 채택**. 부분 갱신(PATCH) 불채택. 이유: Phase 1 단순화. 사용자 사용 빈도 낮아 PATCH 가치 작음.
- 2026-05-07: **시각 순서 검증 미도입**. 사용자 자율 (야간 근무 등 비전형 패턴 허용).
- 2026-05-07: **보호자 대리 갱신 미포함**. Phase 1은 본인만. Phase 2 슬롯 도입 시점에 재검토.
- 2026-05-07: **`isOnboarded` 정의 유지**. mealTimes 미설정도 onboarded. 시간대 설정 강제는 프론트 UX로 유도.
