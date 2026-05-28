# DB Migration Scripts

NCP 매니지드 MySQL → backend 서버 docker MySQL 데이터 이전용 스크립트.

상세 spec: [`docs/features/db-self-hosted-migration.md`](../../docs/features/db-self-hosted-migration.md)

## 사전 조건

- backend 서버에 ssh 접속 가능 (`ssh ppiyaki-prod`)
- `ppiyaki-server`, `ppiyaki-mysql` 두 컨테이너가 실행 중 (`docker compose up -d mysql`로 docker MySQL 미리 기동)
- 서버에 mysql client 설치 (`apt install mysql-client-core-8.0` 또는 이미 설치되어 있을 가능성)

## 실행 순서

backend 서버에서 차례로 실행:

```bash
# 1. NCP 매니지드 DB → 압축 덤프
./dump-from-ncp.sh
# → /tmp/ppiyaki-ncp-dump-YYYYMMDD-HHMMSS.sql.gz 생성

# 2. 덤프 → docker MySQL 복원
./restore-to-docker.sh /tmp/ppiyaki-ncp-dump-YYYYMMDD-HHMMSS.sql.gz

# 3. row count 검증
./verify-counts.sh
```

## 주의사항

- credential은 컨테이너 env에서 추출하며 임시 `.my.cnf` 파일에만 노출. EXIT trap으로 즉시 삭제.
- `restore-to-docker.sh`는 기존 데이터를 덮어쓴다. docker MySQL이 빈 상태인지 확인 후 실행.
- `verify-counts.sh`의 row count는 `information_schema.tables` 통계값이라 ±10% 오차 가능. 정확한 검증이 필요하면 각 테이블 `SELECT COUNT(*)`를 별도로 비교.
- 다운타임 무제한 허용 (spec §9 결정). 마이그레이션 중 ppiyaki-server는 살아있어도 되지만, 사진 업로드/복약 인증 등 쓰기 트래픽이 발생하면 NCP에 남은 데이터와 docker MySQL 데이터가 어긋남. 가능하면 ppiyaki-server를 일시 중단 후 진행.

## 롤백

마이그레이션 후 backend 연결 변경 전이면 NCP DB는 그대로 살아있다. PR 3 머지 전 단계에서 문제 발견 시 docker MySQL만 정리하고 NCP를 계속 사용.
