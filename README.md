# QR-CheckIn — 위치·QR 기반 출결 관리 서비스

교수자가 화면에 띄운 **15초마다 바뀌는 QR**을 학생이 강의실 안에서 스캔하면, 서버가 QR 토큰·수강 자격·시간·위치를 검증해 출석을 기록하는 웹 서비스입니다.

- 설계: 「위치·QR 기반 출결 관리 서비스 — 계획서 및 개요서」
- 1인 개발 일정: [docs/solo-plan.md](docs/solo-plan.md)

## 구조

```
backend/    Spring Boot 3.5 (Java 17+), JPA, Spring Security + JWT
frontend/   React + Vite 화면 소스 (빌드 결과는 backend/src/main/resources/static 에 들어감)
docs/       일정·설계 메모
```

## 진행 상황

| ID | 기능 | 상태 |
|---|---|---|
| — | 프로젝트 뼈대, 엔티티 5개(users, course, enrollment, class_session, attendance), 공통 오류 형식 | ✅ |
| B-01 | 회원가입·로그인(JWT), 역할 구분 | ✅ |
| B-06/B-07 핵심 로직 | QR 토큰 HMAC 발급·검증, 하버사인 거리·정확도 판정 + 단위 테스트 | ✅ |
| B-02 ~ B-05 | 교육·회차·수강생·출석 조건 API | ✅ |
| B-06 ~ B-09 | 출석 시작·종료·QR 발급, 출석 인증, 자동 결석, 오류 안내 (API) | ✅ |
| B-10 ~ B-12 | 내 출결, 회차 현황, 관리자 출결 수정 (API) | ✅ |
| 화면 | 로그인·회원가입, 관리자(교육·회차·QR·현황), 수강생(홈·스캔·출석 결과·내 출결) | ✅ |

## 화면 사용

서버(`backend bootRun`)를 켜고 **http://localhost:8080** 에 접속하면 됩니다. Swagger 없이 회원가입부터 QR 출석까지 모두 화면에서 할 수 있어요.
화면은 빌드된 상태로 Spring Boot 가 함께 제공하므로 Node.js 설치가 필요 없습니다.

### 휴대폰으로 테스트 (HTTPS 터널)

휴대폰의 카메라·위치 기능은 **https** 에서만 동작합니다. Cloudflare 터널로 내 PC 의 8080 을 임시 https 주소로 엽니다(계정 불필요).

```powershell
winget install --id Cloudflare.cloudflared     # 처음 한 번
cloudflared tunnel --url http://localhost:8080
```

출력에 나오는 `https://xxxx.trycloudflare.com` 주소를 PC 와 휴대폰에서 모두 열어 사용합니다.
QR 은 지금 접속한 주소로 만들어지므로, **관리자 QR 화면도 이 https 주소로 열어야** 휴대폰에서 찍었을 때 연결됩니다.

### 화면 코드를 고칠 때 (Node.js 필요)

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173 (API 는 8080 으로 자동 전달)
npm run build    # 결과를 backend/src/main/resources/static 에 덮어씀
```

## Docker로 실행 (서버 + MySQL + 터널 한 번에)

JDK·MySQL·cloudflared 를 따로 설치하지 않아도 되고, 데이터는 Docker 볼륨에 저장돼 껐다 켜도 남아요.
[Docker Desktop](https://www.docker.com/products/docker-desktop/) 설치가 필요해요. 프로젝트 폴더에서:

```powershell
copy .env.example .env          # 처음 한 번 — .env 의 비밀번호·비밀키를 바꿔 주세요
docker compose up -d --build    # 켜기 (처음엔 이미지·라이브러리를 받느라 몇 분)
```

**휴대폰용 HTTPS 주소** — 앱(PWA)은 설치한 주소에 묶이므로 고정 주소를 권장해요.

- 고정 주소(권장): [Tailscale](https://tailscale.com/download) 설치·로그인 후 `tailscale funnel --bg localhost:8080` 한 번 → `https://노트북이름.….ts.net` (재부팅해도 유지)
- 임시 주소: `docker compose --profile tunnel up -d` → `docker compose logs tunnel` 의 `https://….trycloudflare.com` (켤 때마다 바뀜)

