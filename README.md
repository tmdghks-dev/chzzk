# 치지직 클론 — ① 녹화방송 · ③ 인증·인가와 댓글 · ④ 도네이션 · ⑤ 로컬 오토스케일

Spring Boot 4.1 · Java 25 · Thymeleaf · Spring Data JPA · H2(파일 DB) · 토스페이먼츠 결제위젯(테스트 모드)

5단계(클러스터 모드): Docker 이미지 · kind(로컬 Kubernetes) · PostgreSQL · Spring Session JDBC · HPA · k6 — [아래 5단계](#5단계-로컬-오토스케일) 참고. 프로필 없이 실행하면 위 H2 구성 그대로입니다.

## 실행 방법

1. JDK 25를 설치합니다.
2. `src/main/resources/application-secret.properties` 파일을 만들고 토스 **결제위젯 연동 키(테스트)** 를 넣습니다. 이 파일은 `.gitignore`에 들어 있어 커밋되지 않습니다.
3. 실행합니다.
   ```bash
   ./gradlew bootRun
   ```
4. 브라우저에서 <http://localhost:8091/broadcasts> 를 엽니다.

### 환경변수 / 설정 예시

`application-secret.properties` 대신 환경변수로 넣어도 됩니다.

```properties
# application-secret.properties
toss.client-key=test_gck_xxxxxxxxxxxxxxxxxxxx
toss.secret-key=test_gsk_xxxxxxxxxxxxxxxxxxxx
```

```bash
export TOSS_CLIENT_KEY=test_gck_xxxxxxxxxxxxxxxxxxxx
export TOSS_SECRET_KEY=test_gsk_xxxxxxxxxxxxxxxxxxxx
```

### 외부 공개 (Cloudflare Tunnel)

```bash
brew install cloudflared
cloudflared tunnel --url http://localhost:8091
```

출력되는 `https://<임의이름>.trycloudflare.com` 주소로 접속합니다. 결제 성공·실패 주소는 `location.origin` 기준으로 만들어지므로 터널 주소에서도 그대로 동작합니다.

## 구현 범위

| 단계 | 내용 | 주요 코드 |
| --- | --- | --- |
| ① 녹화방송 | 제목 + 영상 URL 등록, 목록, 상세에서 `<video>` 재생, H2 파일 DB에 저장(재시작 후 유지) | `broadcast/Broadcast`, `BroadcastController` |
| ③ 인증·인가 | 회원가입·로그인·로그아웃(HttpSession, BCrypt 암호화) | `member/*` |
| ③ 권한 | 방송 등록은 로그인 필요, 방송 삭제는 **서버에서 소유자 검사**(아니면 403) | `BroadcastController.create/delete` |
| ③ 댓글 | 로그인 사용자만 작성(비로그인은 서버에서 401), 방송별 댓글 목록 | `comment/*` |
| ④ 도네이션 | 로그인 사용자가 방송자에게 금액을 정해 후원, 토스 결제위젯 테스트 결제 | `donation/*`, `templates/donation/checkout.html` |
| ④ 서버 확정 | 성공 리다이렉트 후 서버가 주문서와 금액·수신자를 비교하고, 토스 **결제 승인 API**를 호출해 응답(`DONE`·금액·주문번호)까지 확인한 뒤에만 `DONE`으로 확정 | `DonationController.success`, `TossClient` |
| ④ 중복 방지 | 주문번호(`orderId`)·결제키(`paymentKey`) DB unique, 이미 `DONE`인 주문은 다시 처리하지 않음 | `Donation`, `DonationController.success` |
| ④ 내역·합계 | 방송 상세에 확정된 후원 목록과 합계 표시 | `BroadcastController.detail`, `broadcast/detail.html` |

- ② 휴대폰 라이브 송출은 이번 제출 범위에서 제외했습니다.
- **공개 범위**: 방송 목록·시청·댓글 읽기·후원 내역은 로그인 없이 볼 수 있습니다. 방송 등록·삭제, 댓글 작성, 후원은 로그인이 필요합니다.

### 후원 상태

| 상태 | 의미 |
| --- | --- |
| `READY` | 주문서 생성(결제 전). 금액·수신자를 서버가 기록 |
| `DONE` | 토스 승인까지 확인된 확정 후원. 내역·합계에 포함 |
| `FAILED` | 결제 실패 또는 금액 불일치 |
| `CANCELED` | 사용자가 결제창을 닫음 |

## 직접 확인 절차

1. **녹화방송**: 로그인 → `+ 방송 등록` → 제목과 영상 URL(예: `https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4`) 입력 → 상세에서 재생 → 앱 재시작 후에도 목록에 남아 있는지 확인
2. **권한**: 계정 A로 방송 등록 → 계정 B로 로그인해 같은 방송에서 삭제 버튼이 안 보이는지 확인. 버튼 없이 직접 요청해도 403인지 확인
   ```bash
   curl -i -X POST http://localhost:8091/broadcasts/1/delete   # 비로그인 → 403
   ```
3. **댓글**: 비로그인 상태에서는 입력칸이 없고, 직접 요청하면 401
   ```bash
   curl -i -X POST -d "content=hi" http://localhost:8091/broadcasts/1/comments   # → 401
   ```
4. **테스트 결제 성공**: 로그인 → 주인이 있는 방송 상세 → 금액 입력 → `후원하기` → 결제위젯에서 카드 결제 → 상세로 돌아와 후원 내역과 합계가 늘었는지 확인
5. **결제 취소**: `후원하기` → 결제창을 닫음 → "결제가 완료되지 않았어요" 화면, 합계 변화 없음(주문은 `CANCELED`)
6. **결제 실패 / 금액 조작**: 성공 리다이렉트 주소의 `amount` 값을 바꿔 접속 → "결제 금액이 주문과 달라요", 합계 변화 없음(주문은 `FAILED`)
7. **승인 통지 중복**: 결제 성공 후 브라우저 방문 기록(Cmd+Y)에서 `/donations/success?paymentKey=...` 주소를 다시 열기 → 합계가 늘지 않음
## 5단계: 로컬 오토스케일

부하에 따라 서버 인스턴스 수가 2~5개 사이에서 자동으로 늘고 줄어듭니다. 상태(회원·방송·댓글·후원·로그인 세션)는 모두 인스턴스 밖 PostgreSQL에 있어 인스턴스가 바뀌어도 유지됩니다.

```
브라우저 → localhost:8092 → (kind 노드 30080) → Service chzzk → chzzk 파드 2~5개 ─┐
                                                   HPA: CPU 평균 50% 기준으로 파드 수 조절  │
                                                                     PostgreSQL(공용) ←──┘
```

| 요구사항 | 구현 | 파일 |
| --- | --- | --- |
| 컨테이너 이미지 | 빌드/실행 2단계 Dockerfile (`application-secret.properties`는 `.dockerignore`로 제외) | `Dockerfile`, `.dockerignore` |
| 여러 인스턴스 실행 | kind 단일 노드 클러스터, Deployment `replicas: 2` | `k8s/kind-config.yaml`, `k8s/chzzk.yaml` |
| 자동 스케일 | HPA `minReplicas 2`, `maxReplicas 5`, CPU 평균 사용률 50%, 부하가 1분간 낮으면 축소. 지표는 metrics-server | `k8s/chzzk.yaml`, `scripts/cluster-up.sh` |
| 요청 분산 | Service `chzzk`(NodePort 30080)가 준비된 파드들에 나눠 보냄. 응답 헤더 `X-Chzzk-Instance`로 처리한 파드 확인 | `k8s/chzzk.yaml`, `InstanceHeaderFilter` |
| 공용 저장소 | `cluster` 프로필에서 PostgreSQL 사용(PVC에 저장). 로그인 세션은 Spring Session JDBC로 DB `SPRING_SESSION` 테이블에 저장 | `application-cluster.properties`, `k8s/postgres.yaml`, `build.gradle` |
| 부하 발생 | k6 Job: 가상 사용자 50명이 2분간 `/broadcasts` 요청 | `load/k6-load.js`, `k8s/k6-job.yaml`, `scripts/load.sh` |

- 영상은 1단계부터 URL로 등록하는 방식이라 별도 파일 저장소가 필요 없습니다(메타데이터는 DB).
- ② 휴대폰 라이브는 구현하지 않아 라이브 상태 공유 대상이 없습니다.

### 실행 방법 (클러스터)

필요한 것: Docker Desktop(실행 중), `kind`(`brew install kind`), `kubectl`

```bash
./scripts/cluster-up.sh      # 클러스터 생성 → 이미지 빌드·로드 → Secret → PostgreSQL → metrics-server → chzzk 2개 + HPA
./scripts/load.sh            # k6 부하 2분 + 10초마다 CPU·파드 수 기록(docs/scale-log.txt), 약 6분
./scripts/cluster-down.sh    # 클러스터 삭제
```

접속: <http://localhost:8092/broadcasts>

### 환경변수 예시 (클러스터)

| 변수 | 예시 | 설명 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `cluster` | 매니페스트에 고정 |
| `DB_URL` | `jdbc:postgresql://postgres:5432/chzzk` | 매니페스트에 고정 |
| `DB_USERNAME` | `chzzk` | 매니페스트에 고정 |
| `DB_PASSWORD` | (자동 생성) | `cluster-up.sh`가 `openssl rand`로 만들어 Secret `chzzk-secrets`에만 저장 |
| `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY` | `test_gck_xxxx` / `test_gsk_xxxx` | 선택. `cluster-up.sh` 실행 전에 `export` 하면 Secret에 들어감 |

### 직접 확인 절차

1. `./scripts/cluster-up.sh` 후 `kubectl --context kind-chzzk get pods,hpa` → chzzk 파드 2개, HPA `2/5`
2. <http://localhost:8092> 에서 회원가입·로그인·방송 등록·댓글 작성
3. 같은 쿠키로 여러 번 요청해 응답 헤더 `X-Chzzk-Instance`가 바뀌어도 로그인·댓글이 그대로인지 확인
   ```bash
   curl -s -c jar -d "username=me&password=pw" localhost:8092/login -o /dev/null
   for i in 1 2 3 4 5; do curl -s -b jar -D - localhost:8092/broadcasts -o /dev/null | grep -i x-chzzk-instance; done
   ```
4. `./scripts/load.sh` 실행 → 부하 중 파드 수가 늘고, 부하가 끝나고 약 1분 뒤 2개로 줄어드는지 확인. 그동안 3번을 반복해 로그인 유지 확인
5. `./scripts/cluster-down.sh`

### 실제 확인 기록 (2026-11-18)

**인스턴스 수 변화** (`scripts/load.sh` 출력, 전체는 [docs/scale-log.txt](docs/scale-log.txt))

```
17:27:58 cpu=17%  replicas=2   ← 부하 시작
17:28:29 cpu=329% replicas=2   ← CPU가 기준(50%) 초과
17:28:49 cpu=399% replicas=5   ← 자동 스케일 아웃 2 → 5 (최대)
17:30:12 cpu=217% replicas=5
17:30:32 cpu=1%   replicas=5   ← 부하 종료(2분)
17:31:33 cpu=1%   replicas=2   ← 자동 스케일 인 5 → 2
```

**부하 결과** ([docs/k6-summary.txt](docs/k6-summary.txt)): 2분간 요청 561,085건(초당 약 4,674건), 실패 0%, 평균 응답 10.65ms

**스케일 중 상태 유지** ([docs/session-check-log.txt](docs/session-check-log.txt)): 로그인한 쿠키로 15초마다 22번 방송 상세를 요청 → 응답한 파드가 5개로 바뀌는 동안(스케일 아웃으로 새로 생긴 파드, 스케일 인 뒤 남은 파드 포함) **22번 모두 로그인 상태 유지, 댓글 표시**

```
17:28:54 chzzk-5558ffb668-8nj7q login=1 comment=1   ← 처음부터 있던 파드
17:29:41 chzzk-5558ffb668-sphjr login=1 comment=1   ← 스케일 아웃으로 생긴 파드
17:30:11 chzzk-5558ffb668-qvkc8 login=1 comment=1   ← 스케일 아웃으로 생긴 파드
17:31:42 chzzk-5558ffb668-nqzjd login=1 comment=1   ← 스케일 인 뒤 남은 파드
```
