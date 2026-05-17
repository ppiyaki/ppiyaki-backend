# 관측성 (Observability)

> Spring Boot Actuator + Micrometer 기반. Phase 1은 백엔드 endpoint 노출만, Phase 2(시각화 인프라)는 별도.

## 1) 관리 포트와 노출 endpoint

- **포트**: `MANAGEMENT_PORT` 환경변수(기본 `8081`). 메인 8080과 분리.
- **공개 endpoint**: `health`, `info`, `metrics`, `prometheus`
- **외부 노출**: `backend-cd.yml`이 8080만 publish하므로 8081은 **prod에서 외부 접근 불가**. SSH 접속자(또는 같은 Docker network 내 컨테이너)만 호출 가능.
- Security 설정상 `/actuator/**`는 인증 우회(permitAll). 외부 미노출이 보안 경계.

## 2) 주요 메트릭

### 캐시 hit/miss
| Counter | 태그 | 의미 |
|---|---|---|
| `ppiyaki.cache.hits` | `cache=mfds` | MFDS API 응답 캐시 적중 |
| `ppiyaki.cache.misses` | `cache=mfds` | MFDS API 응답 캐시 미스 (외부 호출 발생) |
| `ppiyaki.cache.hits` | `cache=drug_info` | DrugInfo API 응답 캐시 적중 |
| `ppiyaki.cache.misses` | `cache=drug_info` | DrugInfo API 응답 캐시 미스 |
| `ppiyaki.cache.hits` | `cache=dur_check` | DUR 점검 결과 캐시 적중 (24h, DB-backed) |
| `ppiyaki.cache.misses` | `cache=dur_check` | DUR 점검 결과 캐시 미스 (실측 트리거) |

적중률 = `hits / (hits + misses)` per cache tag.

### 자동 노출 메트릭 (Micrometer)
- `http.server.requests` (히스토그램 활성)
- `jvm.memory.used`, `jvm.gc.*`
- `hikaricp.connections.*`
- `system.cpu.usage`

전체 목록은 `/actuator/metrics`.

## 3) prod 조회 방법 (Phase 1)

```bash
ssh -i ~/workspace/secret/ppiyaki-key.pem -p 22 ubuntu@211.188.48.217
sudo docker exec ppiyaki-server curl -s http://localhost:8081/actuator/prometheus | grep ppiyaki_cache
```

또는 메트릭 단건:
```bash
sudo docker exec ppiyaki-server curl -s "http://localhost:8081/actuator/metrics/ppiyaki.cache.hits?tag=cache:mfds"
```

## 4) Phase 2 (예정)

- Prometheus pull-style scrape (호스팅 위치 TBD)
- Grafana 대시보드 (캐시 적중률, HTTP latency p95, JVM, DB)
- 외부 노출 시 actuator 인증 추가 필요 (bearer token / basic auth / IP allowlist)
