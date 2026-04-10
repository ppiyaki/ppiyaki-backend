---
feature: 카카오 로그인
slug: kakao-login
status: draft
owner: @goohong
scope: user
related_issues: []
related_prs: []
last_reviewed: 2026-04-09
---

> **프론트엔드 팀 전달 사항은 §5-4 참조**
---

# 카카오 로그인

## 1) 개요 (What / Why)
- 시니어/보호자 모두 카카오 계정을 이용해 가입·로그인할 수 있도록 한다.
- 기본 로컬 로그인(`users.login_id` + `password`)은 유지하되, 카카오 로그인을 보조 인증 경로로 추가한다.
- 대상 사용자: 신규 가입자(주로 보호자)와 기존 사용자 모두.
- 이 기능이 없으면 Medicine/Prescription 등 소유자 기반 기능이 실사용자 흐름으로 검증되지 않는다.

## 2) 사용자 시나리오

### SC-1: 신규 사용자 카카오 가입 + 온보딩
1. 사용자가 앱에서 "카카오로 시작하기"를 탭한다.
2. 카카오 SDK가 인가코드를 발급한다.
3. 앱이 인가코드를 백엔드 `POST /api/v1/auth/kakao`에 전달한다.
4. 백엔드가 카카오 서버와 토큰 교환 → 사용자 정보 조회 → 신규 유저 자동 생성(`users` + `oauth_identities`) → JWT 발급.
5. 응답에 `isOnboarded=false` 포함 → 앱이 온보딩 화면(닉네임/역할 입력)으로 이동.
6. 온보딩 완료 후 메인 화면 진입. (온보딩 API 자체는 별도 feature)

### SC-2: 기존 사용자 카카오 로그인
1. 사용자가 "카카오로 시작하기" 탭 → 인가코드 → 백엔드 전달.
2. 백엔드가 기존 `oauth_identities` 매칭 → JWT 발급.
3. `isOnboarded=true` → 앱이 메인 화면으로 이동.

### SC-3: 토큰 만료 시 갱신
1. access token 만료 → 앱이 refresh token으로 `POST /api/v1/auth/refresh` 호출.
2. 새 access token + refresh token 발급(rotation).

> 로컬 계정이 있는 사용자의 카카오 연결(선택)은 이번 범위에서 **제외**. 별도 feature로 분리.

## 3) 요구사항

### 기능 요구사항
- [ ] 클라이언트가 카카오 Authorization Code를 서버로 전달
- [ ] 서버는 카카오 토큰 엔드포인트로 access token 교환
- [ ] 서버는 카카오 /v2/user/me로 사용자 식별자(id) 조회
- [ ] `oauth_identities`에 `(provider=KAKAO, provider_user_id)` 조회
  - 존재: 해당 `user_id`로 JWT 발급
  - 미존재: 신규 `users` 생성(nickname은 카카오 닉네임, 기타는 null) 후 `oauth_identities` 생성
- [ ] JWT 응답 형식: `{ accessToken, refreshToken, isOnboarded }`
- [ ] 온보딩 미완료 판별: `users.role IS NULL` → `isOnboarded=false`
- [ ] Refresh 엔드포인트로 access token 재발급
- [ ] 로그아웃 엔드포인트(서버에서 refresh 토큰 무효화)

### 비기능 요구사항
- 인증 실패 시 사용자에게 노출되는 정보는 최소화(카카오 오류 원문은 마스킹)
- JWT 서명 비밀키는 환경변수로만 주입(시크릿 저장소 사용)
- 카카오 client_secret은 GitHub Secrets 또는 NCP 시크릿 매니저 사용
- 액세스 토큰 만료: 30분 / 리프레시 토큰 만료: 14일 (초안)

## 4) 범위 / 비범위

### 포함
- 카카오 Authorization Code → 서버 JWT 발급
- 신규 가입 시 최소 필드로 `users` + `oauth_identities` 생성
- JWT 기반 `@AuthenticationPrincipal` 리졸버
- `SecurityFilterChain` 구성(permitAll/authenticated 경로 분리)
- Refresh / Logout 엔드포인트

