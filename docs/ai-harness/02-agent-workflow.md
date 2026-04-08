# Agent Workflow

## 1) 브랜치 전략
- 유지 브랜치: `main`, `develop`
- 작업 브랜치: `develop`에서 파생 (`feature`, `refactor`, `chore`, `fix`)
- 브랜치 이름 예시: `feature/ocr-result-save-#123`
- 이슈는 `.github/ISSUE_TEMPLATE/task.md` 템플릿으로 생성하고, 제목은 브랜치 목적이 드러나게 간결하게 작성한다.

## 2) PR 라이프사이클
1. 작업 브랜치에서 변경
2. PR 생성 (base: `develop`)
3. **PR 생성 직후 즉시(같은 작업 단계에서) 아래를 모두 수행한다. 이 단계를 건너뛴 PR은 리뷰 대상이 아니다.**
   - [ ] `type:*` 라벨 1개 부여 (`type:feat` `type:fix` `type:refactor` `type:chore` `type:docs` `type:test` `type:style`)
   - [ ] `scope:*` 라벨 1개 부여 (화이트리스트: `user` `pet` `prescription` `medicine` `medication` `health` `infra`)
   - [ ] AI가 작성/보조한 PR이면 `ai-generated` 라벨 부여
   - [ ] 보호 영역(`docs/ai-harness/01-harness-spec.md` §6) 변경 시 `needs-human-review` 라벨 부여
   - [ ] PR 본문이 `.github/PULL_REQUEST_TEMPLATE.md`를 덮어쓴 경우 AI 체크리스트 블록을 수동으로 다시 채워 넣는다 (`gh pr create --body`는 템플릿을 무시함).
4. 리뷰 1명 승인
5. Squash merge

> **AI 에이전트 자기 점검:** PR을 만든 직후 위 4개 체크박스를 머릿속에 떠올렸는가? 떠올리지 않았다면 PR 생성이 끝난 것이 아니다. 라벨 부여까지가 "PR 생성" 단계다.

## 3) PR 작성 규칙
- 제목 형식(고정): `type(scope): 제목`
  - 예시: `feat(prescription): OCR 추출 결과 저장 개선`
  - `scope`는 아래 화이트리스트에서 선택 (final): `user`, `pet`, `prescription`, `medicine`, `medication`, `health`, `infra`
  - 신규 scope가 필요하면 이 문서를 먼저 PR로 갱신한 뒤 사용한다.
- PR 본문은 `.github/PULL_REQUEST_TEMPLATE.md` 템플릿을 사용한다.
- PR 본문은 `AS-IS`, `TO-BE` 중심으로 간결하게 작성한다.
- PR은 과도하게 커지기 전에 분할한다.
- PR 없이 직접 commit/push 금지

## 4) 예외 정책
- 리뷰 없이 개인 머지 가능 케이스:
  - 보안 긴급 이슈(예: Secret key 유출 대응)
  - 파일 내용 변경 없이 대규모 코드 스타일 변경
  - 대규모 패키지 구조 변경
- 단, 예외 머지도 PR은 생성해야 하며 사유를 본문에 기록한다.
- 단, 예외 머지는 사후 리뷰를 필수로 수행한다. (머지 후 24시간 이내)

## 5) 사후 리뷰(Post Review) 프로세스
1. 머지 당일 담당자가 `Post-Review` 라벨을 PR에 추가한다.
2. 리뷰어 1명이 24시간 내 아래 항목을 검토한다.
   - 보안 정책 위반 여부 (시크릿/민감정보 노출)
   - 품질 게이트 우회 사유의 타당성
   - 회귀 가능성 및 롤백 가능성
3. 결과를 PR 코멘트에 `결론/후속액션/기한`으로 기록한다.
4. 후속 수정이 필요하면 48시간 내 보완 PR을 생성한다.

## 6) 커밋 컨벤션
- AngularJS commit convention 사용
- 타입: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
- 형식:

```text
feat: 제목 (필수)

- 본문 (생략 가능)
```

## 7) 핸드오프 체크리스트
- AI 작업 PR의 핸드오프 체크리스트는 `.github/PULL_REQUEST_TEMPLATE.md`의 `AI Agent 전용 체크리스트`를 single source of truth로 사용한다.
- 사람 작성 PR은 `AS-IS`/`TO-BE`만 채우면 되며, AI 체크리스트 블록은 비워둔다.
