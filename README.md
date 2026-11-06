# QR-CheckIn — 위치·QR 기반 출결 관리 서비스

교수자가 화면에 띄운 **15초마다 바뀌는 QR**을 학생이 강의실 안에서 스캔하면, 서버가 QR 토큰·수강 자격·시간·위치를 검증해 출석을 기록하는 웹 서비스입니다.

- 설계: 「위치·QR 기반 출결 관리 서비스 — 계획서 및 개요서」
- 1인 개발 일정: [docs/solo-plan.md](docs/solo-plan.md)

## 구조

```
backend/    Spring Boot 3.5 (Java 17+), JPA, Spring Security + JWT
frontend/   React + Vite (예정)
docs/       일정·설계 메모
```

## 진행 상황

| ID | 기능 | 상태 |
|---|---|---|
| — | 프로젝트 뼈대, 엔티티 5개(users, course, enrollment, class_session, attendance), 공통 오류 형식 | ✅ |
| B-01 | 회원가입·로그인(JWT), 역할 구분 | ✅ |
| B-06/B-07 핵심 로직 | QR 토큰 HMAC 발급·검증, 하버사인 거리·정확도 판정 + 단위 테스트 | ✅ (API 연결은 2주차) |
| B-02 ~ B-05 | 교육·회차·수강생·출석 조건 API | ✅ |
| B-06 ~ B-09 | QR 표시, 출석 인증, 자동 결석, 오류 안내 | ⬜ |
| B-10 ~ B-12 | 조회·출결 수정 | ⬜ |

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
DB_PASSWORD=비밀번호 ./gradlew bootRun --args='--spring.profiles.active=mysql'
```

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
| GET | `/courses` | 내 교육 목록 (관리자: 개설한 교육, 수강생: 등록된 교육) | 로그인 |
| POST | `/courses` | 교육 생성 | 관리자 |
| GET / PUT / DELETE | `/courses/{courseId}` | 교육 조회·수정·삭제 | 소유 관리자(조회는 수강생도) |
| GET / POST | `/courses/{courseId}/enrollments` | 수강생 목록 · 이메일로 추가 | 소유 관리자 |
| DELETE | `/courses/{courseId}/enrollments/{userId}` | 수강생 삭제 | 소유 관리자 |
| GET / POST | `/courses/{courseId}/sessions` | 회차 목록 · 회차 등록(출석 조건 포함) | 소유 관리자 |
| GET / PUT / DELETE | `/sessions/{sessionId}` | 회차 조회 · 출석 조건 수정 · 삭제 (SCHEDULED 일 때만 수정·삭제) | 소유 관리자 |

```bash
curl -X POST localhost:8080/api/v1/auth/signup -H "Content-Type: application/json" \
  -d '{"email":"prof@test.com","password":"password123","name":"김교수","role":"ADMIN"}'
curl -X POST localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"prof@test.com","password":"password123"}'
```

## QR 토큰 설계 요약

`token = sessionId.windowIndex.HMAC-SHA256(QR_SECRET + ":" + 회차 salt, "sessionId.windowIndex")`

- `windowIndex = ⌊(현재 − 출석 시작 시각) ÷ 15초⌋`, 현재와 직전 1개 주기까지 허용(최대 약 30초)
- 토큰을 DB에 저장하지 않으며, 회차를 열 때마다 salt를 새로 만들어 이전 회차 토큰을 무효화
- 서명 불일치·미래 주기 → `QR_INVALID`, 오래된 주기 → `QR_EXPIRED`

구현: `backend/src/main/java/com/qrcheckin/checkin/QrTokenService.java`
