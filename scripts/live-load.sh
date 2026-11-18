#!/usr/bin/env bash
# 라이브 시그널링 부하 테스트: 테스트용 방송 생성 → k6 실행(서버 CPU/메모리 기록) → 방송 종료
# 사용: ./scripts/live-load.sh   (앱이 localhost:8091 에서 돌고 있어야 함)
set -euo pipefail
cd "$(dirname "$0")/.."
ulimit -n 65536 2>/dev/null || ulimit -n 10240

BASE=http://localhost:8091
LOCATION=$(curl -s -o /dev/null -w '%{redirect_url}' -X POST "$BASE/lives" --data-urlencode "title=부하테스트")
LIVE_ID=$(echo "$LOCATION" | sed -E 's#.*/lives/([0-9]+)/broadcast.*#\1#')
KEY=$(echo "$LOCATION" | sed -E 's#.*key=##')
echo "테스트 방송 id=$LIVE_ID"

PID=$(lsof -ti tcp:8091 -sTCP:LISTEN)
mkdir -p docs
( while true; do echo "$(date +%T) cpu=$(ps -o %cpu= -p "$PID" | xargs)% mem=$(( $(ps -o rss= -p "$PID") / 1024 ))MB"; sleep 5; done ) > docs/live-load-cpu.txt &
MON=$!

k6 run --summary-export=docs/live-load-summary.json -e LIVE_ID="$LIVE_ID" -e KEY="$KEY" load/k6-live.js 2>&1 | tee docs/live-load-k6.txt || true

kill $MON
curl -s -o /dev/null -X POST "$BASE/lives/$LIVE_ID/end" --data-urlencode "key=$KEY"
echo "테스트 방송 $LIVE_ID 종료"
