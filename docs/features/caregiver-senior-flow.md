---
feature: 보호자 기반 시니어 계정 대리 생성 및 코드 로그인
slug: caregiver-senior-flow
status: draft
owner: @qkrehgus02
scope: user
related_issues: [210]
related_prs: []
last_reviewed: 2026-05-05
---

# 보호자 기반 시니어 계정 대리 생성 및 코드 로그인

## 1) 개요 (What / Why)
- 보호자가 회원가입 후 시니어 정보를 입력하여 시니어 계정을 대리 생성하고, 초대 코드를 통해 시니어 기기에서 로그인하는 플로우를 구현한다.
- 시니어(고령자)의 가입 부담을 최소화하고, 보호자가 관리 주체가 되는 서비스 구조를 확립한다.
- 시니어는 코드 입력만으로 로그인되며, refresh token 기반 자동 로그인을 유지한다.

## 2) 사용자 시나리오
1. 보호자가 카카오 또는 로컬 로그인으로 회원가입한다 → role=CAREGIVER 자동 부여.
2. 보호자가 앱에서 "시니어 등록" 화면에 시니어 정보(이름, 생년월일 등)를 입력하고 등록한다 → 시니어 계정 생성 + CareRelation 생성.
3. 보호자가 "초대 코드 발급" 버튼을 누른다 → 6자리 코드(5분 만료) 발급.
4. 시니어 기기에서 코드를 입력한다 → 해당 시니어 계정으로 JWT 발급, 자동 로그인 유지.
5. 시니어는 복약 조회/확인 위주의 제한된 기능만 사용 가능.

## 3) 요구사항
### 기능 요구사항
- [ ] 회원가입(카카오/로컬) 시 role=CAREGIVER 자동 부여
- [ ] 보호자가 시니어 정보를 입력하여 시니어 계정을 대리 생성할 수 있다
- [ ] 시니어 계정 생성 시 CareRelation이 함께 생성된다
- [ ] 보호자는 시니어를 여러 명 등록할 수 있다
- [ ] 보호자가 특정 시니어에 대한 초대 코드를 발급할 수 있다 (6자리 영숫자, 5분 만료)
- [ ] 시니어가 초대 코드를 입력하면 해당 시니어 계정으로 JWT(access + refresh)가 발급된다
- [ ] 시니어는 refresh token 기반 자동 로그인을 유지한다
- [ ] 재로그인이 필요한 경우 보호자가 코드를 재발급한다
- [ ] 시니어 계정은 민감 설정 변경 권한이 없다 (복약 조회/확인 위주)

### 비기능 요구사항
- 시니어 로그인 UX는 코드 입력 한 번으로 완료되어야 한다
- refresh token 만료 기간은 충분히 길게 설정 (30일 권장)

## 4) 범위 / 비범위 (중요)
### 포함
- 회원가입 시 role=CAREGIVER 자동 부여 (기존 signup/kakao login 수정)
- 시니어 대리 생성 API
- 초대 코드 발급 API (기존 로직 활용, seniorId 지정 방식으로 변경)
- 코드 입력 → 시니어 로그인 API
- 시니어 권한 제한 정책
- 단위 테스트 + E2E 테스트

### 제외 (Out of Scope)
- 시니어 단독 회원가입 (보호자 없이 사용하는 케이스)
- 연동 해제 API
- 보호자 계정 삭제 시 시니어 계정 처리
- 시니어 권한별 API 접근 제어 상세 구현 (별도 이슈)

## 5) 설계
### 5-1) 도메인 모델
- `User` 엔티티: 회원가입 시 role=CAREGIVER 세팅. 시니어 계정은 보호자가 생성하므로 password=null, loginId=null 가능.
- `CareRelation` 엔티티: 시니어 대리 생성 시 즉시 생성 (기존 invite 플로우와 달리 seniorId가 확정된 상태)
- 초대 코드: 시니어 로그인 용도. CareRelation.inviteCode/expiresAt 재활용.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/seniors | 보호자가 시니어 계정 대리 생성 | 필수 (CAREGIVER) | `SeniorCreateRequest` | `SeniorCreateResponse` |
| POST | /api/v1/care-relations/invite | 보호자가 특정 시니어의 초대 코드 발급 | 필수 (CAREGIVER) | `InviteCodeRequest` | `InviteCodeResponse` |
| POST | /api/v1/auth/code-login | 시니어가 코드로 로그인 | 불필요 | `CodeLoginRequest` | `LoginResponse` |

### 5-3) 외부 연동
- 없음

### 5-4) 데이터 흐름

**시니어 대리 생성:**
보호자 인증 → 시니어 정보 입력 → User(role=SENIOR, loginId=null, password=null) 생성 → CareRelation(caregiverId, seniorId) 생성 → Pet 자동 생성 → 응답

**초대 코드 발급:**
보호자 인증 → seniorId 지정 → CareRelation 조회/검증 → inviteCode + expiresAt 세팅 → 코드 응답

**코드 로그인:**
시니어 기기에서 코드 입력 → CareRelation에서 inviteCode 조회 → 만료 검증 → 해당 seniorId로 JWT 발급 → 코드 폐기 → LoginResponse 반환

### 5-5) DB 마이그레이션
- 기존 테이블 그대로 사용 (변경 없음)
- `users` 테이블: loginId/password nullable은 이미 허용된 상태

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: 회원가입 시 role=CAREGIVER + 시니어 대리 생성 API + CareRelation/Pet 자동 생성
- [ ] PR 2: 초대 코드 발급 수정 (seniorId 지정) + 코드 로그인 API
- [ ] PR 3: 시니어 권한 제한 정책 (별도 이슈 분리 검토)

## 7) 테스트 전략
- AuthService 단위 테스트 (role 자동 부여)
- 시니어 대리 생성 서비스 단위 테스트
- 코드 로그인 서비스 단위 테스트
- E2E 테스트 (전체 플로우: 가입 → 시니어 생성 → 코드 발급 → 코드 로그인)

## 8) 오픈 질문
없음 (모두 합의됨)

## 9) 결정 로그
- 2026-05-05: 초안 작성. 시니어 혼자 사용 케이스 제외. 보호자 1:N 시니어 등록 가능. 시니어 자동 로그인(refresh token). 기존 CareRelation/inviteCode 구조 재활용.
