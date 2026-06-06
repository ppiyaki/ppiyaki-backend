---
feature: 무중단 배포 (nginx blue-green)
slug: zero-downtime-deploy
status: draft
owner: @goohong
scope: infra
related_issues: [452]
related_prs: []
last_reviewed: 2026-06-06
---

# 무중단 배포 (nginx blue-green)

## 1) 개요 (What / Why)
현재 CD는 `docker stop ppiyaki-server` → `docker rm` → `docker pull` → `docker run` 순서라,
기존 컨테이너를 먼저 내린 뒤 새 컨테이너를 띄운다. 그 사이 + 새 컨테이너의 Spring Boot 부팅 완료까지
**수십 초~1분가량 API가 끊긴다(connection refused).** 릴리즈마다 짧은 다운타임이 발생한다.

이 스펙은 **다운타임 없이** 새 버전을 배포하기 위해, 이미 운영 중인 호스트 nginx를 활용한
**blue-green 배포**로 CD를 전환하는 것을 다룬다. 대상 액터는 운영자(배포)와 최종 사용자(무중단 경험)다.

## 2) 사용자 시나리오
- (운영자) `develop → main` 릴리즈가 머지되면 CD가 새 색(color) 컨테이너를 띄우고, 헬스체크 통과 후
  nginx를 graceful reload 하여 트래픽을 전환한다. **기존 컨테이너는 전환 전까지 계속 서빙**한다.
- (사용자) 배포 중에도 앱 요청이 끊기지 않는다.
- (운영자) 새 컨테이너가 헬스체크에 실패하면 nginx를 전환하지 않고 기존 컨테이너를 그대로 유지한다
  (= 실패한 배포가 트래픽을 받지 않음, 무중단 보장).

## 3) 요구사항
### 기능 요구사항
- [ ] 배포 시 기존 컨테이너를 내리지 않고 **새 색 컨테이너를 다른 포트에 먼저 기동**한다.
- [ ] 새 컨테이너의 `/actuator/health`(관리 포트 8081)가 `UP` 이 될 때까지 폴링한다(타임아웃 有).
- [ ] 헬스 통과 시에만 nginx upstream을 새 색으로 바꾸고 **`nginx -t` 검증 후 graceful reload**한다.
- [ ] 전환 후 구 색 컨테이너를 graceful 종료(in-flight 요청 드레인)한다.
- [ ] 헬스 실패/타임아웃 시: nginx 전환하지 않고, 새(실패) 컨테이너만 제거하고 배포를 실패로 종료한다.
- [ ] 활성 색 판별이 재실행에도 안전하도록 **멱등**하게 동작한다.

### 비기능 요구사항
- **무중단**: nginx graceful reload(`SIGHUP`)로 기존 연결 유지. reload 전 `nginx -t` 필수.
- **자원**: 전환 순간 앱 JVM 2개가 잠깐 공존한다. 서버 RAM 3.8GB에 MySQL + 모니터링 5종이 공존하므로
  **각 앱 컨테이너에 heap/메모리 상한**을 두어 OOM을 방지한다 (§8 Q4).
- **보안**: 앱 포트·관리 포트는 모두 `127.0.0.1` 바인딩(외부 미노출). 공개 진입점은 nginx 443만 유지.
- **관측성**: 배포 결과 Discord 알림 유지. 전환/롤백 로그를 배포 로그에 남긴다.
- **무회귀**: SSE 스트리밍(`/chat` 등), 업로드 등 기존 nginx proxy 동작(헤더·타임아웃)을 그대로 보존한다.

## 4) 범위 / 비범위 (중요)
### 포함
- `.github/workflows/backend-cd.yml`의 SSH 배포 스크립트를 blue-green으로 교체
- 호스트 nginx의 upstream 간접화(`proxy_pass` → `upstream` 참조) 및 활성 색 전환 메커니즘
- 색깔별 컨테이너 포트/관리포트 규약, 헬스체크 폴링, 자동 롤백
- 일회성 서버 셋업 절차 문서화(현재 8080 컨테이너를 첫 blue로 재사용, 무중단 셋업)

### 제외 (Out of Scope)
- **다중 서버 / 오토스케일 / 오케스트레이터(K8s, Swarm) 도입** — 단일 호스트 유지
- **DB 스키마 마이그레이션의 무중단화** — release 직전 ALTER 절차는 기존 방식 유지([[2026-05-12-release-prod-ai]] 참조)
- **nginx의 컨테이너화** — 현재 시스템 패키지 nginx(1.18.0) 유지
- **TLS/도메인 변경** — 기존 Let's Encrypt(`ppiyaki.store`) 그대로
- **carbon copy 트래픽/카나리 비율 분배** — 100% 일괄 전환만(blue↔green)

## 5) 설계

### 5-1) 도메인 모델
인프라 변경이며 도메인 엔티티/컨텍스트는 건드리지 않는다.

### 5-2) API 엔드포인트
변경 없음. (`/actuator/health`는 기존 actuator 그대로 활용)

