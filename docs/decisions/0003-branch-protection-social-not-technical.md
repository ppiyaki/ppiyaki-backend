---
id: 0003
title: 브랜치 보호는 기술적 강제 없이 팀 합의(사회적 규약)로 운영
status: accepted
date: 2026-04-08
deciders: [@goohong]
---

# 0003. 브랜치 보호는 기술적 강제 없이 팀 합의(사회적 규약)로 운영

## Context
`main` 직접 푸시 금지, 리뷰 1명 필수, CI 통과 등 규칙이 있으나 GitHub Branch Protection 설정으로 강제하면 긴급 상황(예: 보안 핫픽스) 유연성이 떨어진다. 또한 초기 소규모 팀에서는 강제 차단이 오히려 마찰을 만든다.

## Decision
본 레포는 브랜치 보호 규칙을 **GitHub 설정으로 강제하지 않는다**. 규칙은 문서와 팀 합의(사회적 규약)로 운영한다. 룰 위반 시 기술적 차단 대신 사후 리뷰(`Post-Review`)로 대응한다.

## Consequences
### 긍정적
- 긴급 머지 가능 (예: 보안 핫픽스)
- 초기 학습/실험 중 마찰 최소
- 규칙 변경이 PR 한 번으로 가능 (Settings 방문 불필요)

### 부정적
- 규칙 위반이 기술적으로 가능 → 팀 자율성에 의존
- 실수로 `main`에 직접 푸시될 가능성
- CI 실패 상태로도 머지 가능 (팀 합의로 차단)

## Alternatives (considered)
- (A) Branch Protection 전면 활성화 — 유연성 손실, 긴급 상황 블록
- (B) CODEOWNERS + Required Reviews만 켜기 — 일부 강제. 향후 팀 규모 커지면 재검토

## References
- `docs/ai-harness/00-index.md §5`
- `docs/ai-harness/03-quality-gates.md §6`
