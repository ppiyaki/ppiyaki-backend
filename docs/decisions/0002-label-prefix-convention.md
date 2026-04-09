---
id: 0002
title: GitHub 라벨은 `type:` / `scope:` prefix 사용
status: accepted
date: 2026-04-08
deciders: [@goohong]
---

# 0002. GitHub 라벨은 `type:` / `scope:` prefix 사용

## Context
PR/이슈 라벨에 커밋 타입(`feat`, `fix` 등)과 도메인 스코프(`user`, `medicine` 등) 두 축이 있다. prefix 없이 `feat`, `infra`만 쓰면 (a) 두 축이 섞여 자동완성이 혼란, (b) `infra`가 type인지 scope인지 모호.

## Decision
모든 운영/분류 라벨에 **콜론 prefix**를 사용한다.
- `type:feat`, `type:fix`, `type:refactor`, `type:chore`, `type:docs`, `type:test`, `type:style`
- `scope:user`, `scope:pet`, `scope:prescription`, `scope:medicine`, `scope:medication`, `scope:health`, `scope:infra`

운영 라벨(`task`, `Post-Review`, `ai-generated`, `needs-human-review`)은 prefix 없이 유지.

## Consequences
### 긍정적
- GitHub 라벨 자동완성 시 그룹핑이 명확 (`type:` / `scope:`)
- 충돌 방지: `infra`가 type인지 scope인지 명확 (scope)
- 새 라벨 추가 규칙이 암묵적이지 않음

### 부정적
- 라벨 이름이 길어짐 (시각적 노이즈 소폭 증가)

## Alternatives (considered)
- (A) prefix 없음 (`feat`, `infra`) — 자동완성 섞임, 충돌 가능
- (B) 슬래시 구분자 (`type/feat`, Kubernetes 스타일) — GitHub UI에서 약간 깔끔하나 콜론이 기존 OSS 관례

## References
- `scripts/setup-labels.sh`
- `docs/ai-harness/02-agent-workflow.md §3`
