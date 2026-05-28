#!/usr/bin/env bash
#
# NCP 매니지드 MySQL → 압축된 sql.gz 덤프 생성.
# backend 서버(ssh ppiyaki-prod)에서 실행할 것을 가정.
#
# 사용법:
#   ./dump-from-ncp.sh [OUTPUT_PATH]
#   기본 OUTPUT_PATH = /tmp/ppiyaki-ncp-dump-<timestamp>.sql.gz
#
# 동작:
#   1) 실행 중인 ppiyaki-server 컨테이너의 env에서 DB_URL/USERNAME/PASSWORD 추출
#   2) DB_URL을 jdbc:mysql://HOST:PORT/DB 패턴에서 분해
#   3) mysqldump --single-transaction --triggers --routines --events --quick → gzip
#   4) 결과 경로와 row count 요약 출력
#
# 보안: credential은 임시 .my.cnf로만 노출되며 EXIT trap으로 즉시 삭제.

set -euo pipefail

OUTPUT="${1:-/tmp/ppiyaki-ncp-dump-$(date +%Y%m%d-%H%M%S).sql.gz}"

CID=$(sudo docker ps --filter name=ppiyaki-server -q | head -1)
if [[ -z "$CID" ]]; then
    echo "ERROR: ppiyaki-server 컨테이너를 찾을 수 없습니다." >&2
    exit 1
fi

ENV_FILE=$(mktemp)
CNF_FILE=$(mktemp)
trap 'rm -f "$ENV_FILE" "$CNF_FILE"' EXIT

sudo docker inspect "$CID" --format '{{range .Config.Env}}{{println .}}{{end}}' > "$ENV_FILE"

URL=$(grep '^DB_URL=' "$ENV_FILE" | cut -d= -f2-)
DBUSER=$(grep '^DB_USERNAME=' "$ENV_FILE" | cut -d= -f2-)
DBPASS=$(grep '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)

if [[ -z "$URL" || -z "$DBUSER" || -z "$DBPASS" ]]; then
    echo "ERROR: ppiyaki-server 컨테이너 env에서 DB_URL/USERNAME/PASSWORD 추출 실패." >&2
    exit 1
fi

# jdbc:mysql://HOST:PORT/DB?params -> HOST, PORT, DB 분해
HOST=$(echo "$URL" | sed -E 's|jdbc:mysql://([^:/]+).*|\1|')
PORT=$(echo "$URL" | sed -nE 's|jdbc:mysql://[^:/]+:([0-9]+).*|\1|p')
PORT="${PORT:-3306}"
DBNAME=$(echo "$URL" | sed -nE 's|jdbc:mysql://[^/]+/([^?]+).*|\1|p')

if [[ -z "$HOST" || -z "$DBNAME" ]]; then
    echo "ERROR: DB_URL 파싱 실패: $URL" >&2
    exit 1
fi

{
    echo "[client]"
    echo "host=$HOST"
    echo "port=$PORT"
    echo "user=$DBUSER"
    echo "password=$DBPASS"
} > "$CNF_FILE"
chmod 600 "$CNF_FILE"

echo "Source: $HOST:$PORT/$DBNAME"
echo "Target: $OUTPUT"

# Pre-dump row count snapshot
echo "=== row counts before dump ==="
mysql --defaults-extra-file="$CNF_FILE" "$DBNAME" -N -e "
    SELECT table_name, table_rows
    FROM information_schema.tables
    WHERE table_schema = '$DBNAME'
    ORDER BY table_name;
"

# Dump
mysqldump --defaults-extra-file="$CNF_FILE" \
    --single-transaction \
    --triggers \
    --routines \
    --events \
    --quick \
    --set-gtid-purged=OFF \
    --no-tablespaces \
    "$DBNAME" \
    | gzip -9 > "$OUTPUT"

DUMP_SIZE=$(du -h "$OUTPUT" | cut -f1)
echo
echo "Dump 완료: $OUTPUT ($DUMP_SIZE)"
echo "다음 단계: ./restore-to-docker.sh $OUTPUT"