| 하고 싶은 것 | 명령 |
|---|---|
| 상태 보기 | `docker compose ps` |
| 서버 로그 계속 보기 (Ctrl+C로 빠져나옴) | `docker compose logs -f app` |
| 코드를 받은 뒤 다시 반영 | `git pull` → `docker compose up -d --build` |
| 끄기 (데이터 유지) | `docker compose down` |
| DB 직접 보기 | `docker compose exec db mysql -u qrcheckin -p qrcheckin` |
| DB 백업 | `docker compose exec db sh -c "mysqldump -u root -p\"$MYSQL_ROOT_PASSWORD\" qrcheckin" > backup.sql` |
| ⚠ 데이터까지 전부 삭제 | `docker compose down -v` |

- 화면은 `http://localhost:8080`, 휴대폰은 위의 HTTPS 주소로 접속해요.
- DB 도구(DBeaver, IntelliJ Database)로는 `localhost:3307` 로 접속할 수 있어요(이 컴퓨터에서만).
- 학교 와이파이처럼 HTTPS 를 가로채는 네트워크에서는 이미지 다운로드·터널이 실패할 수 있어요(x509 오류) → 핫스팟 사용.
- IntelliJ 로 실행하는 기존 방법(H2 파일 DB, `backend/data/`)도 그대로예요. Docker 없이 이렇게 실행해도 돼요. 두 DB 의 데이터는 서로 따로예요.
- IntelliJ 로 실행하면서 Docker 의 MySQL 을 쓰려면: `docker compose up -d db` 로 DB 만 켜고(app 이 켜져 있으면 `docker compose stop app`),
  실행 목록에서 **backend bootRun (Docker MySQL)** ▶. 비밀번호는 루트 `.env` 에서 읽어요. 8080 포트가 겹치니 Docker 의 app 과 동시에 켤 수 없어요.

## 백엔드 실행

JDK 17 이상이 필요합니다. MySQL 없이 H2 파일 DB(`backend/data/`)로 바로 실행됩니다.

```bash
cd backend
./gradlew bootRun          # Windows: gradlew.bat bootRun
./gradlew test             # 테스트
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/qrcheckin`, 사용자 `sa`)

IntelliJ에서는 `backend` 폴더를 Gradle 프로젝트로 열고 `QrCheckinApplication`을 실행하면 됩니다.

### MySQL로 실행

```sql
CREATE DATABASE qrcheckin DEFAULT CHARACTER SET utf8mb4;
CREATE USER 'qrcheckin'@'localhost' IDENTIFIED BY '비밀번호';
GRANT ALL ON qrcheckin.* TO 'qrcheckin'@'localhost';
```

```bash
DB_URL='jdbc:mysql://localhost:3306/qrcheckin?serverTimezone=Asia/Seoul' DB_PASSWORD=비밀번호 ./gradlew bootRun --args='--spring.profiles.active=mysql'
```
(`DB_URL` 을 안 주면 Docker 의 db 컨테이너 `localhost:3307` 에 붙어요)

### 환경 변수 (배포 시 반드시 설정)

| 변수 | 설명 |
|---|---|
| `JWT_SECRET` | JWT 서명 키, 32자 이상 |
| `QR_SECRET` | QR 토큰 HMAC 키, 32자 이상 |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | MySQL 접속 정보 (mysql 프로필) |
| `CORS_ORIGINS` | 프론트 주소, 쉼표로 구분 (기본 `http://localhost:5173`) |

## API (현재)

