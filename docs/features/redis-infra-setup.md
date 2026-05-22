---
feature: Redis 인프라 설정 및 Spring Boot 연동 구성
slug: redis-infra-setup
status: draft
owner: @dohyeon
scope: infra
related_issues: [403]
related_prs: []
last_reviewed: 2026-05-22
---

# Redis 인프라 설정 및 Spring Boot 연동 구성

## 1) 개요 (What / Why)
프로젝트에 Redis를 도입하여 JVM 메모리 기반 캐시와 Rate Limiter를 Redis로 전환하기 위한 **기반 인프라**를 구성한다.

현재 외부 API 응답 캐시(`DrugInfoClient`, `MfdsApiClient`)와 Rate Limiter(`InMemoryRateLimiter`)가 모두 `ConcurrentHashMap`으로 동작한다. 서버 재시작 시 캐시가 소실되고, 다중 인스턴스 환경에서 상태가 공유되지 않는 문제가 있다. 이 Feature Spec은 Redis 서버 자체와 Spring Boot 연동 설정만 다루며, 개별 캐시/Rate Limiter의 Redis 전환은 후속 이슈(#404, #405)에서 진행한다.

## 2) 사용자 시나리오
- (개발자) 로컬에서 `docker compose up -d`로 MySQL + Redis가 함께 기동되어 별도 설치 없이 개발 가능.
- (운영자) prod 배포 시 Redis 컨테이너가 backend와 같은 docker network에서 동작하며, 외부 포트 노출 없이 내부 통신.
- (개발자) `RedisTemplate` 또는 Spring Cache abstraction을 통해 Redis에 값을 저장/조회할 수 있다.

## 3) 요구사항
### 기능 요구사항
- [ ] `build.gradle`에 `spring-boot-starter-data-redis` 의존성 추가
- [ ] `application.yml`에 Redis 접속 설정 추가 (default 프로필: localhost:6379)
- [ ] `application-prod.yml`에 prod Redis 접속 설정 추가 (환경변수 바인딩)
- [ ] 로컬 `docker-compose.yml`에 Redis 7 컨테이너 추가
- [ ] `StringRedisTemplate` 빈이 정상 주입되는지 확인하는 통합 테스트 작성
- [ ] `.env.example`에 Redis 관련 환경변수 placeholder 추가

### 비기능 요구사항
- **보안**: prod Redis는 docker 내부 네트워크만 접근 가능 (외부 포트 노출 금지). 비밀번호 설정.
- **관측성**: Actuator + Micrometer의 Redis 메트릭 자동 수집 활성화
- **자원**: Redis 컨테이너 `maxmemory 256mb` + `maxmemory-policy allkeys-lru` 설정 (서버 OOM 방지)
- **호환**: 기존 테스트(H2 기반)가 Redis 없이도 통과해야 함 (Redis 비활성 시 기존 동작 유지)

## 4) 범위 / 비범위 (중요)
### 포함
- Redis 의존성 추가 및 Spring Boot auto-configuration 연동
- 로컬/prod 환경 Redis 컨테이너 및 접속 설정
- 연결 확인용 통합 테스트
- `.env.example` 갱신

### 제외 (Out of Scope)
- **DrugInfoClient / MfdsApiClient 캐시의 Redis 전환**: 후속 이슈 #404에서 진행
- **InMemoryRateLimiter의 Redis 전환**: 후속 이슈 #405에서 진행
- **Redis Sentinel / Cluster 구성**: 단일 인스턴스. 베타 규모에서 HA 불필요
- **Spring Session (세션 저장소)**: JWT 기반 인증이므로 세션 저장소 불필요
- **prod 서버 docker-compose 수정**: CD 파이프라인 변경은 별도 작업

## 5) 설계

### 5-1) 도메인 모델
인프라 변경이며 도메인 엔티티/컨텍스트는 건드리지 않는다.

### 5-2) API 엔드포인트
변경 없음 (인프라 변경).

### 5-3) 외부 연동
- **Redis 7** (로컬: docker-compose, prod: 같은 서버의 docker 컨테이너)
- 라이브러리: `spring-boot-starter-data-redis` (Lettuce 기본 드라이버)

### 5-4) 데이터 흐름 / 시퀀스

```
[Spring Boot App]
      │
      ├── RedisTemplate / @Cacheable
      │         │
      └─────── Lettuce ──── Redis 7 (docker, port 6379 내부)
```

### 5-5) DB 마이그레이션
없음. Redis는 별도 데이터 저장소이며 MySQL 스키마 변경 없음.

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: `chore(infra): Redis 인프라 설정 및 Spring Boot 연동 구성 #403` — 의존성, 설정, docker-compose, 통합 테스트, .env.example

## 7) 테스트 전략
- **통합 테스트**: `StringRedisTemplate`으로 `SET` / `GET` / `EXPIRE` 동작 확인
- **기존 테스트 호환**: Redis 미기동 환경(CI H2 테스트)에서 기존 테스트가 깨지지 않아야 함. `@ConditionalOnProperty` 또는 프로필 분리로 격리

## 8) 오픈 질문

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q1 | 로컬 테스트 시 embedded Redis vs docker Redis? | (a) Testcontainers로 Redis 컨테이너 기동 / (b) embedded-redis 라이브러리 / (c) docker-compose의 Redis 사용 | @dohyeon / 2026-05-23 |
| Q2 | prod 배포 시 Redis 컨테이너를 backend-cd.yml에서 함께 관리할지, 별도 docker-compose로 분리할지 | (a) backend docker-compose에 포함 / (b) monitoring처럼 별도 compose | @dohyeon / 2026-05-23 |

## 9) 결정 로그

- 2026-05-22: 초안 작성 (status=draft)
