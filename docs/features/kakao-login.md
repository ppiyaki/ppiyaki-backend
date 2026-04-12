---
feature: 카카오 로그인
slug: kakao-login
status: draft
owner: @goohong
scope: user
related_issues: [94]
related_prs: []
last_reviewed: 2026-04-11
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
2. 카카오 SDK가 `openid` scope로 로그인을 수행하고 **ID Token(JWT)** 을 포함한 `OAuthToken`을 반환한다.
3. 앱이 ID Token을 백엔드 `POST /api/v1/auth/kakao`에 전달한다.
4. 백엔드가 카카오 JWKS로 ID Token 서명을 검증하고 `iss`/`aud`/`exp` 클레임을 확인한 뒤, `sub`를 provider user id로 사용해 신규 유저 자동 생성(`users` + `oauth_identities`) → JWT 발급.
5. 응답에 `isOnboarded=false` 포함 → 앱이 온보딩 화면(닉네임/역할 입력)으로 이동.
6. 온보딩 완료 후 메인 화면 진입. (온보딩 API 자체는 별도 feature)

### SC-2: 기존 사용자 카카오 로그인
1. 사용자가 "카카오로 시작하기" 탭 → SDK가 ID Token 반환 → 앱이 ID Token을 백엔드로 전달.
2. 백엔드가 ID Token 검증 후 기존 `oauth_identities` 매칭 → JWT 발급.
3. `isOnboarded=true` → 앱이 메인 화면으로 이동.

### SC-3: 토큰 만료 시 갱신
1. access token 만료 → 앱이 refresh token으로 `POST /api/v1/auth/refresh` 호출.
2. 새 access token + refresh token 발급(rotation).

> 로컬 계정이 있는 사용자의 카카오 연결(선택)은 이번 범위에서 **제외**. 별도 feature로 분리.

## 3) 요구사항

### 기능 요구사항
- [ ] 클라이언트가 카카오 ID Token(OpenID Connect)을 서버로 전달
- [ ] 서버는 카카오 JWKS(`https://kauth.kakao.com/.well-known/jwks.json`)로 RS256 서명을 검증
- [ ] 서버는 ID Token의 `iss`(`https://kauth.kakao.com`), `aud`(카카오 앱 키), `exp` 클레임을 검증
- [ ] 서버는 ID Token의 `sub` 클레임을 provider user id로 사용
- [ ] `oauth_identities`에 `(provider=KAKAO, provider_user_id=sub)` 조회
  - 존재: 해당 `user_id`로 JWT 발급
  - 미존재: 신규 `users` 생성(nickname은 ID Token의 `nickname` 클레임, 기타는 null) 후 `oauth_identities` 생성
- [ ] JWT 응답 형식: `{ accessToken, refreshToken, isOnboarded }`
- [ ] 온보딩 미완료 판별: `users.role IS NULL` → `isOnboarded=false`
- [ ] Refresh 엔드포인트로 access token 재발급
- [ ] 로그아웃 엔드포인트(서버에서 refresh 토큰 무효화)
- [ ] JWKS 공개키는 메모리에 캐시하여 매 로그인마다 네트워크 호출이 발생하지 않도록 함

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
| POST | /api/v1/auth/kakao | Kakao ID Token → 서버 JWT | 없음 | `{ idToken }` | `{ accessToken, refreshToken, isOnboarded }` |
| POST | /api/v1/auth/refresh | Refresh token 재발급 | 없음 | `{ refreshToken }` | `{ accessToken, refreshToken }` |
| POST | /api/v1/auth/logout | 로그아웃 (refresh 무효화) | 필수 | `{ refreshToken }` | 204 |
| GET | /api/v1/users/me | 현재 로그인 유저 정보 | 필수 | - | UserResponse |

### 5-3) 외부 연동 (OpenID Connect 기반)
- **Kakao OpenID Connect**
  - JWKS: `GET https://kauth.kakao.com/.well-known/jwks.json` (공개키 목록)
  - Issuer: `https://kauth.kakao.com` (고정)
  - Audience: 카카오 앱 키(네이티브 SDK 사용 시 네이티브 앱 키)
  - 서명 알고리즘: RS256
  - 필수 클레임: `iss`, `aud`, `sub`, `iat`, `exp`, `auth_time`
