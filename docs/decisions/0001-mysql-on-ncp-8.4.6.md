---
id: 0001
title: 운영 DB는 NCP 매니지드 MySQL 8.4.6
status: accepted
date: 2026-04-08
deciders: [@goohong]
---

# 0001. 운영 DB는 NCP 매니지드 MySQL 8.4.6

## Context
백엔드 저장소 선정 필요. 팀은 자바/Spring Boot 경험이 충분하고, 단순 읽기/쓰기 워크로드에 안정성 중시. 레포 Notion 기술 문서에는 MySQL 8.4.5로 적혀 있으나 실제 NCP 매니지드 서비스 버전은 8.4.6.

## Decision
운영 DB는 **Naver Cloud Platform의 매니지드 MySQL 8.4.6** 을 사용한다. Notion의 8.4.5 표기는 오타이며 본 레포의 모든 문서·설정·로컬 개발 환경은 8.4.6 기준으로 정렬한다.

## Consequences
### 긍정적
- LTS, 장기 지원으로 운영 안정성 확보
- NCP 내 네트워크/비용 이점 (Object Storage 등 동일 벤더)
- 로컬 docker-compose도 같은 8.4.6을 사용해 환경 차이 최소화

### 부정적
- NCP 종속. 멀티 클라우드로 이전 시 마이그레이션 비용
- 매니지드 비용 월 고정 부담 (전체 18만원 예산 내에서 관리 필요)

## Alternatives (considered)
- (A) PostgreSQL — 팀 숙련도 낮음
- (B) MySQL 8.0 LTS — 8.4가 최신 LTS이므로 신규 프로젝트에 8.4 선호

## References
- `docs/ai-harness/06-domain-model.md §9`
- `docker-compose.yml`, `application-local.yml`
