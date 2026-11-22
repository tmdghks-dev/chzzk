#!/usr/bin/env bash
# 새 OCI 우분투(ARM) VM에서 한 번 실행: Docker(이미지 빌드용), K3s, cloudflared, coturn 설치와 방화벽 열기
# 사용: ssh ubuntu@<서버IP> 'bash -s' < scripts/oci-server-setup.sh
set -euo pipefail
sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq docker.io coturn netfilter-persistent iptables-persistent

# cloudflared (Cloudflare 공식 apt 저장소)
curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg | sudo tee /usr/share/keyrings/cloudflare-main.gpg >/dev/null
echo "deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared any main" \
  | sudo tee /etc/apt/sources.list.d/cloudflared.list >/dev/null
sudo apt-get update -qq && sudo apt-get install -y -qq cloudflared

# K3s 단일 노드 (Ingress는 cloudflared가 맡으므로 traefik은 끔. metrics-server·local-path는 기본 포함)
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik --write-kubeconfig-mode 644" sh -

# OCI 우분투 이미지는 iptables가 22번 외에는 막는다. TURN 포트만 연다 (웹은 터널이라 열 필요 없음)
for r in "-p udp --dport 3478" "-p tcp --dport 3478" "-p udp --dport 49160:49200"; do
  sudo iptables -C INPUT $r -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 5 $r -j ACCEPT
done
sudo netfilter-persistent save

kubectl get nodes
