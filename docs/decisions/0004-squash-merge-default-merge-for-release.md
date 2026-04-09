---
id: 0004
title: 기본 머지는 Squash, 릴리즈(develop→main)는 Merge commit
status: accepted
date: 2026-04-08
deciders: [@goohong]
---

# 0004. 기본 머지는 Squash, 릴리즈(develop→main)는 Merge commit

## Context
머지 전략은 히스토리 가독성과 롤백 용이성에 직결된다. feature/fix 단위는 깔끔한 1커밋이 유리하지만, 릴리즈 경계는 "어떤 PR들이 함께 나갔는가"를 보존하는 편이 롤백/추적에 유리하다.

## Decision
- **feature/fix/refactor/chore/docs → develop**: **Squash merge**. PR 내 여러 커밋을 1커밋으로 압축.
- **develop → main (릴리즈)**: **Merge commit** (`--merge`). develop의 개별 squash 커밋들을 main에 그대로 보존하고 머지 커밋이 릴리즈 경계가 된다.

## Consequences
### 긍정적
- develop 히스토리: PR 1개 = 커밋 1개 → `git log`가 깔끔
- main 히스토리: 릴리즈마다 "포함된 PR 목록"이 merge commit 이하에 보존
- 롤백: 머지 커밋 1개 revert로 릴리즈 전체 되돌리기 가능

### 부정적
- Merge commit은 비선형 히스토리 → 일부 툴에서 시각적으로 혼란 가능
- 릴리즈 때 실수로 `--squash`를 누르면 메타데이터 손실 → 문서와 자기 점검으로 예방

## Alternatives (considered)
- (A) 전부 Squash — main에서 개별 PR 추적 어려움
- (B) 전부 Merge commit — develop 히스토리 오염
- (C) Rebase merge — 선형이지만 force-push 필요, 리뷰 경로 손실

## References
- `docs/ai-harness/02-agent-workflow.md §8`