### 5-3) 외부 연동 / 운영 구조 (현황)
조사로 확인한 prod 진입 구조:
```text
[모바일 앱] → ppiyaki.store :443 (호스트 시스템 nginx 1.18.0, Let's Encrypt TLS 종료)
                   └ location / { proxy_pass http://localhost:8080; }  → ppiyaki-server (127.0.0.1:8080)
```
- 호스트 nginx가 이미 앞단에 존재 → **새 프록시 도입 불필요**. upstream 대상만 전환하면 됨.
- 관리 포트 8081은 현재 미publish → 색깔별로 `127.0.0.1`에 publish해 헬스체크에 사용.
- MySQL(`127.0.0.1:13306`)·Redis·모니터링(grafana 3000 등)은 별개 컨테이너로 영향 없음.

### 5-4) 데이터 흐름 / 시퀀스
```text
배포 트리거(main push)
  1. 활성 색 판별 (예: blue=8080 활성)
  2. 새 이미지 pull
  3. 비활성 색(green) 컨테이너 기동  (127.0.0.1:8082 app / 127.0.0.1:18082 mgmt)
  4. green /actuator/health 폴링 → UP 까지 (timeout 내)
       ├─ 실패 → green 제거, 배포 실패 종료 (nginx 그대로 = 무중단, blue 유지)
       └─ 성공 → 다음 단계
  5. nginx upstream 파일을 green 포트로 재작성 → `nginx -t` → `systemctl reload nginx` (graceful)
  6. 구 색(blue) graceful stop & rm (in-flight 드레인)
  7. 이미지 prune
```

nginx 간접화:
```nginx
# /etc/nginx/conf.d/ppiyaki-upstream.conf  (배포 스크립트가 재작성)
upstream ppiyaki_backend { server 127.0.0.1:8080; }   # ← 활성 색 포트

# /etc/nginx/sites-available/default (1회 변경)
location / { proxy_pass http://ppiyaki_backend; ... 기존 헤더/타임아웃 유지 ... }
```

### 5-5) DB 마이그레이션
없음.

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1 (`docs(infra)`): 본 Feature Spec 초안 — 합의용
- [ ] PR 2 (`chore(infra)`): `backend-cd.yml` blue-green 배포 스크립트 + (선택) 헬퍼 스크립트.
      **보호 영역(`.github/workflows/**`) → `needs-human-review` 라벨 필수**
- [ ] 서버 일회성 셋업: nginx upstream 간접화 + 활성 색 파일 생성 (무중단 reload). PR 아님 / 운영 작업으로 기록

## 7) 테스트 전략
- 배포 스크립트는 CI 단위 테스트 대상이 아니므로, **스테이징 성격의 검증**을 어떻게 할지 §8 Q5.
- 최소 검증: 전환 직후 `curl https://ppiyaki.store/actuator/health` 200 확인, 배포 중 별도 터미널에서
  1초 간격 헬스 폴링으로 무중단(연속 200) 실측.
- 롤백 경로: 일부러 부팅 실패하는 이미지로 green 기동 → nginx 미전환 + blue 유지 확인.

## 8) 오픈 질문
> 구현 전에 답이 나와야 하는 것들.

| # | 질문 | 선택지 | 담당/기한 |
|---|---|---|---|
| Q1 | 서버 nginx 일회성 셋업(upstream 간접화)을 누가/어떻게 | (a) 운영자가 수동 1회 적용 후 CD는 전환만 / (b) CD 스크립트가 멱등 셋업까지 자동 | @goohong |
| Q2 | 색깔별 포트 규약 | (a) blue=8080·green=8082, mgmt 18081·18082 (제안) / (b) 다른 값 | @goohong |
| Q3 | 헬스체크 폴링 타임아웃/간격 + 실패 시 동작 | (a) 2s 간격·최대 60s, 실패 시 자동 롤백(전환 안 함) (제안) / (b) 조정 | @goohong |
| Q4 | 전환 순간 앱 JVM 2개 공존 시 OOM 방지 | (a) 컨테이너 `--memory` + `-XX:MaxRAMPercentage`로 각 앱 heap 상한 (제안) / (b) 현 메모리로 충분(측정) / (c) swap 추가 | @goohong |
| Q5 | 첫 적용/검증 시점 | (a) 다음 정규 release 때 자연 적용 / (b) 빈 변경으로 1회 리허설 배포 | @goohong |

## 9) 결정 로그
- 2026-06-06: 초안 작성 (status=draft)
- 2026-06-06: 방식 = **호스트 nginx upstream 전환 blue-green** 확정. 사용자 선택(권장안). 이유: 호스트 nginx가 이미 TLS 종료 + 프록시 앞단으로 존재하여 추가 프록시 도입 없이 무중단 reload로 전환 가능.
- 2026-06-06: 진행 절차 = **Feature Spec 선합의 후 구현**(사용자 선택 B). 보호 영역 변경이라 권장 절차.
