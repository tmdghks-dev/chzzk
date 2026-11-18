#!/usr/bin/env bash
# kind 클러스터 생성 → 이미지 빌드·로드 → Secret → PostgreSQL → metrics-server → chzzk(2개) + HPA
set -euo pipefail
cd "$(dirname "$0")/.."
CTX=kind-chzzk
k() { kubectl --context "$CTX" "$@"; }

kind get clusters | grep -qx chzzk || kind create cluster --name chzzk --config k8s/kind-config.yaml

docker build -t chzzk:local .
kind load docker-image chzzk:local --name chzzk

# DB 비밀번호는 처음 한 번 랜덤으로 만들고, 토스 키는 환경변수가 있을 때만 넣는다
if ! k get secret chzzk-secrets >/dev/null 2>&1; then
  args=(--from-literal=DB_PASSWORD="$(openssl rand -hex 16)")
  [ -n "${TOSS_CLIENT_KEY:-}" ] && args+=(--from-literal=TOSS_CLIENT_KEY="$TOSS_CLIENT_KEY")
  [ -n "${TOSS_SECRET_KEY:-}" ] && args+=(--from-literal=TOSS_SECRET_KEY="$TOSS_SECRET_KEY")
  k create secret generic chzzk-secrets "${args[@]}"
fi

k apply -f k8s/postgres.yaml
k rollout status deploy/postgres --timeout=180s

# HPA가 CPU 사용률을 읽으려면 metrics-server가 필요하다 (kind는 kubelet 인증서 검사를 끈다)
k apply -f https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.7.2/components.yaml
k -n kube-system patch deploy metrics-server --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' 2>/dev/null || true

k apply -f k8s/chzzk.yaml
k rollout restart deploy/chzzk >/dev/null   # 이미지를 다시 빌드했을 때 새 이미지로 교체
k rollout status deploy/chzzk --timeout=300s

echo
echo "배포 완료: http://localhost:8092/broadcasts"
k get pods,hpa
