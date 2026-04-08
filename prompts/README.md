## prompts/

AI 코딩 하네스에서 사용하는 재사용 프롬프트를 저장한다.
운영 규칙은 `docs/ai-harness/05-prompt-ops.md`를 따른다.

### 명명 규칙
`<용도>-<스코프>.md` (예: `feature-prescription.md`, `review-security.md`)

### 파일 헤더 (frontmatter 필수)
```markdown
---
purpose: 무엇을 만드는 프롬프트인가
scope: user|pet|prescription|medicine|medication|health|infra
owner: GitHub 핸들
last_reviewed: YYYY-MM-DD
---
```
