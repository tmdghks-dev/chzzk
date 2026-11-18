# 치지직 클론

치지직처럼 방송을 올리고, 휴대폰으로 라이브를 켜고, 후원까지 할 수 있는 사이트를 만들어 봤습니다.

| 단계 | 만든 것 |
| --- | --- |
| ① 녹화방송 | 영상 URL로 방송 등록, 목록, 재생 |
| ② 휴대폰 라이브 | 휴대폰 카메라로 송출하고 PC에서 실시간 시청 |
| ③ 로그인·권한·댓글 | 회원가입/로그인, 내 방송만 삭제, 로그인해야 댓글 |
| ④ 도네이션 | 토스페이먼츠 테스트 결제로 방송자에게 후원 |
| ⑤ 오토스케일 | 부하가 오르면 서버가 2개 → 5개로 자동으로 늘어남 |

**사용 기술**: Spring Boot 4.1, Java 25, Thymeleaf, Spring Data JPA, H2(파일 DB), WebSocket, WebRTC, 토스페이먼츠 결제위젯
⑤단계만 Docker, kind(로컬 쿠버네티스), PostgreSQL, k6를 추가로 썼습니다.

---

## 실행하는 법

1. JDK 25를 설치합니다.
2. `src/main/resources/application-secret.properties` 파일을 만들고 토스 **결제위젯 테스트 키**를 넣습니다.
   이 파일은 `.gitignore`에 넣어 놔서 깃에 올라가지 않습니다.
   ```properties
   toss.client-key=test_gck_xxxxxxxxxxxxxxxxxxxx
   toss.secret-key=test_gsk_xxxxxxxxxxxxxxxxxxxx
   ```
   파일 대신 환경변수로 넣어도 됩니다.
   ```bash
   export TOSS_CLIENT_KEY=test_gck_xxxxxxxxxxxxxxxxxxxx
   export TOSS_SECRET_KEY=test_gsk_xxxxxxxxxxxxxxxxxxxx
   ```
3. 실행합니다.
   ```bash
   ./gradlew bootRun
   ```
4. <http://localhost:8091/broadcasts> 로 들어가면 됩니다.

### 밖에서 접속하기 (Cloudflare Tunnel)

휴대폰 카메라는 https 주소에서만 켜져서, 라이브를 하려면 터널이 필요합니다.

```bash
brew install cloudflared
cloudflared tunnel --url http://localhost:8091
```

출력에 나오는 `https://<랜덤이름>.trycloudflare.com` 주소로 들어가면 됩니다. 터널을 껐다 켜면 주소가 바뀝니다.

---

## ① 녹화방송

- 제목과 영상 URL을 넣으면 방송이 등록되고, 상세 화면에서 `<video>`로 재생됩니다.
- H2를 파일 DB로 써서 서버를 껐다 켜도 데이터가 남아 있습니다.
- 코드: `broadcast/Broadcast`, `BroadcastController`

## ② 휴대폰 라이브

휴대폰 브라우저 카메라로 방송을 켜면 PC 브라우저에서 실시간으로 볼 수 있습니다. 앱 설치는 필요 없습니다.

### 어떻게 동작하나

```
📱 휴대폰(방송자) ──① 연결 정보만 주고받음 (WebSocket)──▶ 🖥️ 서버 ◀── 💻 PC(시청자)
📱 휴대폰 ━━━━━━━━━━━━━② 영상은 직접 감 (WebRTC P2P)━━━━━━━━━━━━━━━▶ 💻 PC
```

- **영상은 서버를 거치지 않습니다.** 휴대폰에서 시청자 브라우저로 바로 갑니다(WebRTC P2P).
- 서버는 서로 연결할 수 있게 **쪽지(offer / answer / ICE)만 전달**합니다. 주소는 `/ws/live`입니다.
- 내 인터넷 주소를 알아내는 데는 구글 STUN 서버(`stun.l.google.com:19302`)를 씁니다.

### 송출 권한

- 라이브를 만들면 서버가 **방송 키(UUID)** 를 하나 만들어 줍니다.
- 이 키가 있어야 송출 접속과 방송 종료를 할 수 있습니다. 키가 틀리면 403이 나거나 연결이 끊깁니다.
- 시청은 키 없이 누구나 할 수 있습니다.

### 방송 상태

- 방송 중이면 `ON_AIR`, 방송자가 종료하면 `ENDED`가 됩니다.
- 목록에 🔴(방송 중) / (종료)로 표시됩니다. 종료된 방송을 열면 "종료된 방송이에요"가 나옵니다.

### 주소 정리

| 주소 | 하는 일 |
| --- | --- |
| `/lives/new` | 라이브 만들기 (제목 입력 → 키 발급 → 송출 화면으로 이동) |
| `/lives/{id}/broadcast?key=...` | 송출 화면. 카메라·마이크 켜기, 전면/후면 카메라 전환, 시청자 수 표시 |
| `/lives/{id}` | 시청 화면 |
| `POST /lives/{id}/end` | 방송 종료 (키 필요) |
| `/ws/live` | 연결 정보 주고받는 WebSocket |

