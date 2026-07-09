---
feature: 사용자별 타임존 지원
slug: user-timezone
status: draft
owner: @dohyeon
scope: user
related_issues: [464]
related_prs: []
last_reviewed: 2026-07-09
---

# 사용자별 타임존 지원

## 1) 개요 (What / Why)
- 현재 알림 시스템이 `Asia/Seoul`(KST)로 하드코딩되어 있어, 해외 거주 사용자에게 잘못된 시간에 복약 알림이 발송된다.
- User 엔티티에 IANA 타임존 ID(`Asia/Seoul`, `America/New_York` 등)를 저장하고, 알림 스케줄러가 사용자별 현지 시간 기준으로 동작하도록 한다.
- 기존 사용자는 기본값 `Asia/Seoul`로 동작하여 하위 호환을 유지한다.

## 2) 사용자 시나리오
- 시니어(또는 보호자)는 설정 화면에서 자신의 타임존을 선택하여, 복약 알림이 현지 시간 기준으로 오도록 설정한다.
- 미국에 거주하는 시니어가 아침 식사 시간을 08:00으로 설정하면, 뉴욕 시간 08:00에 알림을 받는다 (KST 기준이 아닌 현지 시간 기준).
- 보호자는 연동된 시니어의 타임존을 대신 설정할 수 있다 (직접 앱을 쓰지 않는 시니어 대응).

## 3) 요구사항
### 기능 요구사항
- [ ] User 엔티티에 `timezone` 필드 추가 (IANA 타임존 ID, 기본값 `Asia/Seoul`)
- [ ] 본인 타임존 조회/수정 API 제공 (기존 `PUT /api/v1/users/me` 확장 또는 별도 엔드포인트)
- [ ] 보호자가 연동된 시니어의 타임존을 수정할 수 있다
- [ ] 유효하지 않은 타임존 ID 입력 시 400 응답
- [ ] MedicationReminderScheduler가 사용자별 타임존 기준으로 현재 시간을 비교한다
- [ ] MedicationDelayScheduler가 사용자별 타임존 기준으로 지연 판정한다
- [ ] FamilySafetyScheduler는 절대 시간(마지막 활동 시각) 기반이므로 타임존 영향 없음 확인
- [ ] 선택 가능한 타임존 목록 조회 API 제공 (`GET /api/v1/timezones`)
- [ ] `GET /api/v1/users/me` 응답에 `timezone` 노출

### 비기능 요구사항
- 스케줄러 실행 주기(매 분)는 유지. 사용자 수 증가 시 타임존별 그룹핑으로 쿼리 최적화 고려.
- 기존 사용자 데이터 마이그레이션 불필요 (DB 기본값으로 처리).

## 4) 범위 / 비범위 (중요)
### 포함
- User 엔티티 `timezone` 컬럼 추가 및 도메인 로직
- 타임존 설정/수정 API
- MedicationReminderScheduler, MedicationDelayScheduler 타임존 적용
- `UserMeResponse`에 `timezone` 필드 추가

### 제외 (Out of Scope)
- 클라이언트 자동 타임존 감지 (사용자가 목록에서 직접 선택)
- 글로벌 `Clock` 빈의 UTC 전환 (영향 범위가 넓어 별도 작업으로 분리)
- Jackson/Hibernate 타임존 설정 변경 (DB 저장 기준은 KST 유지, 알림 발송 시점만 사용자별 변환)
- PillIdentificationSyncScheduler 변경 (사용자 무관 배치 작업)

## 5) 설계
### 5-1) 도메인 모델
- `com.ppiyaki.user.domain.User` 엔티티에 `timezone` 필드 추가 (`String`, 기본값 `"Asia/Seoul"`)
- 도메인 메서드: `User.getZoneId()` → `ZoneId.of(timezone)` 반환
- 식사시간(`breakfastTime` 등)은 기존 `LocalTime` 유지 — 사용자 현지 시간 기준으로 해석

### 5-2) API 엔드포인트
| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| GET | /api/v1/timezones | 선택 가능한 타임존 목록 조회 | 불필요 | - | `List<TimezoneResponse>` |
| PUT | /api/v1/users/me | 본인 프로필 수정 (기존 API에 `timezone` 필드 추가) | 필수 | `ProfileUpdateRequest` + `timezone` | `UserMeResponse` |
| PUT | /api/v1/users/{seniorId} | 보호자가 시니어 정보 수정 (기존 API에 `timezone` 추가) | 필수 | `SeniorProfileUpdateRequest` + `timezone` | `UserMeResponse` |

- `TimezoneResponse(id, label, utcOffset)` — 예: `("Asia/Seoul", "서울 (UTC+9)", "+09:00")`
- 목록은 서버가 제공하는 고정 리스트. 사용자가 이 중에서 선택하여 설정한다.

### 5-3) 외부 연동
- 없음

### 5-4) 데이터 흐름 / 시퀀스
```
[매 분 스케줄러 실행]
  → 활성 MedicationSchedule 조회 (User fetch join)
  → 각 사용자별: LocalTime.now(ZoneId.of(user.timezone))으로 현재 현지 시간 계산
  → user.breakfastTime 등과 비교하여 알림 발송 여부 판정
```

### 5-5) DB 마이그레이션
- `users` 테이블에 컬럼 추가:
  ```sql
  ALTER TABLE users ADD COLUMN timezone VARCHAR(40) NOT NULL DEFAULT 'Asia/Seoul';
  ```

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: User 엔티티 timezone 필드 + 설정 API + UserMeResponse 노출 + 테스트
- [ ] PR 2: 스케줄러/디스패처 타임존 적용 + 테스트

## 7) 테스트 전략
- 도메인 단위 테스트: `User.getZoneId()` 정상/잘못된 타임존 케이스
- 서비스 단위 테스트: 스케줄러가 다른 타임존 사용자에게 올바른 시점에 알림 발송하는지 검증 (Clock mock)
- E2E (RestAssured): 타임존 설정 API 성공, 유효하지 않은 타임존 400, 미인증 401

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q1 | 타임존 설정을 기존 프로필 수정 API에 합칠지, 별도 엔드포인트로 뺄지 | (a) 기존 PUT /me 확장 / (b) PUT /me/timezone 신설 | @dohyeon / 2026-07-10 |
| Q2 | 보호자와 시니어의 타임존을 독립 관리할지, 시니어 기준으로 통일할지 | (a) 각자 독립 / (b) 시니어 기준 통일 | @dohyeon / 2026-07-10 |

## 9) 결정 로그
- 2026-07-09: 초안 작성 (status=draft). 타임존 저장 방식은 IANA ID(A안) 채택 — 서머타임 자동 대응을 위해.
