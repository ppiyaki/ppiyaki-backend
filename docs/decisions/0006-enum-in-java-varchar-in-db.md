---
id: 0006
title: 열거형은 Java enum으로 다루고 DB는 varchar로 저장
status: accepted
date: 2026-04-09
deciders: [@goohong]
---

# 0006. 열거형은 Java enum으로 다루고 DB는 varchar로 저장

## Context
`users.role`, `gender`, `delivery_status`, `warning_level` 등 열거형 필드가 많다. DB 타입을 ENUM으로 둘지, varchar로 둘지, Java 측은 enum으로 강타입할지 결정 필요.

## Decision
- **Java 측은 enum** (`UserRole`, `Gender`, `DeliveryStatus`, `ReminderChannel`, `DevicePlatform`, `ReportPeriodType`, `DurWarningLevel`, `OAuthProvider`)
- **DB 측은 varchar**로 저장 (MySQL ENUM 타입 사용 금지)
- 매핑은 `@Enumerated(EnumType.STRING)` 사용

## Consequences
### 긍정적
- Java 측 타입 안전성 확보 (컴파일 타임 검증)
- DB 측은 varchar라 enum 값 추가 시 **DDL 변경 불필요**, 유연
- SQL 직접 조회 시 값이 의미 있는 문자열(정수 아님)

### 부정적
- Java와 DB의 enum 정의가 분리되어 있어 동기화 책임이 애플리케이션에 있음
- DB 단독으로는 잘못된 값 진입을 막지 못함 (Java가 가드)

## Alternatives (considered)
- (A) MySQL ENUM 타입 — 값 추가가 DDL이라 운영 부담
- (B) Java enum + DB int (`@Enumerated(EnumType.ORDINAL)`) — 순서 변경에 매우 취약, 금기
- (C) 별도 코드 테이블 — 지금 규모에선 과설계

## References
- `docs/ai-harness/06-domain-model.md §5`
- PR #29, #31, #32, #33 (enum 도입 커밋들)
