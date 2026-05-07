---
feature: 보호자 온보딩 API
slug: onboarding
status: draft
owner: @qkrehgus02
scope: user
related_issues: [248]
related_prs: []
last_reviewed: 2026-05-08
---

# 보호자 온보딩 API

## 1) 개요 (What / Why)
- 보호자 회원가입 후 온보딩 플로우(닉네임 입력 → 시니어 등록 → 알림 모드 선택)를 단일 API로 처리한다.
- 프론트엔드 온보딩 화면과 1:1 매핑되는 API를 제공하여 클라이언트 호출을 최소화한다.
- 현재 시니어 등록(`POST /api/v1/seniors`)은 존재하지만, 닉네임 변경 + 시니어 N명 + 알림 모드를 한 번에 처리하는 API가 없다.

## 2) 사용자 시나리오
1. 보호자가 카카오 또는 로컬 로그인으로 회원가입한다.
2. 온보딩 화면에서 닉네임을 입력한다.
3. 관리할 시니어의 이름과 성별(남/여/비공개)을 입력한다. 여러 명 추가 가능.
4. 각 시니어에 대한 돌봄 모드를 선택한다.
   - 기본 건강 알림: 복약 확인 알림 + 일간/월간 리포트
   - 집중 안심: 복약 완료 즉시 알림 + 30분 지연 경고 + 일간/주간/월간 리포트
5. 완료 버튼을 누르면 온보딩이 끝나고 메인 화면으로 이동한다.

## 3) 요구사항
### 기능 요구사항
- [ ] 보호자 닉네임을 변경할 수 있다
- [ ] 시니어를 1명 이상 등록할 수 있다 (이름, 성별 필수)
- [ ] 성별은 MALE / FEMALE / UNKNOWN(비공개) 중 선택
- [ ] 각 시니어마다 알림 모드(BASIC_ALERT / INTENSIVE_CARE)를 선택할 수 있다
- [ ] 시니어 등록 시 User(SENIOR) + CareRelation + Pet이 자동 생성된다
- [ ] 알림 모드는 시니어 엔티티에 저장된다 (이후 알림 설정에서 개별 조정 가능)
- [ ] 온보딩은 단일 API(`POST /api/v1/onboarding`)로 처리된다

### 비기능 요구사항
- 시니어 등록 실패 시 전체 롤백 (트랜잭션 보장)

## 4) 범위 / 비범위 (중요)
### 포함
- `POST /api/v1/onboarding` API
- `NotificationMode` enum 신설
- User 엔티티에 `notificationMode` 필드 추가
- `User.createSenior()` 팩토리에 gender, notificationMode 반영
- 단위 테스트 + E2E 테스트

### 제외 (Out of Scope)
- 알림 모드에 따른 실제 알림 발송 로직 (알림 기능 구현 시 연동)
- 알림 설정 개별 조정 API
- 온보딩 완료 여부 추적 (isOnboarded 플래그)

## 5) 설계
### 5-1) 도메인 모델
- `NotificationMode` enum: `BASIC_ALERT` / `INTENSIVE_CARE`
- `User` 엔티티에 `notificationMode` 필드 추가 (시니어에만 적용)
- `User.createSenior()` 팩토리에 gender, notificationMode 파라미터 추가

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | /api/v1/onboarding | 보호자 온보딩 완료 | 필수 (CAREGIVER) | `OnboardingRequest` | `OnboardingResponse` |

### 5-3) 요청/응답 예시

**Request:**
```json
{
  "nickname": "보호자닉네임",
  "seniors": [
    {
      "nickname": "할머니",
      "gender": "FEMALE",
      "notificationMode": "BASIC_ALERT"
    },
    {
      "nickname": "할아버지",
      "gender": "MALE",
      "notificationMode": "INTENSIVE_CARE"
    }
  ]
}
```

**Response (201):**
```json
{
  "caregiverNickname": "보호자닉네임",
  "seniors": [
    {
      "seniorId": 2,
      "nickname": "할머니",
      "petId": 1
    },
    {
      "seniorId": 3,
      "nickname": "할아버지",
      "petId": 2
    }
  ]
}
```

### 5-4) 데이터 흐름
1. 보호자 인증 확인 (CAREGIVER role)
2. 보호자 닉네임 업데이트
3. 각 시니어에 대해:
   - User(role=SENIOR, gender, notificationMode) 생성
   - CareRelation 생성
   - Pet 생성 + User에 연결
4. 전체 결과 응답

### 5-5) DB 마이그레이션
- `users` 테이블에 `notification_mode` enum 컬럼 추가 (nullable, 시니어에만 사용)

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: NotificationMode + 온보딩 API + 테스트

## 7) 테스트 전략
- OnboardingService 단위 테스트 (닉네임 변경 + 시니어 N명 생성)
- E2E 테스트 (전체 온보딩 플로우)

## 8) 오픈 질문
없음 (모두 합의됨)

## 9) 결정 로그
- 2026-05-08: 초안 작성. 단일 API, 알림 모드는 프리셋(나중에 개별 조정 가능), 성별 비공개=UNKNOWN.
