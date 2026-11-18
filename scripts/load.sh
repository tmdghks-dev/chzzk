#!/usr/bin/env bash
# k6로 2분간 부하를 건 뒤, 부하가 끝나고 줄어드는 것까지 HPA·파드 수를 10초마다 기록한다
set -euo pipefail
cd "$(dirname "$0")/.."
CTX=kind-chzzk
k() { kubectl --context "$CTX" "$@"; }
LOG=${1:-docs/scale-log.txt}
mkdir -p "$(dirname "$LOG")"

k create configmap k6-script --from-file=load/k6-load.js --dry-run=client -o yaml | k apply -f - >/dev/null
k delete job k6-load --ignore-not-found >/dev/null
k apply -f k8s/k6-job.yaml >/dev/null

: > "$LOG"
for i in $(seq 1 36); do   # 6분 기록 (부하 2분 + 줄어드는 시간)
  line="$(date +%H:%M:%S) cpu=$(k get hpa chzzk -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}')% replicas=$(k get hpa chzzk -o jsonpath='{.status.currentReplicas}') ready=$(k get pods -l app=chzzk --no-headers | grep -c '1/1.*Running' || true) k6=$(k get job k6-load -o jsonpath='{.status.active}' 2>/dev/null | sed 's/^$/done/')"
  echo "$line" | tee -a "$LOG"
  sleep 10
done
