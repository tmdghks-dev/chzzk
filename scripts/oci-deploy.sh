#!/usr/bin/env bash
# OCI 서버(K3s)에 치지직 배포: 소스 복사 → 서버에서 ARM 이미지 빌드 → K3s에 넣기 → 파드 교체
# 사용: ./scripts/oci-deploy.sh            (서버 주소는 OCI_HOST로 바꿀 수 있음)
set -euo pipefail
cd "$(dirname "$0")/.."
HOST=${OCI_HOST:-ubuntu@129.221.216.69}

rsync -az --delete --exclude build --exclude .gradle --exclude data --exclude mobile --exclude desktop \
  --exclude node_modules --exclude .git --exclude .idea ./ "$HOST":~/chzzk/
ssh "$HOST" 'set -e
cd ~/chzzk
sudo docker build -q -t chzzk:local . >/dev/null
sudo docker save chzzk:local | sudo k3s ctr images import - >/dev/null
kubectl apply -f k8s/postgres.yaml -f k8s/oci.yaml >/dev/null
kubectl rollout restart deploy/chzzk deploy/chzzk-live >/dev/null
kubectl rollout status deploy/chzzk --timeout=300s
kubectl rollout status deploy/chzzk-live --timeout=300s'
curl -s https://chzzk.tmdghks.dev/actuator/health; echo
