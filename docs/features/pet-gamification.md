---
feature: 펫(삐약이) 게이미피케이션 시스템
slug: pet-gamification
status: draft
owner: @qkrehgus02
scope: pet
related_issues: [206]
related_prs: []
last_reviewed: 2026-05-02
---

# 펫(삐약이) 게이미피케이션 시스템

## 1) 개요 (What / Why)
- 시니어가 복약을 성공할 때마다 삐약이 캐릭터에 경험치(포인트)가 쌓이고 레벨이 성장하는 게이미피케이션 기능.
- 규칙적인 복약을 유도하고, 시니어에게 성취감을 제공한다.
- 현재 Pet 엔티티는 `point` 필드만 존재하며, CRUD API/레벨 계산/이벤트 연동이 모두 미구현 상태.

## 2) 사용자 시나리오
1. 시니어가 회원가입(온보딩) 시 삐약이가 자동으로 생성된다 (point=0, level=0).
2. 시니어가 복약을 완료(TAKEN)하면 포인트가 증가하고 레벨이 오를 수 있다.
3. 시니어(또는 보호자)가 앱에서 삐약이의 현재 레벨/포인트를 조회한다.

## 3) 요구사항
### 기능 요구사항
- [ ] 시니어 회원가입 시 Pet이 자동 생성되고 User에 연결된다
- [ ] Pet 조회 API — 현재 포인트, 레벨 반환
- [ ] 레벨 계산: `level = floor(sqrt(point / 10))`
- [ ] 복약 성공(TAKEN) 이벤트 발생 시 포인트 증가
- [ ] 포인트 증가량은 설정값으로 관리 (초기값: 복약 1회당 10포인트)

### 비기능 요구사항
- 레벨은 DB에 저장하지 않고 서버에서 point로부터 계산 (밸런스 변경 시 마이그레이션 불필요)
- 복약 → 포인트 증가는 약한 결합(이벤트 기반) 권장

## 4) 범위 / 비범위 (중요)
### 포함
- Pet 엔티티 보강 (도메인 메서드)
- PetRepository, PetService, PetController, DTO
- 시니어 가입 시 Pet 자동 생성
- Pet 조회 API
- 레벨 계산 도메인 로직
- 복약 성공 → 포인트 증가 이벤트 연동
- 단위 테스트 + E2E 테스트

### 제외 (Out of Scope)
- 캐릭터 외형/스킨/아이템 시스템
- 보호자별 펫 (펫은 시니어 전용)
- 랭킹 시스템
- 포인트 차감/소비 메커니즘
- 레벨별 보상 체계

## 5) 설계
### 5-1) 도메인 모델
- `Pet` 엔티티: 기존 `id`, `point`에 도메인 메서드 추가 (`addPoint()`, `getLevel()`)
- `User.pet` (Long FK) → Pet 연결은 기존 구조 유지
- 레벨은 엔티티 필드가 아닌 계산 값

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| GET | /api/v1/pets/me | 내 펫 조회 | 필수 | - | `PetResponse` |

### 5-3) 외부 연동
- 없음

### 5-4) 데이터 흐름

**Pet 자동 생성:**
시니어 회원가입 → Pet(point=0) 생성 → User.pet에 Pet.id 저장

**포인트 증가:**
복약 기록 TAKEN 확정 → Spring ApplicationEvent 발행 → PetEventListener가 수신 → Pet.addPoint() → 저장

### 5-5) DB 마이그레이션
- 기존 `pets` 테이블 그대로 사용 (변경 없음)

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: Pet 엔티티 도메인 로직 + PetRepository + PetService + 조회 API + 시니어 가입 시 자동 생성
- [ ] PR 2: 복약 성공 이벤트 → 포인트 증가 연동

## 7) 테스트 전략
- Pet 도메인 단위 테스트 (레벨 계산, addPoint)
- PetService 단위 테스트 (Mockito)
- E2E 테스트 (RestAssured) — 펫 조회 성공
- 이벤트 연동 통합 테스트 (PR2)

## 8) 오픈 질문
없음 (모두 합의됨)

## 9) 결정 로그
- 2026-05-02: 초안 작성. 레벨 공식 `floor(sqrt(point / 10))` 확정. 복약 성공 시 포인트 증가, 시니어 가입 시 자동 생성.
