# 치지직 클론

치지직처럼 방송을 올리고, 휴대폰으로 라이브를 켜고, 후원까지 할 수 있는 사이트를 만들어 봤습니다.

| 단계 | 만든 것 |
| --- | --- |
| ① 녹화방송 | 영상 URL로 방송 등록, 목록, 재생 |
| ② 휴대폰 라이브 | 휴대폰 카메라로 송출하고 PC에서 실시간 시청 |
| ③ 로그인·권한·댓글 | 회원가입/로그인, 내 방송만 삭제, 로그인해야 댓글 |
| ④ 도네이션 | 토스페이먼츠 테스트 결제로 방송자에게 후원 |
| ⑤ 오토스케일 | 부하가 오르면 서버가 2개 → 5개로 자동으로 늘어남 |
| ⑥ 도메인·서버·앱 | 내 도메인 + OCI 무료 서버 24시간 운영, 셀룰러 라이브(TURN), PC·Mac·Android·iPhone 앱 |
| ⑦ OCI에서 오토스케일 | OCI 서버에 K3s를 올리고 HPA로 2개 → 5개 자동 확장 (라이브 시그널링은 전용 파드) |

**사용 기술**: Spring Boot 4.1, Java 25, Thymeleaf, Spring Data JPA, H2(파일 DB), WebSocket, WebRTC, 토스페이먼츠 결제위젯
⑤단계만 Docker, kind(로컬 쿠버네티스), PostgreSQL, k6를 추가로 썼습니다.
⑥단계는 Cloudflare(도메인·DNS·터널), OCI(ARM 서버), coturn(TURN), Electron(PC·Mac 앱), Capacitor(휴대폰 앱), GitHub Actions를 썼습니다.

## 테스트할 때 쓰는 링크

**방송 등록용 테스트 영상** (방송 등록 화면의 영상 URL 칸에 그대로 붙여 넣으면 됩니다)

| 영상 | URL |
| --- | --- |
| 꽃 (5초) | `https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4` |
| 금요일 (짧은 영상) | `https://interactive-examples.mdn.mozilla.net/media/cc0-videos/friday.mp4` |
| 빅 벅 버니 (10초) | `https://www.w3schools.com/html/mov_bbb.mp4` |
| 샘플 (5초) | `https://download.samplelib.com/mp4/sample-5s.mp4` |

**배포된 사이트**: <https://chzzk.tmdghks.dev> · 앱 다운로드 <https://tmdghks.dev/download/>

**내 사이트** (로컬에서 서버를 켠 상태에서)

| 화면 | 주소 |
| --- | --- |
| 방송 목록 (첫 화면) | <http://localhost:8091/broadcasts> |
| 회원가입 / 로그인 | <http://localhost:8091/signup> / <http://localhost:8091/login> |
| 녹화방송 등록 | <http://localhost:8091/broadcasts/new> |
| 라이브 시작 | <http://localhost:8091/lives/new> (휴대폰은 터널 주소 + `/lives/new`) |
| ⑤ 클러스터 모드 | <http://localhost:8092/broadcasts> |

**토스페이먼츠**

