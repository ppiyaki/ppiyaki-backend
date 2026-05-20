#!/usr/bin/env bash
#
# NCP 매니지드 MySQL과 backend 서버 docker MySQL의 row count를 비교 검증.
# backend 서버(ssh ppiyaki-prod)에서 실행할 것을 가정.
#
# 사용법:
#   ./verify-counts.sh
#
# 동작:
#   1) ppiyaki-server env에서 NCP 접속 정보 추출
#   2) ppiyaki-mysql env에서 local 접속 정보 추출
#   3) 두 DB에서 information_schema.tables의 table_rows를 가져와 diff
#   4) 차이가 있는 테이블만 표시 (없으면 OK 메시지)
#
# 참고: information_schema.tables.table_rows는 통계값이라 ±10% 오차 가능.
#       정확한 검증이 필요하면 각 테이블 SELECT COUNT(*)를 별도로 비교할 것.

set -euo pipefail

# === NCP 접속 정보 추출 ===
SERVER_CID=$(sudo docker ps --filter name=ppiyaki-server -q | head -1)
if [[ -z "$SERVER_CID" ]]; then
    echo "ERROR: ppiyaki-server 컨테이너를 찾을 수 없습니다." >&2
    exit 1
fi

NCP_ENV=$(mktemp)
NCP_CNF=$(mktemp)
LOCAL_CNF=$(mktemp)
trap 'rm -f "$NCP_ENV" "$NCP_CNF" "$LOCAL_CNF"' EXIT

sudo docker inspect "$SERVER_CID" --format '{{range .Config.Env}}{{println .}}{{end}}' > "$NCP_ENV"
NCP_URL=$(grep '^DB_URL=' "$NCP_ENV" | cut -d= -f2-)
NCP_HOST=$(echo "$NCP_URL" | sed -E 's|jdbc:mysql://([^:/]+).*|\1|')
NCP_PORT=$(echo "$NCP_URL" | sed -nE 's|jdbc:mysql://[^:/]+:([0-9]+).*|\1|p')
NCP_PORT="${NCP_PORT:-3306}"
NCP_DB=$(echo "$NCP_URL" | sed -nE 's|jdbc:mysql://[^/]+/([^?]+).*|\1|p')

{
    echo "[client]"
    echo "host=$NCP_HOST"
    echo "port=$NCP_PORT"
    echo "user=$(grep '^DB_USERNAME=' "$NCP_ENV" | cut -d= -f2-)"
    echo "password=$(grep '^DB_PASSWORD=' "$NCP_ENV" | cut -d= -f2-)"
} > "$NCP_CNF"
chmod 600 "$NCP_CNF"

# === Local docker 접속 정보 추출 ===
MYSQL_CID=$(sudo docker ps --filter name=ppiyaki-mysql -q | head -1)
if [[ -z "$MYSQL_CID" ]]; then
    echo "ERROR: ppiyaki-mysql 컨테이너를 찾을 수 없습니다." >&2
    exit 1
fi

LOCAL_ENV=$(sudo docker inspect "$MYSQL_CID" --format '{{range .Config.Env}}{{println .}}{{end}}')
LOCAL_ROOT_PW=$(echo "$LOCAL_ENV" | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)
LOCAL_DB=$(echo "$LOCAL_ENV" | grep '^MYSQL_DATABASE=' | cut -d= -f2-)
LOCAL_DB="${LOCAL_DB:-ppiyaki}"

# === row count 가져오기 ===
NCP_COUNTS=$(mysql --defaults-extra-file="$NCP_CNF" -N -e "
    SELECT table_name, table_rows
    FROM information_schema.tables
    WHERE table_schema = '$NCP_DB'
    ORDER BY table_name;
")

LOCAL_COUNTS=$(sudo docker exec -e "MYSQL_PWD=$LOCAL_ROOT_PW" "$MYSQL_CID" mysql -u root -N -e "
    SELECT table_name, table_rows
    FROM information_schema.tables
    WHERE table_schema = '$LOCAL_DB'
    ORDER BY table_name;
")

# === diff ===
echo "=== NCP ($NCP_DB) vs docker ($LOCAL_DB) row counts ==="
printf "%-50s %12s %12s %s\n" "table" "ncp_rows" "local_rows" "match"
echo "-----------------------------------------------------------------------------------"

MISMATCH=0
while IFS=$'\t' read -r TBL NCP_ROWS; do
    LOCAL_ROWS=$(echo "$LOCAL_COUNTS" | awk -v t="$TBL" '$1==t {print $2}')
    LOCAL_ROWS="${LOCAL_ROWS:-MISSING}"
    if [[ "$NCP_ROWS" == "$LOCAL_ROWS" ]]; then
        MARK="OK"
    else
        MARK="MISMATCH"
        MISMATCH=$((MISMATCH+1))
    fi
    printf "%-50s %12s %12s %s\n" "$TBL" "$NCP_ROWS" "$LOCAL_ROWS" "$MARK"
done <<< "$NCP_COUNTS"

echo
if [[ "$MISMATCH" -eq 0 ]]; then
    echo "모든 테이블 row count 일치."
else
    echo "$MISMATCH개 테이블 불일치. 통계값이라 ±10% 오차 가능 — 의심되는 테이블은 SELECT COUNT(*)로 정확히 검증할 것."
fi
