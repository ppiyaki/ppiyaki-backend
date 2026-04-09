---
id: 0005
title: `pet`을 별도 바운디드 컨텍스트/스코프로 분리
status: accepted
date: 2026-04-09
deciders: [@goohong]
---

# 0005. `pet`을 별도 바운디드 컨텍스트/스코프로 분리

## Context
삐약이 캐릭터는 복약 성공 시 성장하는 게이미피케이션 요소다. 의료 도메인(`health`, `medication`)에 통합할지, 별도 컨텍스트로 둘지 결정 필요. 현재 스키마는 `pets` 테이블이 매우 작음(`id`, `point`만).

## Decision
`pet`을 **독립 바운디드 컨텍스트 + 독립 PR scope**로 유지한다. `health`가 `pet`에 의존하지 않으며, 복약 성공 이벤트를 `pet`이 subscribe하는 단방향 결합을 권장한다.

## Consequences
### 긍정적
- 책임 분리: 의료 데이터 모델과 게이미피케이션 분리
- 변경 주기 독립: 게이미피케이션 밸런스 변경이 의료 코드에 영향 없음
- PII 민감도 차이 존중: `pet`은 로깅 정책을 자유롭게 가져갈 수 있음
- 확장 여지: 캐릭터 종류/아이템/랭킹 등 기능 추가 시 깔끔

### 부정적
- 초기에는 테이블/코드가 작아 분리 오버헤드로 보일 수 있음
- 이벤트 기반 결합 시 일관성 주의 필요

## Alternatives (considered)
- (A) `health` 하위에 포함 — 의료 도메인 복잡도 증가, 변경 주기 충돌
- (B) `user` 하위에 포함 — 사용자 속성으로 오해. 성장 로직이 user에 침범

## References
- `docs/ai-harness/06-domain-model.md §3`
- PR #13 (도메인 문서 초안)
