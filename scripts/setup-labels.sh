#!/usr/bin/env bash
# GitHub 라벨 일괄 생성 스크립트
# 사용법: gh auth login 후 ./scripts/setup-labels.sh
# 이미 존재하는 라벨은 --force로 색상/설명만 갱신한다.

set -euo pipefail

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI가 설치돼 있지 않습니다. https://cli.github.com/ 에서 설치하세요." >&2
  exit 1
fi

create() {
  local name="$1" color="$2" desc="$3"
  gh label create "$name" --color "$color" --description "$desc" --force
}

# 운영
create "task"               "0E8A16" "이슈 템플릿 기본 라벨"
create "Post-Review"        "D93F0B" "예외 머지 후 24시간 내 사후 리뷰 대상"

# Type
create "type:feat"          "1D76DB" "신규 기능"
create "type:fix"           "D73A4A" "버그 수정"
create "type:refactor"      "5319E7" "리팩터링"
create "type:chore"         "C5DEF5" "잡일/설정"
create "type:docs"          "0075CA" "문서"
create "type:test"          "BFD4F2" "테스트"
create "type:style"         "FBCA04" "코드 스타일/포맷"

# Scope (도메인)
create "scope:user"         "EDEDED" "도메인: user"
create "scope:pet"          "EDEDED" "도메인: pet"
create "scope:prescription" "EDEDED" "도메인: prescription"
create "scope:medicine"     "EDEDED" "도메인: medicine"
create "scope:medication"   "EDEDED" "도메인: medication"
create "scope:health"       "EDEDED" "도메인: health"
create "scope:infra"        "EDEDED" "도메인: infra"

# AI 운영
create "ai-generated"       "8A2BE2" "AI 보조/생성으로 작성된 PR"
create "needs-human-review" "B60205" "보호 영역(마이그레이션/시크릿/CI/빌드) 변경 PR"

echo "완료."