기본 경로 `/api/v1`, 인증은 `Authorization: Bearer <JWT>`.
모든 오류는 `{"code": "...", "message": "...", "detail": {...}}` 형식입니다.

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | `/auth/signup` | 회원가입 (`role`: `ADMIN` / `STUDENT`) | 공개 |
| POST | `/auth/login` | 로그인, JWT 발급 | 공개 |
| GET | `/me` | 내 정보 | 로그인 |
| PUT | `/me/name` | 이름 변경 `{"name":"..."}` | 로그인 |
| POST | `/me/password` | 비밀번호 변경 (`currentPassword`, `newPassword`, `confirmPassword`) | 로그인 |
| GET | `/push/public-key` | 알림용 서버 공개키 | 로그인 |
| POST | `/push/subscriptions` | 이 기기 알림 구독 (`endpoint`, `keys.p256dh`, `keys.auth`) | 로그인 |
| POST | `/push/subscriptions/delete` | 이 기기 알림 끄기 `{"endpoint":"..."}` | 로그인 |
| POST | `/push/test` | 내 기기로 테스트 알림 | 로그인 |
| GET | `/courses` | 내 교육 목록 (관리자: 개설한 교육, 수강생: 등록된 교육) | 로그인 |
| POST | `/courses` | 교육 생성 | 관리자 |
| GET / PUT / DELETE | `/courses/{courseId}` | 교육 조회·수정·삭제 | 소유 관리자(조회는 수강생도) |
| GET / POST | `/courses/{courseId}/enrollments` | 수강생 목록 · 이메일로 추가 | 소유 관리자 |
| DELETE | `/courses/{courseId}/enrollments/{userId}` | 수강생 삭제 | 소유 관리자 |
| GET / POST | `/courses/{courseId}/sessions` | 회차 목록 · 회차 등록(출석 조건 포함) | 소유 관리자 |
| GET / PUT / DELETE | `/sessions/{sessionId}` | 회차 조회 · 출석 조건 수정 · 삭제 (SCHEDULED 일 때만 수정·삭제) | 소유 관리자 |
| POST | `/sessions/{sessionId}/open` | 출석 시작 (장소 필수, QR 출석 시간 뒤 자동 마감) | 소유 관리자 |
| POST | `/sessions/{sessionId}/close` | 출석 종료 (미출석자 즉시 결석 처리) | 소유 관리자 |
| GET | `/sessions/{sessionId}/qr` | 현재 QR 토큰·URL·남은 시간·출석 인원 | 소유 관리자 |
| GET | `/sessions/{sessionId}/attendances` | 회차 명단·상태·요약(출석/지각/결석/미정) | 소유 관리자 |
| PUT | `/sessions/{sessionId}/attendances/{userId}` | 출결 수정 `{"status":"LATE"}` — 언제든, 기록이 없으면 새로 생성 | 소유 관리자 |
| GET | `/me/attendances?courseId=` | 내 출결(회차별) | 수강생 |
| POST | `/check-in` | 출석 인증 (`token`, `latitude`, `longitude`, `accuracy`) | 수강생 |

출석 인증 실패 시 `code`는 계획서 4.4 오류 코드(QR_INVALID, QR_EXPIRED, NOT_ENROLLED, SESSION_NOT_OPEN, SESSION_CLOSED, LOW_ACCURACY, OUT_OF_RANGE, ALREADY_CHECKED)이고, `detail.hint`에 재시도 방법이 들어갑니다.

```bash
curl -X POST localhost:8080/api/v1/auth/signup -H "Content-Type: application/json" \
  -d '{"email":"prof@test.com","password":"password123","name":"김교수","role":"ADMIN"}'
curl -X POST localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"prof@test.com","password":"password123"}'
```

## 수업 10분 전 알림 (웹 푸시)

프로필의 **수업 알림 → 켜기**(또는 홈 화면 안내의 [켜기])를 누르면, 수업 시작 10분 전에 수강생과 교수 휴대폰으로 알림이 갑니다.
서버가 1분마다 곧 시작하는 회차를 찾아 보내며, 회차마다 한 번만 보냅니다.

- 안드로이드·PC 크롬/엣지: 브라우저와 설치한 앱 모두 가능 / 아이폰: 홈 화면에 추가한 앱(iOS 16.4+)에서만 가능
- https 주소에서만 동작합니다(고정 주소 `https://qr-checkin.tailcefec1.ts.net` 사용). `npm run dev` 개발 서버에서는 동작하지 않습니다
- 서버 키(VAPID)는 처음 실행할 때 자동으로 만들어 DB에 저장하므로 따로 설정할 것이 없습니다
- 알림 내용은 표준(RFC 8291)대로 암호화되어 구글·애플 푸시 서버도 내용을 볼 수 없습니다. 로그아웃하면 그 기기의 알림이 꺼집니다

## QR 토큰 설계 요약

`token = sessionId.windowIndex.HMAC-SHA256(QR_SECRET + ":" + 회차 salt, "sessionId.windowIndex")`

- `windowIndex = ⌊(현재 − 출석 시작 시각) ÷ 15초⌋`, 현재와 직전 1개 주기까지 허용(최대 약 30초)
- 토큰을 DB에 저장하지 않으며, 회차를 열 때마다 salt를 새로 만들어 이전 회차 토큰을 무효화
- 서명 불일치·미래 주기 → `QR_INVALID`, 오래된 주기 → `QR_EXPIRED`

구현: `backend/src/main/java/com/qrcheckin/checkin/QrTokenService.java`
