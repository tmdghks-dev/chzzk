# 치지직 클론 — ① 녹화방송 · ③ 인증·인가와 댓글 · ④ 도네이션

Spring Boot 4.1 · Java 25 · Thymeleaf · Spring Data JPA · H2(파일 DB) · 토스페이먼츠 결제위젯(테스트 모드)

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