코드: `live/LiveController`, `live/LiveSignalingHandler`, `templates/live/*`

### 주의할 점

- 휴대폰 카메라는 **https에서만** 켜집니다. 그래서 위 터널 주소로 접속해야 합니다.
- 터널을 거치면 WebSocket이 403으로 막혀서 `server.forward-headers-strategy=native`를 켜 뒀습니다. 터널이 붙여 주는 "원래 https였다"는 정보를 서버가 믿게 하는 설정입니다.
- 카메라·마이크 권한을 **허용**해야 합니다. 아이폰은 화면이 꺼지거나 Safari가 백그라운드로 가면 송출이 끊깁니다.
- 중계 서버(TURN)가 없어서 방화벽이 빡빡한 네트워크끼리는 연결이 안 될 수 있습니다. 같은 와이파이에서 제일 잘 됩니다.
- 연결 목록을 서버 메모리에 들고 있어서, ⑤처럼 서버를 여러 대로 늘리면 방송자와 시청자가 서로를 못 찾습니다. 라이브는 서버 한 대 기준입니다.

### 확인하는 법

1. 휴대폰에서 `https://<터널주소>/lives/new` → 제목 입력 → `방송 시작` → `카메라 켜고 송출 시작` → 권한 허용
2. PC에서 `/broadcasts` → 🔴 방송 클릭 → 영상이 나오는지, 휴대폰에 시청자 수가 올라가는지 확인
3. 휴대폰에서 `카메라 전환` → PC 화면이 끊기지 않고 바뀌는지 확인
4. 휴대폰에서 `방송 종료` → PC에 "방송이 종료됐어요", 목록에 `(종료)` 표시
5. 키 없이 종료하면 403
   ```bash
   curl -i -X POST -d "key=wrong" http://localhost:8091/lives/1/end   # → 403
   ```

### 직접 해 본 결과 (2026-11-19)

- 아이폰(Safari, 터널 https)에서 후면 카메라로 송출하고, PC 시청자 2명(localhost, 터널 주소)이 동시에 봤습니다. 둘 다 연결 성공, 영상(480×640)과 소리가 잘 들어왔습니다.
- **서버가 몇 명까지 버티는지** k6로 테스트해 봤습니다(`scripts/live-load.sh`).
  - 가짜 방송자 1명에 가짜 시청자를 200 → 500 → 1,000 → 2,000 → 3,000명까지 늘렸습니다.
  - 접속 12,945번, 연결 정보 전달 **성공 100%**, 전달 시간 **95%가 4ms 안**이었습니다.
  - 서버 CPU는 최고 약 40%(코어 1개 기준), 메모리 약 600MB로, 3,000명까지도 버텼습니다.
  - 기록: `docs/live-load-k6.txt`, `docs/live-load-cpu.txt`, `docs/live-load-summary.json`
- 서버는 쪽지만 전달해서 가볍지만, **영상은 휴대폰이 시청자마다 따로 보내야 해서** 실제로는 휴대폰 쪽이 먼저 한계에 걸립니다(보통 몇 명 수준). 시청자가 많아지면 미디어 서버(SFU)가 따로 필요합니다.

## ③ 로그인·권한·댓글

- 회원가입/로그인/로그아웃은 세션(HttpSession)으로 처리하고, 비밀번호는 BCrypt로 암호화해서 저장합니다.
- 방송 등록은 로그인해야 할 수 있습니다.
- 방송 삭제는 **서버에서 방송 주인인지 검사**합니다. 주인이 아니면 403입니다. 화면에서 버튼만 숨긴 게 아니라 직접 요청해도 막힙니다.
- 댓글은 로그인한 사람만 쓸 수 있습니다. 로그인 없이 요청하면 401입니다.
- 방송 목록, 시청, 댓글 읽기, 후원 내역은 로그인 없이도 볼 수 있습니다.
- 코드: `member/*`, `comment/*`, `BroadcastController.create/delete`

### 확인하는 법

1. 계정 A로 방송 등록 → 계정 B로 로그인하면 그 방송에 삭제 버튼이 안 보입니다.
2. 버튼 없이 직접 요청해도 막힙니다.
   ```bash
   curl -i -X POST http://localhost:8091/broadcasts/1/delete            # 로그인 안 함 → 403
   curl -i -X POST -d "content=hi" http://localhost:8091/broadcasts/1/comments   # → 401
   ```

## ④ 도네이션 (토스페이먼츠 테스트 결제)

로그인한 사람이 방송 상세에서 금액을 정해 방송자에게 후원할 수 있습니다.

### 결제 흐름

1. `후원하기`를 누르면 서버가 먼저 **주문서(READY)** 를 저장합니다. 금액과 받는 사람을 서버가 기록해 둡니다.
2. 토스 결제위젯에서 결제합니다.
3. 결제 성공으로 돌아오면 서버가 확인합니다.
   - 주문서 금액과 같은지, 받는 사람이 맞는지
   - 토스 **결제 승인 API**를 호출하고, 응답의 상태(`DONE`)·금액·주문번호까지 확인
