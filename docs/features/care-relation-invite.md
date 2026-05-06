---
feature: 보호자 연동 초대 코드
slug: care-relation-invite
status: approved
owner: @qkrehgus02
scope: user
related_issues: [170]
related_prs: []
last_reviewed: 2026-05-02
---

# 보호자 연동 초대 코드

## 1) 개요 (What / Why)
- 보호자가 1회용 초대 코드를 발급하고, 시니어가 해당 코드를 입력하여 보호자-시니어 연동(CareRelation)을 생성하는 기능.
- 현재 CareRelation 엔티티/테이블은 존재하나 생성 API가 없어 연동을 맺을 수 없다.

## 2) 사용자 시나리오
1. 보호자가 앱에서 "초대 코드 발급" 버튼을 누르면 6자리 영숫자 코드가 생성된다.
2. 보호자가 시니어에게 코드를 구두/메시지로 전달한다.
3. 시니어가 앱에서 코드를 입력하면 연동이 완료되고, 코드는 즉시 폐기된다.

## 3) 요구사항
### 기능 요구사항
- [ ] 보호자가 초대 코드를 발급할 수 있다 (6자리 영숫자, 5분 만료, 1회용)
- [ ] 시니어가 초대 코드를 입력하여 연동을 생성할 수 있다
- [ ] 연동 생성 시 초대 코드는 즉시 폐기된다
- [ ] 이미 활성 연동이 존재하는 보호자-시니어 쌍은 중복 연동 에러를 반환한다
- [ ] 만료된 코드로 연동 시도 시 에러를 반환한다
- [ ] 보호자만 코드를 발급할 수 있고, 시니어만 코드를 수락할 수 있다

### 비기능 요구사항
- 초대 코드는 충돌 확률이 낮아야 한다 (6자리 영숫자 = 2,176,782,336 조합)

## 4) 범위 / 비범위 (중요)
### 포함
- 초대 코드 발급 API
- 초대 코드 수락 API
- CareRelationService, CareRelationController, DTO 신설
- 서비스 단위 테스트 + E2E 테스트
- 도메인 문서 갱신

### 제외 (Out of Scope)
- 연동 해제(soft delete) API
- 연동 목록 조회 API
- 초대 코드 재발급 정책 (기존 미사용 코드 자동 폐기 등)

## 5) 설계
### 5-1) 도메인 모델
- 초대 코드는 별도 `InviteCode` 엔티티로 관리 (invite_codes 테이블)
- 코드는 BCrypt hash로 저장 (평문 저장 안 함)
- 보호자가 seniorId를 지정하여 초대 코드 발급
- 코드 사용 시 `usedAt`에 시각 기록

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/care-relations/invite | 보호자가 초대 코드 발급 | 필수 (CAREGIVER) | `InviteCodeRequest` | `InviteCodeResponse` |
| POST | /api/v1/auth/code-login | 시니어가 초대 코드로 로그인 | 불필요 | `CodeLoginRequest` | `LoginResponse` |

### 5-3) 외부 연동
- 없음

### 5-4) 데이터 흐름
1. **발급**: 보호자 인증 확인 → seniorId 지정 → CareRelation 존재 검증 → InviteCode 생성(code_hash, expiresAt) 저장 → 평문 코드 응답
2. **코드 로그인**: 시니어 기기에서 코드 입력 → IP Rate Limit 확인 → 미사용 InviteCode 순회하며 BCrypt 매칭 → 만료 검증 → usedAt 기록 → 해당 seniorId로 JWT 발급 → LoginResponse 반환

### 5-5) DB 마이그레이션
- `invite_codes` 테이블 신설 (senior_id, code_hash, expires_at, used_at, created_at)
- `care_relations`의 invite_code/expires_at 컬럼은 레거시 (추후 제거)

### 비기능 요구사항 (추가)
- IP 기준 brute force 방어: 1분 10회 실패 시 429 반환

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: 초대 코드 발급 + 수락 API 전체 (단일 PR)

## 7) 테스트 전략
- CareRelationService 단위 테스트 (Mockito)
- E2E 테스트 (RestAssured) — 발급 성공, 수락 성공

## 8) 오픈 질문
없음 (모두 합의됨)

## 9) 결정 로그
- 2026-04-17: 초안 작성 및 합의 완료 (status=approved). 코드 형식 6자리 영숫자, 만료 5분, 1회용 폐기 방식 확정.
- 2026-05-02: 초대 방향 변경. 보호자가 코드 발급 → 시니어가 수락으로 수정.
