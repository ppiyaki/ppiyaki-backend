#!/usr/bin/env bash
#
# 압축된 sql.gz 덤프를 backend 서버의 docker MySQL(ppiyaki-mysql)에 import.
# backend 서버(ssh ppiyaki-prod)에서 실행할 것을 가정.
#
# 사용법:
#   ./restore-to-docker.sh DUMP_PATH
#
# 동작:
#   1) ppiyaki-mysql 컨테이너 healthy 상태 확인
#   2) MYSQL_ROOT_PASSWORD를 컨테이너 env에서 추출 (혹은 외부 env에서 받음)
#   3) gzip -dc | docker exec mysql 로 import (DROP TABLE → CREATE → INSERT 전체)
#   4) import 직후 row count 출력
#
# 주의: import는 기존 데이터를 덮어쓴다. 빈 DB가 아니면 사전에 백업 필요.

set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "사용법: $0 DUMP_PATH" >&2
    exit 1
fi

DUMP="$1"
if [[ ! -f "$DUMP" ]]; then
    echo "ERROR: 덤프 파일을 찾을 수 없음: $DUMP" >&2
    exit 1
fi

CID=$(sudo docker ps --filter name=ppiyaki-mysql -q | head -1)
if [[ -z "$CID" ]]; then
    echo "ERROR: ppiyaki-mysql 컨테이너가 실행 중이 아닙니다." >&2
    echo "먼저 'docker compose up -d mysql'을 실행하세요." >&2
    exit 1
fi

# Healthcheck 통과 대기 (최대 60초)
echo "ppiyaki-mysql healthy 대기 중..."
for i in {1..30}; do
    STATUS=$(sudo docker inspect "$CID" --format '{{.State.Health.Status}}' 2>/dev/null || echo "unknown")
    if [[ "$STATUS" == "healthy" ]]; then
        break
    fi
    sleep 2
done

if [[ "$STATUS" != "healthy" ]]; then
    echo "ERROR: ppiyaki-mysql healthcheck 실패 (current=$STATUS). 컨테이너 로그 확인 필요." >&2
    exit 1
fi

# 컨테이너 env에서 root password + db name 추출
ROOT_PW=$(sudo docker inspect "$CID" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)
DBNAME=$(sudo docker inspect "$CID" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_DATABASE=' | cut -d= -f2-)
DBNAME="${DBNAME:-ppiyaki}"

if [[ -z "$ROOT_PW" ]]; then
    echo "ERROR: MYSQL_ROOT_PASSWORD를 컨테이너 env에서 추출 실패." >&2
    exit 1
fi

echo "Source: $DUMP ($(du -h "$DUMP" | cut -f1))"
echo "Target: ppiyaki-mysql / $DBNAME"

# import
gzip -dc "$DUMP" | sudo docker exec -i -e "MYSQL_PWD=$ROOT_PW" "$CID" mysql -u root "$DBNAME"

echo
echo "=== row counts after import ==="
sudo docker exec -e "MYSQL_PWD=$ROOT_PW" "$CID" mysql -u root -N -e "
    SELECT table_name, table_rows
    FROM information_schema.tables
    WHERE table_schema = '$DBNAME'
    ORDER BY table_name;
"

echo
echo "Restore 완료. 다음 단계: ./verify-counts.sh"
