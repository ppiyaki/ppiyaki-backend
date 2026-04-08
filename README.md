# ppiyaki-backend

복약/건강 도메인을 다루는 백엔드 프로젝트입니다.

## 팀 온보딩 (5줄 요약)
1. 작업은 이슈부터 — `.github/ISSUE_TEMPLATE/task.md`로 이슈를 만들고 번호를 받는다.
2. `develop`에서 `feature|fix|refactor|chore/<요약>-#<이슈번호>` 브랜치를 판다.
3. 커밋은 AngularJS 컨벤션(`type: 제목`), PR 제목은 `type(scope): 제목` 형식.
4. 푸시 전 `./gradlew checkstyleMain spotlessCheck test` 통과 확인.
5. PR 템플릿의 `AS-IS` / `TO-BE`를 채우고, 리뷰어 1명 승인 후 Squash merge.

## 운영 가이드 (AI 하네스 포함)

### 1) 브랜치 전략
- 유지 브랜치: `main`, `develop`
- 작업 브랜치: `develop`에서 파생 (`feature`, `refactor`, `chore`, `fix`)
- 브랜치 이름 예시: `feature/made-something-new-#123`
- 이슈는 `.github/ISSUE_TEMPLATE/task.md`로 생성하고, 이슈 번호를 브랜치 이름 뒤에 붙인다.

### 2) PR 규칙
- `main` 직접 푸시 금지, PR 필수
- 리뷰어 1명 승인 후 머지
- 머지 방식은 `Squash merge`
- PR 제목 형식 고정: `type(scope): 제목`
- PR 본문 템플릿 사용: `.github/PULL_REQUEST_TEMPLATE.md`
- PR 본문은 `AS-IS`, `TO-BE`를 간결하게 작성
- 예외 머지 시, 머지 후 24시간 이내 `Post-Review` 수행

### 3) 커밋 컨벤션
- AngularJS commit convention 사용
- 타입: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
- 형식:

```text
feat: 제목

- 본문 (선택)
```

### 4) 품질 게이트 (필수)
- 빌드/테스트 통과 상태에서만 머지
- 최소 확인 명령어:

```bash
./gradlew checkstyleMain spotlessCheck
./gradlew test
```

### 5) 보안/민감정보 원칙
- 시크릿(API 키/토큰/비밀번호) 하드코딩 금지
- 복약 기록/건강 프로필 등 의료정보 원문 노출 금지
- 로그/샘플/스크린샷에 민감정보는 마스킹 처리

## 상세 규칙 문서
- 문서 인덱스: `docs/ai-harness/00-index.md`
- 하네스 규격: `docs/ai-harness/01-harness-spec.md`
- 워크플로우: `docs/ai-harness/02-agent-workflow.md`
- 품질 게이트: `docs/ai-harness/03-quality-gates.md`
- 보안 정책: `docs/ai-harness/04-security-policy.md`
- 프롬프트 운영: `docs/ai-harness/05-prompt-ops.md`

## 참고
- 기본 Spring 문서: `HELP.md`