- **서버는 카카오 REST API(`/oauth/token`, `/v2/user/me`)를 호출하지 않음** — ID Token 검증만으로 사용자 식별 완료
- REST client: Spring 6 `RestClient` (JWKS 조회에만 사용)
- `common/auth/KakaoIdTokenVerifier` 컴포넌트가 JWKS 조회·캐시·서명 검증·클레임 검증 담당
- JWT 파싱/검증: `io.jsonwebtoken:jjwt-api:0.12.6` (프로젝트 기존 의존성)
- 설정: `application.yml` 또는 환경변수로 `kakao.oidc.issuer`, `kakao.oidc.jwks-uri`, `kakao.oidc.audience`

### 5-4) 데이터 흐름 (프론트엔드 팀 공유용)

```
┌──────┐       ┌──────────┐       ┌──────────┐       ┌──────────┐
│  App │       │ 카카오SDK  │       │ Backend  │       │카카오 서버│
└──┬───┘       └────┬─────┘       └────┬─────┘       └────┬─────┘
   │  1. 로그인 탭    │                  │                    │
   │ (openid scope)  │                  │                    │
   │────────────────>│                  │                    │
   │                 │  2. 카카오 인증    │                    │
   │                 │─────────────────────────────────────>│
   │                 │  3. 인가코드       │                    │
   │                 │<─────────────────────────────────────│
   │                 │  4. 토큰 교환      │                    │
   │                 │    (SDK 내부)      │                    │
   │                 │─────────────────────────────────────>│
   │                 │  5. OAuthToken    │                    │
   │                 │    + idToken      │                    │
   │                 │<─────────────────────────────────────│
   │ 6. OAuthToken    │                  │                    │
   │<─────────────────│                  │                    │
   │                                    │                    │
   │  7. POST /api/v1/auth/kakao        │                    │
   │     { idToken }                    │                    │
   │───────────────────────────────────>│                    │
   │                                    │ 8. JWKS 조회        │
   │                                    │    (최초 1회 캐시)   │
   │                                    │──────────────────>│
   │                                    │   공개키 목록        │
   │                                    │<──────────────────│
   │                                    │                    │
   │                                    │ 9. ID Token 검증    │
   │                                    │    (서명 + iss/aud/ │
   │                                    │     exp 클레임)     │
   │                                    │                    │
   │                                    │ 10. DB 조회/생성     │
   │                                    │   oauth_identities │
   │                                    │   (sub → userId)   │
   │                                    │                    │
   │                                    │ 11. JWT 발급        │
   │                                    │   access + refresh │
   │                                    │                    │
   │  12. 응답                            │                    │
   │  { accessToken,                    │                    │
   │    refreshToken,                   │                    │
   │    isOnboarded }                   │                    │
   │<───────────────────────────────────│                    │
   │                                    │                    │
   │  [isOnboarded=false]               │                    │
   │  → 온보딩 화면 (닉네임/역할 입력)    │                    │
   │  [isOnboarded=true]                │                    │
   │  → 메인 화면                         │                    │
```

> 8번 JWKS 조회는 서버 메모리 캐시에 해당 kid가 없을 때만 발생합니다. 캐시가 있으면 네트워크 호출 0회로 로그인 완료됩니다.

#### 프론트엔드 요약
| 항목 | 내용 |
|---|---|
| 앱이 할 일 | 카카오 SDK 초기화 시 `openid` scope 포함하여 `loginWithKakaoTalk()` / `loginWithKakaoAccount()` 호출. SDK가 반환하는 `OAuthToken.idToken`을 백엔드에 전달. |
| 사전 조건 | 카카오 개발자 콘솔에서 **OpenID Connect 활성화** 필요 ([내 애플리케이션 → 제품 설정 → 카카오 로그인 → OpenID Connect]) |
| 로그인 요청 | `POST /api/v1/auth/kakao` body: `{ "idToken": "카카오가 발급한 JWT" }` |
| 응답 분기 | `isOnboarded=false` → 온보딩 화면, `isOnboarded=true` → 메인 화면 |
| 인증 헤더 | 이후 API 호출 시 `Authorization: Bearer {accessToken}` |
| 토큰 갱신 | 401 응답 시 `POST /api/v1/auth/refresh` body: `{ "refreshToken": "..." }` → 새 토큰 쌍 수신 |
| 로그아웃 | `POST /api/v1/auth/logout` (인증 필수) body: `{ "refreshToken": "..." }` → 204 |
| redirectUri | **프론트 코드에서 전혀 다루지 않음.** 카카오 SDK가 내부적으로 처리. 백엔드도 관리하지 않음. |

