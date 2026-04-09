---
id: 0007
title: DUR 점검 결과는 매 호출마다 영속 로그로 저장, 캐싱 없음
status: accepted
date: 2026-04-09
deciders: [@goohong]
---

# 0007. DUR 점검 결과는 매 호출마다 영속 로그로 저장, 캐싱 없음

## Context
DUR(약물 상호작용) 점검은 외부 API 호출 필요. 캐싱이 자연스러운 후보지만, 약물 정보는 시간이 지남에 따라 변경될 수 있어(신규 경고, 금기 추가) 캐시된 결과가 잘못된 판단을 유발할 수 있다.

## Decision
- `dur_checks` 테이블에 **매 호출 결과를 immutable 레코드**로 저장한다.
- 캐싱 레이어(Redis 등)는 **도입하지 않는다**.
- 가장 최근 결과가 필요하면 `(medicine_id, checked_at DESC)` 인덱스로 조회.
- 외부 API 비용이 문제가 되면 **그때 가서** TTL 캐시 도입을 별도 ADR로 재검토.

## Consequences
### 긍정적
- 감사(audit) 용이: 특정 시점의 DUR 판단 근거가 영구 기록
- 정확성 우선: 항상 외부 최신 정보 기반
- 단순한 아키텍처: 캐시 일관성 고민 불필요

### 부정적
- 외부 API 호출 비용 증가
- 외부 API 장애 시 DUR 기능 직접 영향 (회로 차단기 고려 필요)

## Alternatives (considered)
- (A) TTL 캐시 (Redis 24h) — 약물 정보 변경을 놓칠 위험
- (B) 캐시 + 캐시 invalidation 웹훅 — 과설계. DUR API 제공자가 웹훅 지원할지 불명
- (C) 결과만 저장하지 않고 매번 호출 — 감사/이력 불가

## References
- `docs/ai-harness/06-domain-model.md §5 dur_checks, §7-7`
- PR #33 (`dur_checks` 엔티티 도입)