### 제외 (Out of Scope)
- 로컬 로그인 UI/엔드포인트 구현 (spec은 살아있되 이번 PR에서는 제외)
- 카카오 외 IdP (Apple/Google 등)
- 카카오 계정 ↔ 기존 로컬 계정 연결
- 프로필 이미지/친구 목록 등 카카오 추가 스코프 조회
- 역할(SENIOR/CAREGIVER) 선택 UI — 온보딩 프로필 설정 feature에서 처리

## 5) 설계

### 5-1) 도메인 모델
- `users` (기존), `oauth_identities` (신규 — #19 완료)
- `users.password` nullable 타깃 유지. OAuth 전용 유저는 password NULL.
- `docs/ai-harness/06-domain-model.md §5 users, oauth_identities` 참조

### 5-2) API 엔드포인트
| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/auth/kakao | Authorization Code → 서버 JWT | 없음 | `{ code, redirectUri }` | `{ accessToken, refreshToken, isOnboarded }` |
| POST | /api/v1/auth/refresh | Refresh token 재발급 | 없음 | `{ refreshToken }` | `{ accessToken, refreshToken }` |
| POST | /api/v1/auth/logout | 로그아웃 (refresh 무효화) | 필수 | `{ refreshToken }` | 204 |
| GET | /api/v1/users/me | 현재 로그인 유저 정보 | 필수 | - | UserResponse |

### 5-3) 외부 연동
- **Kakao OAuth API**
  - Token: `POST https://kauth.kakao.com/oauth/token`
  - User Info: `GET https://kapi.kakao.com/v2/user/me`
- REST client: Spring 6 `RestClient` (또는 WebClient). 동기 호출 OK.
- `infra/auth/KakaoOAuthClient` 어댑터 분리
- 설정: `application-local.yml`/`application.yml` 또는 환경변수로 `kakao.client-id`, `kakao.client-secret`, `kakao.redirect-uri`

### 5-4) 데이터 흐름 (프론트엔드 팀 공유용)

```
┌──────┐       ┌──────────┐       ┌──────────┐       ┌──────────┐
│  App │       │ 카카오SDK  │       │ Backend  │       │카카오 서버│
└──┬───┘       └────┬─────┘       └────┬─────┘       └────┬─────┘
   │  1. 로그인 탭   │                  │                   │
   │───────────────>│                  │                   │
   │                │  2. 카카오 인증   │                   │
   │                │─────────────────────────────────────>│
   │                │  3. 인가코드 반환  │                   │
   │                │<─────────────────────────────────────│
   │ 4. 인가코드     │                  │                   │
   │<───────────────│                  │                   │
   │                                   │                   │
   │  5. POST /api/v1/auth/kakao       │                   │
   │     { code, redirectUri }         │                   │
   │──────────────────────────────────>│                   │
   │                                   │ 6. 토큰 교환       │
   │                                   │  POST /oauth/token│
   │                                   │─────────────────>│
   │                                   │  access_token     │
   │                                   │<─────────────────│
   │                                   │ 7. 사용자 정보     │
   │                                   │  GET /v2/user/me  │
   │                                   │─────────────────>│
   │                                   │  kakao_user_id    │
   │                                   │<─────────────────│
   │                                   │                   │
   │                                   │ 8. DB 조회/생성    │
   │                                   │  oauth_identities │
   │                                   │  → User 매칭/생성  │
   │                                   │                   │
   │                                   │ 9. JWT 발급       │
   │                                   │  access + refresh │
   │                                   │                   │
   │  10. 응답                          │                   │
   │  { accessToken, refreshToken,     │                   │
   │    isOnboarded }                  │                   │
   │<──────────────────────────────────│                   │
   │                                   │                   │
   │  [isOnboarded=false]              │                   │
   │  → 온보딩 화면 (닉네임/역할 입력)  │                   │
   │  [isOnboarded=true]               │                   │
   │  → 메인 화면                      │                   │
```

#### 프론트엔드 요약
| 항목 | 내용 |
|---|---|
| 앱이 할 일 | 카카오 SDK로 **인가코드(authorization code)** 만 받아서 백엔드에 전달. access token 교환은 백엔드가 처리. |
| 로그인 요청 | `POST /api/v1/auth/kakao` body: `{ "code": "인가코드", "redirectUri": "카카오에 등록한 redirect URI" }` |
| 응답 분기 | `isOnboarded=false` → 온보딩 화면, `isOnboarded=true` → 메인 화면 |
| 인증 헤더 | 이후 API 호출 시 `Authorization: Bearer {accessToken}` |
| 토큰 갱신 | 401 응답 시 `POST /api/v1/auth/refresh` body: `{ "refreshToken": "..." }` → 새 토큰 쌍 수신 |
| 로그아웃 | `POST /api/v1/auth/logout` (인증 필수) body: `{ "refreshToken": "..." }` → 204 |

### 5-5) DB 마이그레이션
- `oauth_identities` 테이블은 #19에서 이미 생성됨. 추가 마이그레이션 불필요.
- `users.password` nullable 전환 — `#17` 완료 여부 확인 필요 (타깃 스키마 기준이나 코드 갭 있음: 06-domain-model §7-16)

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: `feat(user)` Spring Security 뼈대 + JWT 유틸 + JwtAuthenticationFilter
- [ ] PR 2: `feat(user)` KakaoOAuthClient 어댑터 + `/api/v1/auth/kakao` 엔드포인트 + user 조회/생성
- [ ] PR 3: `feat(user)` Refresh/Logout + `/api/v1/users/me` + `@AuthenticationPrincipal` ArgumentResolver
- [ ] PR 4: `refactor(user)` `users.password` nullable 전환 (코드 갭 §7-16 일부)

## 7) 테스트 전략
- **단위**: JWT 유틸 서명/검증, KakaoOAuthClient 응답 매핑(모킹)
- **통합**: `@SpringBootTest` + MockMvc로 `/api/v1/auth/kakao` end-to-end (카카오 API는 WireMock 또는 인터페이스 목)
- **인증 필터**: 유효/무효 JWT, 만료 케이스

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| ~~Q1~~ | ~~refresh token 저장 위치~~ | ~~(a) DB 테이블~~ | **결정됨** → §9 참조 |
| ~~Q2~~ | ~~JWT 시크릿 관리~~ | ~~(a) GitHub Secrets + env~~ | **결정됨** → §9 참조 |
| ~~Q3~~ | ~~카카오 신규 가입 시 default role~~ | ~~(b) null 후 온보딩에서 선택~~ | **결정됨** → §9 참조 |
| ~~Q4~~ | ~~logout 시 access token 블랙리스트~~ | ~~(a) 없음(refresh만 무효화)~~ | **결정됨** → §9 참조 |
| Q5 | 에러 응답 포맷 | `docs/features/` 다른 spec과 공유할 공통 ErrorCode | 프론트팀 합의 필요 |

## 9) 결정 로그
- 2026-04-09: 초안 작성 (status=draft). 카카오 외 IdP, 로컬 계정 연결은 out-of-scope.
- 2026-04-09: Refresh/Logout 포함 범위 확정. 역할 선택 UI는 온보딩 feature로 분리.
- 2026-04-09: OAuth 플로우는 **하이브리드** 채택 — 앱이 카카오 SDK로 인가코드 획득, 백엔드가 토큰 교환. 순수 서버 redirect는 모바일 앱에 부적합.
- 2026-04-09: Q3 결정 — 신규 가입 시 role=NULL. 온보딩에서 닉네임/역할 입력. `users.role IS NULL`로 온보딩 미완료 판별 (`isOnboarded` 응답 필드).
- 2026-04-09: 기존 `OAuthIdentity`/`OAuthProvider(KAKAO)` 엔티티 재사용 확정.
- 2026-04-09: 프론트엔드 연동 플로우 다이어그램 추가 (§5-4).
- 2026-04-09: Q1 결정 — refresh token은 DB 테이블(`refresh_tokens`)에 저장.
- 2026-04-09: Q2 결정 — JWT 시크릿은 GitHub Secrets + 환경변수로 관리.
- 2026-04-09: Q4 결정 — logout 시 access token 블랙리스트 없음. refresh만 무효화. access token은 만료(30분)까지 유효.
