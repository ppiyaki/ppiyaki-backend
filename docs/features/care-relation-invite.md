---
feature: 보호자 연동 초대 코드
slug: care-relation-invite
status: approved
owner: @qkrehgus02
scope: user
related_issues: [170]
related_prs: []
last_reviewed: 2026-04-17
---

# 보호자 연동 초대 코드

## 1) 개요 (What / Why)
- 시니어가 1회용 초대 코드를 발급하고, 보호자가 해당 코드를 입력하여 보호자-시니어 연동(CareRelation)을 생성하는 기능.
- 현재 CareRelation 엔티티/테이블은 존재하나 생성 API가 없어 연동을 맺을 수 없다.

## 2) 사용자 시나리오
1. 시니어가 앱에서 "초대 코드 발급" 버튼을 누르면 6자리 영숫자 코드가 생성된다.
2. 시니어가 보호자에게 코드를 구두/메시지로 전달한다.
3. 보호자가 앱에서 코드를 입력하면 연동이 완료되고, 코드는 즉시 폐기된다.

## 3) 요구사항
### 기능 요구사항
- [ ] 시니어가 초대 코드를 발급할 수 있다 (6자리 영숫자, 5분 만료, 1회용)
- [ ] 보호자가 초대 코드를 입력하여 연동을 생성할 수 있다
- [ ] 연동 생성 시 초대 코드는 즉시 폐기된다
- [ ] 이미 활성 연동이 존재하는 보호자-시니어 쌍은 중복 연동 에러를 반환한다
- [ ] 만료된 코드로 연동 시도 시 에러를 반환한다
- [ ] 시니어만 코드를 발급할 수 있고, 보호자만 코드를 수락할 수 있다

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
- `CareRelation` 엔티티에 `expiresAt` 필드 추가 (초대 코드 만료 시각)
- `caregiverId`를 nullable로 변경 (코드 발급 시점에는 보호자 미정)
- 연동 수락 시 `caregiverId`를 채우고 `inviteCode`/`expiresAt`를 null 처리(폐기)

### 5-2) API 엔드포인트
| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/care-relations/invite | 시니어가 초대 코드 발급 | 필수 (SENIOR) | - | `InviteCodeResponse` |
| POST | /api/v1/care-relations/accept | 보호자가 초대 코드로 연동 | 필수 (CAREGIVER) | `AcceptInviteRequest` | `AcceptInviteResponse` |

### 5-3) 외부 연동
- 없음

### 5-4) 데이터 흐름
1. **발급**: 시니어 인증 확인 → 6자리 코드 생성 → CareRelation(seniorId, inviteCode, expiresAt) 저장 → 코드 응답
2. **수락**: 보호자 인증 확인 → inviteCode로 CareRelation 조회 → 만료/중복 검증 → caregiverId 세팅, 코드 폐기 → 연동 완료 응답

### 5-5) DB 마이그레이션
- `care_relations` 테이블에 `expires_at datetime(6)` 컬럼 추가

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: 초대 코드 발급 + 수락 API 전체 (단일 PR)

## 7) 테스트 전략
- CareRelationService 단위 테스트 (Mockito)
- E2E 테스트 (RestAssured) — 발급 성공, 수락 성공

## 8) 오픈 질문
없음 (모두 합의됨)

## 9) 결정 로그
- 2026-04-17: 초안 작성 및 합의 완료 (status=approved). 코드 형식 6자리 영숫자, 만료 5분, 1회용 폐기 방식 확정.
