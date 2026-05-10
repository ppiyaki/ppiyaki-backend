---
feature: 보호자 온보딩 API
slug: onboarding
status: draft
owner: @qkrehgus02
scope: user
related_issues: [248]
related_prs: []
last_reviewed: 2026-05-10
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
4. 각 시니어에 대해 케어 모드(`AUTONOMOUS` = 기본 건강 알림 모드 / `MANAGED` = 집중 안심 모드)를 선택한다. 화면에 "세부 알림은 가입 후 내 정보 > 알림 설정에서 변경할 수 있어요" 안내. UI 모음집상 라벨은 "시니어 별 알림설정"이지만, 백엔드는 careMode를 상위 개념으로 받아 알림 프리셋 + 처방전 권한 + 향후 sub-속성(복약인증 강제, 여유 등)을 일괄 결정.
5. 완료 버튼을 누르면 온보딩이 끝나고 메인 화면으로 이동한다.

## 3) 요구사항
### 기능 요구사항
- [x] 보호자 닉네임을 변경할 수 있다
- [x] 시니어를 1명 이상 등록할 수 있다 (이름, 성별 필수)
- [x] 성별은 MALE / FEMALE / UNKNOWN(비공개) 중 선택
- [x] 시니어 등록 시 User(SENIOR) + CareRelation + Pet이 자동 생성된다
- [x] 각 시니어마다 careMode(`AUTONOMOUS` / `MANAGED`)를 선택할 수 있다 (화면 라벨: "기본 건강 알림 모드" / "집중 안심 모드")
- [x] 시니어 등록 시 careMode가 `User.careMode`에 저장되고 매핑된 프리셋으로 `notification_settings` row 자동 생성: `AUTONOMOUS` → STANDARD preset / `MANAGED` → INTENSIVE preset
- [x] 온보딩은 단일 API(`POST /api/v1/onboarding`)로 처리된다

### 비기능 요구사항
- 시니어 등록 실패 시 전체 롤백 (트랜잭션 보장)

## 4) 범위 / 비범위 (중요)
### 포함
- `POST /api/v1/onboarding` API
- `User.createSenior(nickname, gender)` 팩토리
- 시니어 생성 시 선택된 careMode 매핑 프리셋으로 `notification_settings` row 자동 생성 (AUTONOMOUS → STANDARD / MANAGED → INTENSIVE)
- 단위 테스트 + E2E 테스트

### 제외 (Out of Scope)
- 알림 모드에 따른 실제 알림 발송 로직 (`docs/features/medication-notification.md` 책임)
- 알림 설정 개별 조정 API (`docs/features/medication-notification.md` 책임)
- 온보딩 완료 여부 추적 (isOnboarded 플래그)

## 5) 설계
### 5-1) 도메인 모델
- `User.createSenior(nickname, gender)` 팩토리
- `NotificationSettings` 엔티티(`com.ppiyaki.notification`)에 caregiver ↔ senior 1:1 row 자동 생성 (careMode 매핑 프리셋)
- 입력 enum은 `CareMode` 단일 (상위 개념). `NotificationMode` enum은 폐기 (#294)
- ~~`User.notificationMode` 필드~~, ~~`com.ppiyaki.user.NotificationMode`~~ — 2026-05-10 refactor로 제거 (#283)

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
      "careMode": "AUTONOMOUS"
    },
    {
      "nickname": "할아버지",
      "gender": "MALE",
      "careMode": "MANAGED"
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
   - User(role=SENIOR, gender) 생성
   - 받은 `careMode`를 `User.careMode`에 직접 저장
   - CareRelation 생성
   - Pet 생성 + User에 연결
   - `notification_settings` row 생성 (caregiver_id × senior_id, careMode 매핑 프리셋 — AUTONOMOUS → STANDARD / MANAGED → INTENSIVE)
4. 전체 결과 응답

### 5-5) DB 마이그레이션
- ~~`users.notification_mode` 컬럼~~ — 2026-05-10 DROP (알림 모델이 N:M `notification_settings`로 이전)

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: NotificationMode + 온보딩 API + 테스트

## 7) 테스트 전략
- OnboardingService 단위 테스트 (닉네임 변경 + 시니어 N명 생성)
- E2E 테스트 (전체 온보딩 플로우)

## 8) 오픈 질문
없음 (모두 합의됨)

## 9) 결정 로그
- 2026-05-08: 초안 작성. 단일 API, 알림 모드는 프리셋(나중에 개별 조정 가능), 성별 비공개=UNKNOWN.
- 2026-05-10: **알림 모드 책임 이전** — `docs/features/medication-notification.md` spec(보호자 ↔ 시니어 N:M `notification_settings` 모델 채택)에 따라 `users.notification_mode` 컬럼 + `User.notificationMode` 필드 제거. enum은 `com.ppiyaki.user` → `com.ppiyaki.notification`으로 이전 + `BASIC_ALERT`/`INTENSIVE_CARE` → `STANDARD`/`INTENSIVE`/`CUSTOM`로 리네임. 시니어 생성 시 default `STANDARD` 프리셋의 `notification_settings` row 자동 생성. 호환성 깨짐 — 프론트엔드 cut-over 필요.
- 2026-05-10 (#286): **알림 모드 입력 다시 받음 + careMode 매핑** — 디자인 화면("시니어를 어떻게 돌볼까요?") 확인 후 정정. 디자인 의도는 보호자가 시니어별로 STANDARD vs INTENSIVE 명시 선택. `OnboardingRequest.SeniorEntry.notificationMode` 필드 다시 추가 (`STANDARD` / `INTENSIVE`, required). 받은 모드에 따라 ① `notification_settings` 프리셋 적용 (`STANDARD` → standard preset / `INTENSIVE` → intensive preset), ② `senior.careMode` 매핑 (`STANDARD` → `AUTONOMOUS` / `INTENSIVE` → `MANAGED`). 모델은 N:M `notification_settings` 그대로 유지.
- 2026-05-10 (#294): **입력을 careMode로 통합** — UI 모음집("복약인증 강제 / 여유 설정 추가 필요") 확인 결과 onboarding 모드 선택은 알림에 한정되지 않고 careMode 수준의 통합 프리셋(처방전 권한 + 알림 + 향후 복약인증 강제/여유 등). 개념 크기상 careMode가 상위. `OnboardingRequest.SeniorEntry.notificationMode` → `careMode` (`MANAGED`/`AUTONOMOUS`)로 변경. 백엔드는 careMode → 알림 프리셋 자동 매핑(`MANAGED` → INTENSIVE preset / `AUTONOMOUS` → STANDARD preset). `NotificationMode` enum 폐기. `POST /notification-settings/preset` body도 `careMode`로 통일. 디자인 라벨 "기본/집중"은 frontend가 careMode 값으로 매핑. 호환성 깨짐 — 프론트엔드 cut-over.
