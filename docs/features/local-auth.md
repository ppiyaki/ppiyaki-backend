---
feature: 로컬 회원가입/로그인
slug: local-auth
status: draft
owner: @goohong
scope: user
related_issues: []
related_prs: []
last_reviewed: 2026-04-10
---

# 로컬 회원가입/로그인

## 1) 개요 (What / Why)
- `loginId` + `password` 기반의 로컬 회원가입/로그인 기능을 구현한다.
- 카카오 OAuth와 병행하여 두 가지 인증 경로를 제공한다.
- 카카오 계정이 없는 사용자(특히 시니어)도 서비스를 이용할 수 있어야 한다.

## 2) 사용자 시나리오

### SC-1: 로컬 회원가입
1. 사용자가 "일반 가입" 화면에서 loginId, password, nickname을 입력한다.
2. `POST /api/v1/auth/signup`으로 가입 요청을 보낸다.
3. 서버가 loginId 중복 확인 → BCrypt 비밀번호 암호화 → users 생성 → JWT 발급.
4. 응답에 `isOnboarded=false` 포함 → 앱이 온보딩 화면으로 이동.

### SC-2: 로컬 로그인
1. 사용자가 loginId, password를 입력한다.
2. `POST /api/v1/auth/login`으로 로그인 요청을 보낸다.
3. 서버가 loginId 조회 → 비밀번호 검증 → JWT 발급.
4. `isOnboarded` 여부에 따라 온보딩 또는 메인 화면으로 이동.

## 3) 요구사항

### 기능 요구사항
- [ ] 회원가입: loginId, password, nickname 필수 입력
- [ ] loginId 중복 시 에러 응답 (409 Conflict)
- [ ] 비밀번호는 BCrypt로 암호화 후 저장
- [ ] 가입 성공 시 JWT(accessToken + refreshToken) 발급 + `isOnboarded=false`
- [ ] 로그인: loginId + password 검증 후 JWT 발급
- [ ] 잘못된 loginId 또는 password 시 401 응답 (어떤 필드가 틀렸는지 구분하지 않음)
- [ ] 응답 형식은 카카오 로그인과 동일 (`LoginResponse`)
- [ ] refresh/logout/me 엔드포인트는 기존 것 그대로 사용

### 비기능 요구사항
- 비밀번호 원문은 로그에 절대 노출 금지
- BCrypt strength: Spring Security 기본값 (10 rounds)

## 4) 범위 / 비범위

### 포함
- `POST /api/v1/auth/signup` — 회원가입
- `POST /api/v1/auth/login` — 로그인
- BCrypt PasswordEncoder 빈 등록
- ErrorCode 추가 (AUTH_DUPLICATE_LOGIN_ID, AUTH_INVALID_CREDENTIALS)
- E2E 테스트

### 제외 (Out of Scope)
- 비밀번호 변경/찾기
- 이메일 인증
- 로컬 계정 ↔ 카카오 계정 연결
- 비밀번호 복잡도 정책 (후속 feature)

## 5) 설계

### 5-1) 도메인 모델
- `users` 테이블 기존 사용. `loginId`, `password` 필드 활용.
- `password`는 nullable (카카오 전용 유저는 NULL).
- 추가 마이그레이션 없음.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/auth/signup | 로컬 회원가입 | 없음 | `SignupRequest` | `LoginResponse` (201) |
| POST | /api/v1/auth/login | 로컬 로그인 | 없음 | `LoginRequest` | `LoginResponse` (200) |

#### DTO 설계

**SignupRequest**
```json
{
  "loginId": String (필수),
  "password": String (필수),
  "nickname": String (필수)
}
```

**LoginRequest**
```json
{
  "loginId": String (필수),
  "password": String (필수)
}
```

**LoginResponse** (기존 재사용)
```json
{
  "accessToken": String,
  "refreshToken": String,
  "isOnboarded": boolean
}
```

### 5-3) 외부 연동
- 없음. Spring Security `BCryptPasswordEncoder` 사용.

### 5-4) 데이터 흐름

```text
[회원가입]
Client → POST /api/v1/auth/signup { loginId, password, nickname }
       → AuthService.signup()
       → loginId 중복 검사 (UserRepository.existsByLoginId)
       → BCrypt 암호화 → User 저장 (role=null)
       → JWT 발급 + RefreshToken 저장
       → LoginResponse { accessToken, refreshToken, isOnboarded=false }

[로그인]
Client → POST /api/v1/auth/login { loginId, password }
       → AuthService.login()
       → UserRepository.findByLoginId(loginId)
       → BCrypt 비밀번호 매칭
       → JWT 발급 + RefreshToken 저장
       → LoginResponse { accessToken, refreshToken, isOnboarded }
```

### 5-5) DB 마이그레이션
- 없음. `users.login_id`(UNIQUE), `users.password`(nullable) 이미 존재.

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: `feat(user)` BCryptPasswordEncoder 빈 + signup/login 엔드포인트 + ErrorCode 추가 + E2E 테스트

## 7) 테스트 전략
- **E2E (RestAssured)**: 
  - 회원가입 성공 → 201 + JWT + isOnboarded=false
  - 중복 loginId 가입 → 409
  - 로그인 성공 → 200 + JWT
  - 잘못된 비밀번호 → 401
  - 존재하지 않는 loginId → 401
- 기존 카카오/refresh/logout/me 테스트 회귀 없음

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| ~~Q1~~ | ~~비밀번호 암호화 방식~~ | ~~BCrypt~~ | **결정됨** → §9 참조 |

## 9) 결정 로그
- 2026-04-10: 초안 작성 (status=draft). 비밀번호 변경/찾기, 이메일 인증은 out-of-scope.
- 2026-04-10: Q1 결정 — BCrypt 사용, Spring Security 기본 strength(10 rounds).
- 2026-04-10: 응답 형식은 카카오 로그인과 동일한 `LoginResponse` 재사용. refresh/logout/me도 기존 그대로.
