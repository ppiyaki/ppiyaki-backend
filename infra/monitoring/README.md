# Monitoring Stack (Phase 2)

Prometheus + Grafana docker compose 스택. 같은 NCP 서버에 백엔드와 함께 실행.

## 구성

- **Prometheus** (`prom/prometheus:v3.0.1`) — 백엔드 `/actuator/prometheus` 엔드포인트를 15초 간격으로 scrape. 30일 보관.
- **Grafana** (`grafana/grafana:11.4.0`) — 시각화. 3000 포트 외부 노출 (admin 패스워드 인증).
- 백엔드와 같은 docker network (`ppiyaki-monitoring`)에 join → `ppiyaki-server:8081/actuator/prometheus` scrape.
- **Dashboard provisioning** — `grafana/provisioning/dashboards/json/*.json`을 자동 로드 (folder: `ppiyaki`). 현재 `ppiyaki-overview` 한 개.

## 최초 셋업 (prod, 1회)

### 1) docker network 생성

```bash
sudo docker network create ppiyaki-monitoring
```

### 2) 기존 백엔드 컨테이너를 network에 join

(backend-cd.yml이 이미 `--network ppiyaki-monitoring`을 추가했으므로, 다음 release deploy 시 자동 적용. 기존 컨테이너는 재시작 전까지는 default network.)

선택: 즉시 적용하려면

```bash
sudo docker network connect ppiyaki-monitoring ppiyaki-server
```

### 3) compose 파일 prod에 배치

```bash
# 로컬에서 prod로 복사
scp -i ~/workspace/secret/ppiyaki-key.pem -r infra/monitoring ubuntu@211.188.48.217:~/
```

### 4) Grafana admin 패스워드 설정

prod 서버에서:

```bash
cd ~/monitoring
echo "GRAFANA_ADMIN_PASSWORD=<강한_패스워드>" > .env
chmod 600 .env
```

### 5) 스택 기동

```bash
sudo docker compose --env-file .env up -d
```

### 6) 접속 검증

- Prometheus 타겟 상태: prod localhost로 `sudo docker exec ppiyaki-prometheus wget -qO- http://localhost:9090/api/v1/targets | head`
- Grafana: 브라우저로 `http://211.188.48.217:3000` → admin/<패스워드> 로그인

## 일상 운영

```bash
# 스택 상태
sudo docker compose ps

# 재시작
sudo docker compose restart

# 종료 (데이터는 volume에 유지)
sudo docker compose down

# Prometheus 설정 reload (재시작 없이)
sudo docker exec ppiyaki-prometheus kill -HUP 1
```

## 알려진 제약

- **백업 없음** (Phase 2 범위 외). Prometheus tsdb / Grafana 설정은 docker volume에만. 서버 장애 시 손실.
- **HA 없음**. 단일 서버.
- **외부 노출 보안** = admin 패스워드. IP allowlist / VPN은 별도 옵션.
- 백엔드 컨테이너가 `ppiyaki-monitoring` network에 join한 후에만 scrape 동작. 미연결 상태에선 target down.

## 대시보드 갱신

대시보드 JSON을 수정한 후 prod 적용:

```bash
# 로컬에서 prod로 dashboards 디렉토리 동기화
scp -i ~/workspace/secret/ppiyaki-key.pem -r infra/monitoring/grafana/provisioning ubuntu@211.188.48.217:~/monitoring/grafana/

# Grafana 재시작 (provisioning은 30s 간격 reload 설정이라 재시작 없이도 반영되지만, datasource 변경 시엔 필요)
ssh -i ~/workspace/secret/ppiyaki-key.pem ubuntu@211.188.48.217 'cd ~/monitoring && sudo docker compose restart grafana'
```

## Phase 3 (예정/옵션)

- 알림 (Alertmanager + Discord/Slack webhook)
- 백업 (Prometheus snapshot → NCP Object Storage)
- ~~대시보드 .json provisioning (JVM, HTTP, 캐시 적중률, DB connection)~~ ✅ done (`ppiyaki-overview`)
