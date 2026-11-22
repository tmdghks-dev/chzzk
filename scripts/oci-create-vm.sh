#!/usr/bin/env bash
# OCI Always Free VM 만들기: "Out of host capacity"면 자리가 날 때까지 자동 재시도한다 (OCI CLI + API 키 필요)
# 실행 예: TENANCY=... AD=... SUBNET=... IMAGE_ID=... OCPUS=2 MEM=12 caffeinate -i bash scripts/oci-create-vm.sh
set -u
export SUPPRESS_LABEL_WARNING=True PYTHONWARNINGS=ignore

T=${TENANCY:?TENANCY(테넌시 OCID)를 넣어 주세요}
AD=${AD:?가용성 도메인 이름을 넣어 주세요 (oci iam availability-domain list)}
SUBNET=${SUBNET:?공개 서브넷 OCID를 넣어 주세요}
IMAGE=${IMAGE_ID:?우분투 aarch64 이미지 OCID를 넣어 주세요}
INTERVAL=${INTERVAL:-180}   # 재시도 간격(초)
NAME=${NAME:-tmdghks-server}
SHAPE=${SHAPE:-VM.Standard.A1.Flex}
LOG=${LOG:-oci-create-vm.log}

# UNTIL=09:00 처럼 주면 그 시각까지만 시도하고, 못 잡으면 Mac을 잠자기로 보낸다.
DEADLINE=""
if [ -n "${UNTIL:-}" ]; then
  DEADLINE=$(date -j -f "%Y-%m-%d %H:%M" "$(date +%F) $UNTIL" +%s)
  [ "$DEADLINE" -le "$(date +%s)" ] && DEADLINE=$((DEADLINE + 86400))  # 이미 지난 시각이면 내일
  echo "$(date '+%F %T') $(date -r "$DEADLINE" '+%m/%d %H:%M')까지 시도해요" | tee -a "$LOG"
fi

n=0
while true; do
  if [ -n "$DEADLINE" ] && [ "$(date +%s)" -ge "$DEADLINE" ]; then
    echo "$(date '+%F %T') 마감 시각까지 자리를 못 잡았어요. Mac을 잠자기로 보내요." | tee -a "$LOG"
    pmset sleepnow
    break
  fi
  n=$((n + 1))
  # 이미 만들어진 서버가 있으면 멈춤 (다른 창에서 성공했을 때 대비)
  existing=$(oci compute instance list -c "$T" --display-name "$NAME" --no-retry \
    --query 'data[?"lifecycle-state"!=`TERMINATED`] | [0].id' --raw-output 2>/dev/null)
  if [ -n "$existing" ] && [ "$existing" != "null" ]; then
    echo "$(date '+%F %T') 이미 서버가 있어요: $existing" | tee -a "$LOG"
    break
  fi

  out=$(oci compute instance launch --no-retry -c "$T" --availability-domain "$AD" \
    --display-name "$NAME" --shape "$SHAPE" \
    $([ "$SHAPE" = VM.Standard.A1.Flex ] && echo --shape-config "{\"ocpus\":${OCPUS:-4},\"memoryInGBs\":${MEM:-24}}") \
    --image-id "$IMAGE" --subnet-id "$SUBNET" --assign-public-ip true \
    --ssh-authorized-keys-file "$HOME/.ssh/id_ed25519.pub" \
    --query 'data.id' --raw-output 2>&1)

  if [[ "$out" == ocid1.instance.* ]]; then
    echo "$(date '+%F %T') [$n] 🎉 생성 성공: $out" | tee -a "$LOG"
    ip=""
    for _ in $(seq 1 30); do   # 공인 IP는 VNIC가 붙은 뒤에 나온다
      ip=$(oci compute instance list-vnics --instance-id "$out" --query 'data[0]."public-ip"' --raw-output 2>/dev/null)
      [ -n "$ip" ] && [ "$ip" != null ] && break
      sleep 10
    done
    echo "$(date '+%F %T') 공인 IP: $ip   (ssh ubuntu@$ip)" | tee -a "$LOG"
    break
  fi

  wait=$INTERVAL
  if   echo "$out" | grep -qi "capacity";      then why="자리 없음"
  elif echo "$out" | grep -qi "TooManyRequests\|429"; then why="요청 너무 많음"; wait=$((INTERVAL * 2))
  else why="기타 오류: $(echo "$out" | grep -o '"message": "[^"]*"' | head -1)"; fi
  echo "$(date '+%F %T') [$n] $why → ${wait}초 뒤 다시" | tee -a "$LOG"
  sleep "$wait"
done
