# Notion API 명세 연동 가이드

> API 엔드포인트가 추가/수정/삭제될 때 Notion의 "API 명세" 데이터베이스를 **같은 PR 안에서** 갱신한다.

## 1) 왜 필요한가
- API 명세서가 코드와 동기화되지 않으면 프론트엔드·기획 팀이 잘못된 정보를 참조한다.
- PR 단위로 코드와 명세를 함께 갱신하면 리뷰 시 변경 범위를 한눈에 파악할 수 있다.

## 2) Notion API 명세 DB 정보

| 항목 | 값 |
|---|---|
| DB 이름 | API 명세 |
| DB ID | `32e55690-fbc7-808a-b226-fc9df044d287` |
| 상위 페이지 | API 명세서 (`32e55690-fbc7-80fc-a87c-ec9860c99b5d`) |

### 컬럼 구조

| 컬럼명 | 타입 | 설명 |
|---|---|---|
| API 명세 | title | API 이름 (예: "카카오 로그인") |
| HTTP | select | `GET` / `POST` / `PUT` / `PATCH` / `DELETE` |
| URI | rich_text | 엔드포인트 경로 (예: `/api/v1/medicines`) |
| 도메인 | multi_select | `유저` / `처방전` / `약` / `로그` / `스케쥴` |
| 구현 여부 | status | `시작 전` / `진행 중` / `완료` |
| 백엔드 담당 | people | 담당 개발자 |

### 페이지 본문 템플릿

각 API 명세 페이지의 본문은 다음 구조를 따른다:

```
[목차]
# Details (blue_background)
  API 설명 paragraph
---
# Request (blue_background)
  ## 헤더 (yellow_background) → 테이블 또는 bullet
  ## Path 파라미터 (yellow_background)
  ## Request Body (yellow_background)
  ### JSON 예시 (green_background, bold) → code block (json)
---
# Response (blue_background)
  ## Response Status (yellow_background)
  ## Response Body (yellow_background)
  ### JSON 예시 (green_background, bold) → code block (json)
```

## 3) 갱신 규칙

### 신규 API 추가 시
1. Notion DB에 새 행 생성 (API 명세, HTTP, URI, 도메인, 구현 여부=완료)
2. 페이지 본문에 §2 템플릿 구조로 Request/Response 상세 작성
3. code block으로 JSON 예시 포함

### 기존 API 수정 시
1. 해당 행의 속성 업데이트 (URI, HTTP method 등)
2. 페이지 본문의 변경된 필드/DTO 갱신

### API 삭제 시
1. 해당 행을 Notion에서 삭제 (archive)

### PR 체크리스트
PR을 만들 때 API 변경이 있으면 다음을 확인한다:
- [ ] Notion API 명세 DB에 반영했는가
- [ ] Request/Response DTO 변경이 본문에 반영되었는가

## 4) NOTION_API_KEY 관리

### 원칙
- **절대 커밋하지 않는다.** `.env`, `.claude/settings.local.json`은 `.gitignore` 대상이다.
- 키는 팀 내 안전한 채널(1Password, 팀 Vault 등)로 공유한다.
- 키 유출 시 즉시 Notion 설정에서 revoke → 재발급 → 팀원에게 재배포한다.

### 설정 방법 (팀원 온보딩)

**Step 1 — 환경변수 등록**

프로젝트 루트의 `.env` 파일에 추가한다 (이미 `.gitignore` 대상):
```
NOTION_API_KEY=ntn_xxxxx
```

**Step 2 — Claude Code MCP 설정**

`~/.claude/settings.json`에 Notion MCP 서버를 추가한다:
```json
{
  "mcpServers": {
    "notion": {
      "command": "npx",
      "args": ["-y", "@notionhq/notion-mcp-server"],
      "env": {
        "OPENAPI_MCP_HEADERS": "{\"Authorization\": \"Bearer ${NOTION_API_KEY}\", \"Notion-Version\": \"2022-06-28\"}"
      }
    }
  }
}
```

**Step 3 — 프로젝트 로컬 설정**

`.claude/settings.local.json`에 환경변수를 주입한다 (이 파일도 `.gitignore` 대상):
```json
{
  "env": {
    "NOTION_API_KEY": "ntn_xxxxx"
  },
  "enableAllProjectMcpServers": true,
  "enabledMcpjsonServers": ["notion"]
}
```

**Step 4 — 연결 확인**

Claude Code에서 다음을 실행하여 연결을 확인한다:
```
> Notion 접근해봐
```
`ppiyaki-claude` 봇 정보가 반환되면 설정 완료.

### 참고: Notion MCP 도구의 제한사항
- MCP 도구는 `paragraph`와 `bulleted_list_item` 블록만 생성 가능
- heading, table, code, divider 등은 Notion REST API를 직접 호출해야 함 (환경변수 `$NOTION_API_KEY` 사용)
- code block 생성 예시:
  ```bash
  curl -X PATCH "https://api.notion.com/v1/blocks/{page_id}/children" \
    -H "Authorization: Bearer $NOTION_API_KEY" \
    -H "Notion-Version: 2022-06-28" \
    -H "Content-Type: application/json" \
    -d '{"children": [{"type": "code", "code": {"rich_text": [{"type": "text", "text": {"content": "..."}}], "language": "json"}}]}'
  ```

## 5) 도메인 ↔ Notion 매핑

| PR scope | Notion 도메인 |
|---|---|
| `user` | 유저 |
| `prescription` | 처방전 |
| `medicine` | 약 |
| `medication` | 스케쥴 |
| `health` | 로그 |
