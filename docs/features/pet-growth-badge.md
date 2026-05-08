---
feature: 펫 성장 단계 + 뱃지 시스템
slug: pet-growth-badge
status: draft
owner: @qkrehgus02
scope: pet
related_issues: [276]
related_prs: []
last_reviewed: 2026-05-09
---

# 펫 성장 단계 + 뱃지 시스템

## 1) 개요 (What / Why)
- 기존 포인트/레벨 기반 펫 시스템을 연속 복약 일수(streak) 기반 성장 단계 + 뱃지 시스템으로 확장한다.
- 시니어에게 복약 성취감과 동기를 부여하고, 시각적으로 '내 건강이 이만큼 회복되고 있다'를 느낄 수 있게 한다.

## 2) 사용자 시나리오
1. 시니어가 매일 복약을 완료하면 streak(연속 일수)가 증가한다.
2. streak에 따라 삐약이가 알 → 아기 삐약이 → ... → 황제 삐약이로 성장한다.
3. 한번 달성한 성장 단계는 유지되지만, 7일 이상 복약 미인증 시 알 단계로 리셋된다.
4. 특정 조건을 달성하면 뱃지를 획득한다.

## 3) 요구사항
### 기능 요구사항

**성장 단계:**
- [ ] streak(연속 복약 일수)를 추적한다
- [ ] 하루의 모든 복약 스케줄이 TAKEN이면 streak +1 (하나라도 MISSED면 streak 리셋)
- [ ] streak에 따라 성장 단계를 결정한다:

| 단계 | 이름 | 조건 (연속 일수) |
|---|---|---|
| 1 | 알 | 0일 (시작) |
| 2 | 금 간 알 | 3일 |
| 3 | 아기 삐약이 | 7일 |
| 4 | 건강 삐약이 | 14일 |
| 5 | 수호 삐약이 | 30일 |
| 6 | 황제 삐약이 | 100일 |

- [ ] 한번 달성한 단계는 유지 (streak이 줄어도 단계는 내려가지 않음)
- [ ] 단, 7일 이상 복약 미인증 시 알(1단계)로 리셋
- [ ] 펫 조회 API에 stage, streak 포함

**뱃지:**
- [ ] Badge 엔티티 (뱃지 종류, 획득 시각)
- [ ] 뱃지 획득 조건 판정 로직
- [ ] 뱃지 목록:

| 뱃지 | 조건 |
|---|---|
| 천리길도 한 걸음부터 | 첫 복약 완료 |
| 진정한 미라클 모닝 | 7일 연속 아침 약 정시 복용 |
| 가족 연결고리 | 보호자 연동 + 첫 안부 알림 수신 |
| 건강 수호자 | 한 달간 100% 복약 달성 |
| 삐약이 단짝 | AI 음성 대화 5회 이상 |

- [ ] 펫 조회 API에 획득한 뱃지 목록 포함

### 비기능 요구사항
- streak 계산은 서버에서 수행 (클라이언트 의존 금지)
- 기존 point/level 시스템은 유지 (streak과 별도로 공존)

## 4) 범위 / 비범위 (중요)
### 포함
- Pet 엔티티에 streak, highestStage 필드 추가
- PetStage enum (성장 단계)
- streak 증가/리셋 로직
- 7일 미인증 리셋 로직
- Badge 엔티티 + BadgeType enum
- 펫 조회 API 응답 확장
- 단위 테스트 + E2E 테스트

### 제외 (Out of Scope)
- 뱃지별 보상 (알 포인트 등)
- 캐릭터 외형/스킨
- 뱃지 푸시 알림
- 보호자 앱 뱃지 표시

## 5) 설계
### 5-1) 도메인 모델

**PetStage enum:**
```
EGG(0), CRACKED_EGG(3), BABY(7), HEALTHY(14), GUARDIAN(30), EMPEROR(100)
```

**Pet 엔티티 확장:**
- `streak` (int) — 현재 연속 복약 일수
- `highestStage` (PetStage) — 달성한 최고 단계
- `lastTakenDate` (LocalDate) — 마지막 복약 완료일 (리셋 판정용)

**Badge 엔티티 (신규):**
- `id`, `petId`, `badgeType`, `earnedAt`, `createdAt`

**BadgeType enum:**
```
FIRST_STEP, MIRACLE_MORNING, FAMILY_LINK, HEALTH_GUARDIAN, BUDDY
```

### 5-2) API 엔드포인트

| Method | Path | 설명 | 변경 |
|---|---|---|---|
| GET | /api/v1/pets/me | 내 펫 조회 | 응답에 stage, streak, badges 추가 |

### 5-3) streak 로직

**증가:** 복약 성공 이벤트(MedicationTakenEvent) 수신 시, 해당 날짜의 모든 스케줄이 TAKEN인지 확인 → 전부 TAKEN이면 streak +1, lastTakenDate 갱신

**리셋:** 펫 조회 시 `lastTakenDate`와 현재 날짜 차이가 7일 이상이면 streak=0, highestStage=EGG로 리셋

### 5-4) DB 마이그레이션
- `pets` 테이블에 `streak`, `highest_stage`, `last_taken_date` 컬럼 추가
- `badges` 테이블 신설

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: PetStage + streak/성장 단계 로직 + 리셋 + 펫 조회 응답 확장
- [ ] PR 2: Badge 엔티티 + 뱃지 획득 로직 + 뱃지 조회

## 7) 테스트 전략
- Pet 도메인 단위 테스트 (streak 증가, 단계 계산, 리셋)
- PetPointListener 단위 테스트 (streak 연동)
- Badge 서비스 단위 테스트 (뱃지 획득 조건)
- E2E 테스트 (펫 조회 → stage/streak/badges 포함)

## 8) 오픈 질문
없음 (모두 합의됨)

## 9) 결정 로그
- 2026-05-09: 초안 작성. 6단계 성장, 7일 미인증 리셋, 뱃지 5종. 기존 point/level과 공존.