- [API 키 확인 (개발자센터)](https://developers.tosspayments.com/my/api-keys) — 결제위젯 연동 키 `test_gck_` / `test_gsk_`를 여기서 복사합니다
- [테스트 결제 내역](https://developers.tosspayments.com/my/payment-logs) — 테스트로 결제한 기록이 남는지 확인
- [테스트 환경 안내](https://docs.tosspayments.com/guides/v2/get-started/environment) — 테스트 키로는 실제 돈이 나가지 않습니다
- [결제위젯 연동 가이드](https://docs.tosspayments.com/guides/v2/payment-widget/integration)

**도구**

- [Cloudflare 빠른 터널 (trycloudflare)](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/) — 휴대폰 라이브용 https 주소
- [k6 문서](https://grafana.com/docs/k6/latest/) — 부하 테스트
- [kind](https://kind.sigs.k8s.io/) — 로컬 쿠버네티스

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
라이브는 **로그인 없이** 송출하고 시청할 수 있습니다. 송출 권한은 로그인 대신 아래 방송 키로 확인합니다.

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

---

## ⑥ 내 도메인 + OCI 서버 + 앱 배포

치지직을 내 도메인 **<https://chzzk.tmdghks.dev>** 에 올리고, OCI 무료 서버에서 24시간 돌립니다. PC·Mac·Android·iPhone 앱으로도 쓸 수 있습니다.

| 항목 | 주소 |
| --- | --- |
| 서비스 | <https://chzzk.tmdghks.dev> |
| 상태 확인 (health) | <https://chzzk.tmdghks.dev/actuator/health> → `{"status":"UP"}` |
| 앱 다운로드 | <https://tmdghks.dev/download/> |
| 포트폴리오 | <https://tmdghks.dev> |

### 전체 구조

```
                ┌──────────── Cloudflare ────────────┐
브라우저·앱 ──▶ │ DNS · HTTPS 인증서 · DDoS 보호       │ ──(Cloudflare Tunnel)──┐
                └────────────────────────────────────┘                        ▼
                                                    ┌──── OCI 서버 (싱가포르, ARM) ────┐
                                                    │ cloudflared  →  chzzk (K3s)      │
휴대폰 셀룰러 라이브 영상 ─────(UDP 3478)─────────▶ │ coturn (TURN 중계)               │
                                                    └──────────────────────────────────┘
```

- 서버는 **들어오는 웹 포트(80/443)를 열지 않습니다.** 서버 안의 `cloudflared`가 Cloudflare 쪽으로 먼저 연결을 열고, 그 연결로 요청을 받습니다. 공격받을 수 있는 입구가 줄어듭니다.
- 열린 포트는 SSH(22)와 TURN(3478, 49160~49200/udp)뿐입니다.

### 도메인 (Cloudflare)

| 항목 | 내용 |
| --- | --- |
| 도메인 | `tmdghks.dev` (2026-11-20 구매, 1년) |
| 등록 기관 | Cloudflare Registrar |
| 비용 | **약 $12.20 / 년** (Cloudflare는 등록 원가에 팔아서 .dev 중 가장 쌈. 다른 곳은 $12~21) |
| 서브도메인 | `chzzk.tmdghks.dev` → 터널(CNAME), `tmdghks.dev` → Cloudflare Pages(포트폴리오·다운로드 페이지) |

**DNS·HTTPS**

- 네임서버가 Cloudflare라서 DNS 레코드를 대시보드나 `cloudflared tunnel route dns`로 바로 추가합니다.
- HTTPS 인증서는 Cloudflare가 자동으로 발급·갱신합니다. 서버에는 인증서를 둘 필요가 없습니다.
- `.dev`는 브라우저가 **HTTPS만 허용(HSTS preload)** 하는 도메인이라 http로는 접속되지 않습니다.
- 휴대폰 카메라(getUserMedia)는 HTTPS에서만 켜지므로 라이브에도 꼭 필요합니다.
- 스프링은 `server.forward-headers-strategy=native`로 프록시가 넘겨준 `https` 정보를 그대로 써서, 리다이렉트 주소가 `https://`로 나갑니다.

### 서버 (OCI Always Free)

| 항목 | 선택 | 이유 |
| --- | --- | --- |
| 리전 | 싱가포르 (`ap-singapore-1`) | 가입할 때 정한 홈 리전. 무료 서버는 홈 리전에서만 만들 수 있음. 한국과 가까워 지연이 적음 |
| 모양 | **VM.Standard.A1.Flex (ARM)** 2 OCPU / 12GB | Always Free 중 가장 좋은 사양(ARM 합계 4 OCPU / 24GB까지 무료). x86 무료 서버(E2.1.Micro)는 1/8 코어·1GB라 스프링+TURN을 돌리기 부족 |
| OS | Ubuntu 24.04 (aarch64) | |
| 디스크 | 부트 볼륨 47GB | 무료 한도 200GB 안 |

- 싱가포르의 A1 자리가 계속 없어서("Out of host capacity") OCI CLI로 **자동 재시도 스크립트**를 돌렸습니다. 200번 넘게 실패했습니다.
- 계정을 **Pay As You Go**로 전환하자 몇 분 만에 생성됐습니다. 무료 한도 안에서만 쓰면 요금은 0원입니다.
- 혹시 모를 요금에 대비해 **$1 예산 + 알림**을 걸어 두었습니다. 실제 요금이나 예상 요금이 생기면 메일이 옵니다.
- 4 OCPU / 24GB 대신 2 / 12로 시작한 건 자리를 잡기 쉬워서입니다. 무료 한도 안에서 나중에 늘릴 수 있습니다.

### 서버에서 도는 것

| 이름 | 방식 | 역할 |
| --- | --- | --- |
| `chzzk` | K3s (⑦ 참고) | 스프링 앱. 처음엔 Docker 컨테이너 하나 + H2로 띄웠다가 ⑦에서 K3s + PostgreSQL로 옮김 |
| `cloudflared` | systemd 서비스 | `chzzk.tmdghks.dev` → `localhost:8091` 터널 |
| `coturn` | systemd 서비스 | 셀룰러 라이브용 TURN 중계 |

세 개 모두 서버가 재부팅되면 자동으로 다시 켜집니다.

**배포**: 내 Mac에서 `./scripts/oci-deploy.sh` 한 줄 (⑦ 참고)

### 셀룰러에서도 라이브 되게 (TURN)

- 와이파이에서는 되는데 **LTE·5G에서는 라이브 화면이 안 나왔습니다.**
- 원인: 통신사망은 휴대폰을 여러 겹의 NAT(CGNAT) 뒤에 두어서, WebRTC가 STUN만으로는 상대를 찾지 못합니다.
- 해결: 서버에 **coturn**을 설치해서, 직접 연결이 안 되면 TURN이 영상을 중계하게 했습니다.

| 설정 | 값 |
| --- | --- |
| 포트 | 3478 (udp/tcp), 중계 포트 49160~49200/udp |
| 인증 | `use-auth-secret`: 비밀값으로 **12시간짜리 임시 계정**을 그때그때 만듦 |
| 보호 | 사설망(10.x, 172.16.x, 192.168.x, 127.x)으로는 중계 금지 |

- 브라우저는 방송·시청 전에 `GET /api/ice`([IceController](src/main/java/com/chzzk/live/IceController.java))에서 ICE 서버 목록을 받습니다.
  - 목록 내용: STUN + TURN 주소, 임시 아이디, 비밀번호
  - 비밀번호 = `base64(HMAC-SHA1(비밀값, "만료시각:chzzk"))`
  - 비밀값은 서버 환경변수(`TURN_SECRET`)에만 있고, 코드와 화면에는 없습니다.
- `TURN_URL`·`TURN_SECRET`이 없으면 예전처럼 STUN만 내려줍니다. 로컬 개발은 그대로 됩니다.
- OCI는 클라우드 방화벽(보안 목록)과 우분투 `iptables` 둘 다 열어야 합니다. 보안 목록만 열면 막힙니다.

### 앱 (PC · Mac · Android · iPhone)

모든 앱은 `chzzk.tmdghks.dev` 화면을 앱 창에 띄우는 방식입니다. 서버를 고치면 앱을 다시 배포하지 않아도 바로 반영됩니다.

| 플랫폼 | 만든 방법 | 받는 곳 |
| --- | --- | --- |
| Windows | Electron ([desktop/](desktop)) → `chzzk-win-x64.exe` | 다운로드 페이지 |
| macOS | Electron → `chzzk-mac-universal.dmg` (Apple Silicon·Intel) | 다운로드 페이지 |
| Android | Capacitor ([mobile/](mobile)) → `chzzk-android.apk` (정식 서명) | 다운로드 페이지 |
| iPhone | Capacitor → TestFlight | 내부 테스터 |

- **자동 빌드**: `v1.2.0` 같은 태그를 push 하면 [GitHub Actions](.github/workflows/apps.yml)가 Windows·macOS·Android를 각각의 러너에서 빌드해서 GitHub Release 하나에 올립니다.
  - 다운로드 페이지는 `releases/latest/download/...` 고정 주소라 새 버전이 나오면 자동으로 최신을 받습니다.
- **카메라·마이크 권한**: Android 매니페스트, iOS `Info.plist`, macOS `extendInfo`에 넣었습니다. 앱에서도 라이브 송출이 됩니다.
- Android 서명 키는 저장소에 넣지 않고 GitHub Secrets(`ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`)로 넘깁니다.

### 겪은 문제

| 문제 | 원인 | 해결 |
| --- | --- | --- |
| Mac 앱이 켜자마자 꺼짐 | Electron 앱 이름(`productName`)을 한글 `치지직`으로 지음 | 내부 이름은 `Chzzk`, 화면 표시 이름만 한글(`CFBundleDisplayName`) |
| iPhone 앱에서 로그인 누르면 Safari로 넘어감 | 앱 시작 주소가 `/broadcasts`라, 그 주소로 시작하지 않는 페이지는 밖으로 보냄 | 시작 주소를 사이트 루트로 바꾸고 `/` → `/broadcasts` 리다이렉트 추가 |
| iPhone 상단 바가 시계와 겹침 | 웹뷰가 화면 끝까지 차지 | Capacitor `ios.contentInset: always` |
| Android 영상 자리에 회색 재생 아이콘 | 안드로이드 웹뷰 기본 포스터 | `<video poster>`에 투명 이미지 |
| OCI 서버 생성 실패 | 싱가포르 A1 자리 부족 | CLI 자동 재시도 + Pay As You Go 전환 |

### 직접 해 본 결과 (2026-11-22)

- `https://chzzk.tmdghks.dev/actuator/health` → `{"status":"UP"}`. Mac을 꺼도 접속됩니다.
- 브라우저에서 TURN만 쓰도록 강제(`iceTransportPolicy: 'relay'`) → 서버 공인 IP의 `typ relay` 후보가 잡힘
- **휴대폰 셀룰러(와이파이 끔)로 앱에서 라이브 송출 → 다른 기기에서 시청 성공**
- Android 에뮬레이터: 앱 설치 → 라이브 시작 → 카메라 권한 허용 → "방송 중"
- Mac: GitHub Actions가 만든 `.dmg`를 받아 실행 확인
- iPhone: TestFlight로 설치 → 로그인·라이브 확인

---

## ⑦ OCI 서버에서 오토스케일 (K3s + HPA)

⑤에서 로컬(kind)로 해 본 오토스케일을 **실제 서비스 중인 OCI 서버**로 옮겼습니다. 지금 `chzzk.tmdghks.dev`는 K3s 위에서 돌아갑니다.

```
                                  ┌──────────── OCI 서버 (K3s) ────────────┐
                 /ws/ (라이브) ──▶ │ chzzk-live 파드 1개 (NodePort 30081)    │
Cloudflare ─(터널)─┤                │                                          │──▶ PostgreSQL 파드
                 그 외 페이지  ──▶ │ chzzk 파드 2~5개 ← HPA (NodePort 30080) │     (local-path PVC)
                                  └──────────────────────────────────────────┘
```

| 필요한 것 | 어떻게 했나 | 파일 |
| --- | --- | --- |
| 쿠버네티스 | **K3s**: 서버 한 대용 경량 쿠버네티스. metrics-server·local-path 저장소가 기본 포함이라 HPA·PVC가 바로 됨. 안 쓰는 traefik은 끔 | — |
| 이미지 | 서버에서 ARM 이미지를 빌드해 `k3s ctr images import`로 넣음 (레지스트리 없이) | `scripts/oci-deploy.sh` |
| 비밀값 | DB 비밀번호(랜덤)·토스 키·TURN 비밀값을 Secret `chzzk-secrets`에만 저장 | — |
| 자동 확장 | HPA: 최소 2, 최대 5, CPU 50%. 1분간 낮으면 줄임 | `k8s/oci.yaml` |
| 데이터 | PostgreSQL + 로그인 세션도 DB(Spring Session JDBC) → 파드가 바뀌어도 로그인 유지 | `k8s/postgres.yaml` |
| 외부 연결 | cloudflared가 경로로 나눔: `^/ws/` → 30081, 나머지 → 30080 | `/etc/cloudflared/config.yml` |

### 라이브는 왜 따로 뺐나

- 라이브 시그널링(방송자·시청자 웹소켓 연결)은 **파드 메모리**에 들고 있습니다.
- 파드가 여러 개면 방송자는 A 파드, 시청자는 B 파드에 붙을 수 있습니다. 그러면 서로를 못 찾아서 화면이 안 나옵니다.
- 그래서 웹소켓(`/ws/`)만 **전용 파드 1개(`chzzk-live`)**로 보내고, 목록·다시보기·로그인·후원 같은 나머지 페이지만 HPA로 늘립니다.
- 같은 이미지·같은 DB를 쓰므로 기능 차이는 없습니다.
- 한계: 동시 방송이 아주 많아지면 이 파드 하나가 병목이 됩니다. 그때는 Redis pub/sub처럼 파드끼리 시그널링 메시지를 주고받게 바꾸면 됩니다.

### 배포

```bash
./scripts/oci-deploy.sh
```

소스를 서버로 복사 → ARM 이미지 빌드 → K3s에 넣기 → `kubectl apply` → 파드 순차 교체(rollout) → health 확인까지 한 번에 합니다. 파드를 하나씩 바꿔서 배포 중에도 사이트가 끊기지 않습니다.

### 직접 해 본 결과 (2026-11-22)

서버 안에서 k6로 가상 사용자 50명이 2분 동안 `/broadcasts`를 요청했습니다.

```bash
CTX=default bash scripts/load.sh docs/oci-scale-log.txt   # 서버에서 실행
```

**서버 수 변화** (전체: [docs/oci-scale-log.txt](docs/oci-scale-log.txt), 시간은 UTC)

```
10:24:59 cpu=1%   replicas=2   ← 부하 시작 전
10:25:10 cpu=279% replicas=2   ← CPU가 기준(50%) 넘음
10:25:31 cpu=290% replicas=5   ← 2개 → 5개로 늘어남
10:26:04 cpu=191% replicas=5 ready=5   ← 5개 모두 준비 완료
10:27:29 cpu=0%   replicas=5   ← 부하 끝
10:28:11 cpu=0%   replicas=2   ← 5개 → 2개로 줄어듦
```

- **부하 결과** ([docs/oci-k6-summary.txt](docs/oci-k6-summary.txt)): 요청 32,738건(초당 약 273건), **실패 0%**, 평균 응답 180ms
- ⑤(로컬 Mac, 초당 4,674건)보다 처리량이 낮은 건, 무료 서버가 **ARM 2코어** 하나에서 k6·DB·서버 5개를 같이 돌렸기 때문입니다.
- 전환 후에도 `chzzk.tmdghks.dev`의 health, 웹소켓 연결(101), TURN 임시 계정 발급(`/api/ice`)이 그대로 동작했습니다.