4. 전부 맞을 때만 후원이 **확정(DONE)** 되고 내역과 합계에 들어갑니다.

### 후원 상태

| 상태 | 뜻 |
| --- | --- |
| `READY` | 주문서만 만든 상태 (결제 전) |
| `DONE` | 토스 승인까지 확인된 후원. 내역·합계에 포함 |
| `FAILED` | 결제 실패, 또는 금액이 주문서와 다름 |
| `CANCELED` | 사용자가 결제창을 닫음 |

- 같은 결제가 두 번 처리되지 않게 주문번호와 결제키를 DB에서 중복 불가(unique)로 막았고, 이미 `DONE`인 주문은 다시 처리하지 않습니다.
- 코드: `donation/*`, `TossClient`, `templates/donation/checkout.html`

### 확인하는 법

1. **결제 성공**: 로그인 → 방송 상세 → 금액 입력 → `후원하기` → 카드 결제 → 내역과 합계가 늘었는지 확인
2. **결제 취소**: 결제창을 닫으면 "결제가 완료되지 않았어요", 합계 그대로
3. **금액 조작**: 결제 성공 주소의 `amount`를 바꿔서 들어가면 "결제 금액이 주문과 달라요", 합계 그대로
4. **중복 요청**: 결제 성공 주소를 방문 기록에서 다시 열어도 합계가 늘지 않음

## ⑤ 오토스케일 (로컬 쿠버네티스)

부하가 오르면 서버가 2개에서 최대 5개까지 자동으로 늘어나고, 부하가 끝나면 다시 2개로 줄어듭니다.
회원, 방송, 댓글, 후원, 로그인 세션은 전부 서버 밖의 PostgreSQL에 저장해서 서버가 늘고 줄어도 그대로 유지됩니다.

```
브라우저 → localhost:8092 → chzzk 서버 2~5개 → PostgreSQL(같이 씀)
              (CPU 평균 50% 넘으면 서버 수를 늘림)
```

| 필요한 것 | 어떻게 했나 | 파일 |
| --- | --- | --- |
| 컨테이너 이미지 | 빌드용/실행용 2단계 Dockerfile. 시크릿 파일은 이미지에서 빼 둠 | `Dockerfile`, `.dockerignore` |
| 서버 여러 대 | kind로 로컬 쿠버네티스를 만들고 서버 2개로 시작 | `k8s/kind-config.yaml`, `k8s/chzzk.yaml` |
| 자동으로 늘리고 줄이기 | HPA: 최소 2개, 최대 5개, CPU 50% 기준. 부하가 1분간 낮으면 줄임 | `k8s/chzzk.yaml` |
| 요청 나누기 | 쿠버네티스 Service가 서버들에 나눠 보냄. 응답 헤더 `X-Chzzk-Instance`에 처리한 서버 이름이 나옴 | `InstanceHeaderFilter` |
| 데이터 같이 쓰기 | PostgreSQL 사용. 로그인 세션도 Spring Session JDBC로 DB에 저장 | `application-cluster.properties`, `k8s/postgres.yaml` |
| 부하 주기 | k6로 가상 사용자 50명이 2분 동안 `/broadcasts` 요청 | `load/k6-load.js`, `scripts/load.sh` |

### 실행하는 법

필요한 것: Docker Desktop(켜 둔 상태), `kind`(`brew install kind`), `kubectl`

```bash
./scripts/cluster-up.sh      # 클러스터 만들기 + 이미지 빌드 + DB + 서버 2개 + HPA
./scripts/load.sh            # 2분 동안 부하 주면서 10초마다 CPU·서버 수 기록 (약 6분)
./scripts/cluster-down.sh    # 클러스터 지우기
```

접속: <http://localhost:8092/broadcasts>

DB 비밀번호는 `cluster-up.sh`가 랜덤으로 만들어서 쿠버네티스 Secret에만 저장합니다. 토스 키는 실행 전에 `export` 해 두면 같이 들어갑니다(선택).

### 직접 해 본 결과 (2026-11-18)

**서버 수 변화** (전체: [docs/scale-log.txt](docs/scale-log.txt))

```
17:27:58 cpu=17%  replicas=2   ← 부하 시작
17:28:29 cpu=329% replicas=2   ← CPU가 기준(50%) 넘음
17:28:49 cpu=399% replicas=5   ← 2개 → 5개로 늘어남
17:30:32 cpu=1%   replicas=5   ← 부하 끝
17:31:33 cpu=1%   replicas=2   ← 5개 → 2개로 줄어듦
```

- **부하 결과** ([docs/k6-summary.txt](docs/k6-summary.txt)): 2분 동안 요청 561,085건(초당 약 4,674건), 실패 0%, 평균 응답 10.65ms
- **로그인 유지** ([docs/session-check-log.txt](docs/session-check-log.txt)): 로그인한 상태로 15초마다 22번 요청했습니다. 응답한 서버가 5개로 바뀌는 동안에도 **22번 모두 로그인 상태와 댓글이 그대로** 나왔습니다.