### 5-5) DB 마이그레이션
- `oauth_identities` 테이블은 #19에서 이미 생성됨. 추가 마이그레이션 불필요.
- `users.password` nullable 전환 — `#17` 완료 여부 확인 필요 (타깃 스키마 기준이나 코드 갭 있음: 06-domain-model §7-16)

## 6) 작업 분할 (예상 PR 리스트)
- [x] PR 1: `feat(user)` Spring Security 뼈대 + JWT 유틸 + JwtAuthenticationFilter
- [x] PR 2: `feat(user)` `KakaoIdTokenVerifier`(OIDC JWKS 검증) + `/api/v1/auth/kakao` 엔드포인트 + user 조회/생성
- [x] PR 3: `feat(user)` Refresh/Logout + `/api/v1/users/me` + `@AuthenticationPrincipal` ArgumentResolver
- [ ] PR 4: `refactor(user)` `users.password` nullable 전환 (코드 갭 §7-16 일부)

## 7) 테스트 전략
- **단위**: JWT 유틸 서명/검증, `KakaoIdTokenVerifier` 클레임 검증 로직(서명 실패, iss/aud 불일치, sub 누락 등)
- **통합/E2E**: `@SpringBootTest` + RestAssured로 `/api/v1/auth/kakao` end-to-end
  - 카카오 JWKS 엔드포인트는 WireMock으로 스텁
  - 테스트 RSA 키페어를 `@BeforeAll`에서 생성, 공개키는 JWKS 응답에 포함
  - 테스트 ID Token은 테스트 개인키로 직접 서명하여 생성
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
- 2026-04-09: ~~OAuth 플로우는 **하이브리드** 채택 — 앱이 카카오 SDK로 인가코드 획득, 백엔드가 토큰 교환. 순수 서버 redirect는 모바일 앱에 부적합.~~ **2026-04-11 번복됨 (아래 참조)**
- 2026-04-09: Q3 결정 — 신규 가입 시 role=NULL. 온보딩에서 닉네임/역할 입력. `users.role IS NULL`로 온보딩 미완료 판별 (`isOnboarded` 응답 필드).
- 2026-04-09: 기존 `OAuthIdentity`/`OAuthProvider(KAKAO)` 엔티티 재사용 확정.
- 2026-04-09: 프론트엔드 연동 플로우 다이어그램 추가 (§5-4).
- 2026-04-09: Q1 결정 — refresh token은 DB 테이블(`refresh_tokens`)에 저장.
- 2026-04-09: Q2 결정 — JWT 시크릿은 GitHub Secrets + 환경변수로 관리.
- 2026-04-09: Q4 결정 — logout 시 access token 블랙리스트 없음. refresh만 무효화. access token은 만료(30분)까지 유효.
- **2026-04-11: OAuth 플로우 결정 번복 — OpenID Connect(OIDC) ID Token 기반(패턴 C)으로 전환.**
  - **사유 1**: 카카오 REST API 공식 문서가 "PC 및 모바일 웹에서 사용하기 적합한 방식"이라 명시 — 네이티브 앱에는 권장 아님. 2026-04-09 '하이브리드' 결정은 이 공식 권장을 확인하지 않고 내려진 결정이었음.
  - **사유 2**: 카카오 iOS/Android SDK의 기본 플로우인 `loginWithKakaoTalk()` / `loginWithKakaoAccount()`는 인가코드를 SDK 내부에서 소비하고 **OAuthToken을 직접 반환**함. "인가코드만 받는" 별도 API는 공식 문서 본문에 제시되지 않음.
  - **사유 3**: 카카오는 OpenID Connect를 공식 지원하며, **백엔드 인증 프로토콜을 명시적으로 문서화한 유일한 패턴**이 OIDC ID Token 검증임.
  - **효과**: `client_secret`, `redirect_uri`, `/oauth/token`, `/v2/user/me` 관리 부담 완전 제거. JWKS 초기 캐시 후 매 로그인 시 네트워크 호출 0회.
  - **전제**: 카카오 개발자 콘솔에서 OpenID Connect를 명시적으로 활성화해야 함 (사용자 액션).
  - **참조**: RFC 7519 (JWT), RFC 7517 (JWK), [Kakao OIDC 지원 공지](https://devtalk.kakao.com/t/openid-connect-notice-support-of-openid-connect/121888).
