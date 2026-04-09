---
id: 0008
title: 기능 기획은 단발 plan이 아닌 living Feature Spec으로 관리
status: accepted
date: 2026-04-09
deciders: [@goohong]
---

# 0008. 기능 기획은 단발 plan이 아닌 living Feature Spec으로 관리

## Context
AI 바이브 코딩에서 기능 구현 전 합의가 필요하다. 초기에는 "PR마다 plan .md" 모델을 검토했으나, 그 경우 요구사항이 바뀌면 plan이 무용지물이 된다. 팀원 공유/온보딩 관점에서도 단발 문서는 한계.

## Decision
기능 기획은 **`docs/features/<slug>.md`에 living Feature Spec 형태**로 관리한다. 1 spec ↔ N PR 구조를 가지며, 요구사항 변경 시 spec을 먼저 갱신한 뒤 구현한다. 상태는 `draft → approved → implementing → shipped → deprecated` 순.

## Consequences
### 긍정적
- 합의 이력 보존: 결정 로그로 "왜 이렇게 만들었는지"를 한 파일에 집중
- 재방문 가치: 신규 팀원 온보딩, AI 에이전트 컨텍스트 소스로 재사용
- 스코프 크립 방지: "범위/비범위" 섹션이 명시적 브레이크

### 부정적
- 초기 작성 비용 존재 (소규모 변경은 불필요)
- spec과 코드 간 동기화 책임 발생 → 프로세스 규율 필요

## Alternatives (considered)
- (A) PR 단위 plan .md — 1회용, 기능 단위 추적 불가
- (B) GitHub Issue 본문으로 대체 — 편집 이력/버전관리 약함, 검색성 낮음
- (C) Notion 등 외부 — 코드와 분리, AI 컨텍스트 로드 어려움

## References
- `docs/features/README.md`
- `docs/ai-harness/02-agent-workflow.md §9`
- PR #39 (Feature Spec 프로세스 도입)
