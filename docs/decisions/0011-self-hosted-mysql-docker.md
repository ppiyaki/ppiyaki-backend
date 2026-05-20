---
id: 0011
title: 운영 DB를 backend 서버 docker MySQL로 자체 호스팅
status: accepted
date: 2026-05-20
deciders: [@goohong]
---

# 0011. 운영 DB를 backend 서버 docker MySQL로 자체 호스팅

## Context
ADR 0001로 NCP 매니지드 MySQL 8.4.6을 도입했으나, 베타 사용자 < 100명 규모에서는 매니지드 백업·HA 기능이 비용 대비 과잉이다. 운영 데이터 사이즈도 22.84MB로 매우 작고(`pill_identifications` 캐시 21.59MB가 대부분), 다운타임 허용폭이 넓어 단일 인스턴스로 충분하다. 매니지드 월 구독료를 줄이는 것이 우선순위가 됐다.

## Decision
운영 DB를 **backend 서버(211.188.48.217) 의 docker MySQL 8.4.6** 으로 이전한다.
- `ppiyaki-monitoring` docker network 내부에만 노출, 외부 포트 미공개
- `mysql-data` 영속 볼륨에만 데이터 보관 (외부/오프사이트 백업 없음)
- backend는 `jdbc:mysql://mysql:3306/ppiyaki` (network DNS)로 접속
- credential은 backend 컨테이너 env(`DB_USERNAME`/`DB_PASSWORD`)와 동일하게 .env에 주입
- 메모리 1GB 제한, healthcheck로 기동 검증

ADR 0001은 superseded.

## Consequences
### 긍정적
- 매니지드 월 구독료 제거 (가장 큰 비용 절감 효과)
- backend ↔ DB 간 네트워크 hop이 사라져 평균 latency 감소
- 모니터링 stack과 동일한 docker compose로 통합 관리

### 부정적
- **단일 장애점**: backend 서버 디스크/하드웨어 장애 시 데이터 손실 (외부 백업 없음 — 결정 시 명시적으로 수용)
- **HA 없음**: 매니지드의 자동 fail-over 기능 상실
- **자원 경합 위험**: backend + monitoring 5종 + MySQL이 같은 서버(2 vCPU / 3.8GB RAM)에서 공존 — 트래픽 증가 시 분리 필요
- 백업·복구 운영 부담이 운영자에게 이전됨 (현재는 자동 cron 백업 없음)

## Alternatives (considered)
- (A) **별도 저렴한 VM에 docker MySQL** — 자원 분리는 안전하지만 VM 고정 비용이 다시 발생 → 비용 절감 효과 절반 이하
- (B) **다른 클라우드의 저렴한 매니지드** (AWS RDS 등) — NCP Object Storage·CD 와 분리되어 운영 복잡도 증가
- (C) **현 상태 유지** — 비용 절감 목표 미달성

## References
- Spec: `docs/features/db-self-hosted-migration.md`
- Supersedes: ADR 0001
- 마일스톤: 이슈 #395
- 관련 PR: #396 (spec + docker-compose), #398 (migration scripts), #399 (마무리)
- 마이그레이션 일시: 2026-05-20
