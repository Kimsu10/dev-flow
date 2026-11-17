# DevFlow

Vue 3 + Vite + TypeScript와 Java 21 + Spring Boot 3.5 + Gradle 기반 개인 개발 블로그의 초기 애플리케이션입니다. 현재 범위는 PostgreSQL 연결 검사와 상태 화면입니다.

## 구조

```text
frontend/       Vue 앱, API 상태 화면, 컴포넌트 테스트
backend/        Spring Boot, Gradle Wrapper, 테스트
  src/main/java/com/devflow/health/
    controller/ HTTP 응답
    service/    상태 판정
    mapper/     MyBatis SELECT 1
compose.yaml    PostgreSQL 17 및 영속 볼륨
agent/          Codex 실행 Orchestrator, Discord 보고, PostgreSQL 감사 기록
scripts/        기존 Codex 완료 알림 호환 스크립트
```

Codex 작업 시작·완료·실패를 Discord에 자동 보고하려면 [DevFlow Agent 실행 안내](agent/README.md)를 참고하세요. 보고는 Agent를 통해 시작한 작업에 적용됩니다.

## 요구사항

- Java 21, Node.js 20.19+ 또는 22.12+ (검증 환경: Node.js 24.14), npm
- 실행 중인 Docker Engine 및 Docker Compose v2
- 최초 빌드에는 인터넷 연결 필요. Gradle은 Wrapper가 다운로드합니다.

## 실행 (PowerShell)

저장소 루트에서 예시를 복사한 뒤 `.env`의 `POSTGRES_PASSWORD`를 로컬 비밀번호로 직접 설정합니다. 실제 값을 저장소에 커밋하지 마세요.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d --wait
```

Compose는 루트 `.env`를 읽지만 Spring Boot는 자동으로 읽지 않습니다. 프로젝트 루트에서 다음 스크립트로 시작하면 현재 `.env`의 DB 설정을 읽고 이전 터미널의 DB 설정을 교체하여 실행합니다. 비밀번호와 Webhook URL은 출력하지 않습니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

별도 터미널:

```powershell
Set-Location frontend
npm ci
npm run dev
```

화면: http://localhost:5173 / API: http://localhost:8080/api/health

macOS/Linux에서는 동일한 환경변수를 셸에서 설정하고 backend에서 `sh ./gradlew bootRun`을 실행합니다.

## 환경변수

| 변수 | 용도 | 기본값 |
| --- | --- | --- |
| POSTGRES_DB / POSTGRES_USER | Compose DB 이름 / 사용자 | devflow |
| POSTGRES_PASSWORD | Compose DB 비밀번호 | 필수 |
| POSTGRES_PORT | 로컬 DB 노출 포트 | 15432 |
| DB_URL | Spring JDBC URL | POSTGRES_HOST/PORT/DB로 구성 (기본 jdbc:postgresql://localhost:15432/devflow) |
| DB_USERNAME / DB_PASSWORD | Spring DB 인증 | POSTGRES_USER / POSTGRES_PASSWORD 사용 가능 |
| SERVER_PORT | 백엔드 포트 | 8080 |
| API_PROXY_TARGET | Vite 개발 프록시 대상 | http://localhost:8080 |

백엔드 포트를 바꾸면 `frontend/.env.example`을 `frontend/.env`로 복사해 프록시 대상도 변경합니다. `VITE_*`는 브라우저에 노출되므로 비밀정보를 넣지 않습니다. 프로덕션에서는 `/api`를 백엔드로 전달하는 동일 출처 리버스 프록시가 필요합니다. `vite preview`는 개발 프록시를 제공하지 않습니다.

IDE에서 Java 애플리케이션을 직접 실행하면 위 스크립트가 적용되지 않습니다. IDE 실행 설정에 DB 환경변수를 지정하거나 스크립트로 실행하세요. `.env`의 비밀번호 변경 후에는 백엔드를 재시작해야 합니다. `.env`에 별도로 DB_URL/DB_USERNAME/DB_PASSWORD가 있으면 POSTGRES_*보다 우선하므로 이전 값이 남아 있는지도 확인하세요.

## API 계약

`GET /api/health`는 MyBatis로 `SELECT 1`을 실행합니다.

- 정상: HTTP 200, `{"status":"UP","database":"UP"}`
- DB 장애: HTTP 503, `{"status":"DOWN","database":"DOWN"}`

DB 쿼리 및 커넥션 대기 시간은 각각 3초로 제한합니다. Vue 요청은 5초 후 중단하며 재시도할 수 있습니다. 이 API는 DB까지 검사하는 준비 상태 확인 용도입니다.

## 빌드 및 테스트

```powershell
Set-Location frontend
npm ci
npm test
npm run build
Set-Location ../backend
.\gradlew.bat build --no-daemon
```

프런트엔드는 정상·503·잘못된 응답·통신 오류 후 재시도를 검증합니다. 백엔드는 Service 단위 테스트, HTTP 200/503 테스트, H2 PostgreSQL 모드 기반 전체 계층 통합 테스트를 실행합니다. H2 검증은 실제 PostgreSQL 호환성 검증을 대신하지 않습니다.

실제 DB 확인: Compose와 백엔드를 실행한 뒤 `Invoke-RestMethod http://localhost:8080/api/health`로 UP을 확인합니다. `docker compose stop postgres` 후 다시 호출하면 503이 예상됩니다. 이후 `docker compose start postgres`로 복구합니다.

DB 종료는 `docker compose down`을 사용합니다. 볼륨은 유지되며, 초기화된 DB의 인증정보는 `.env` 수정만으로 변경되지 않습니다.

## 초기 검증 결과

- 백엔드 `gradlew.bat build --no-daemon`: 성공, 테스트 6개 통과
- 프런트엔드 `npm test`: 테스트 4개 통과, `npm run build`: 성공
- `npm audit`: 취약점 0건
- `docker compose config --quiet`: 설정 검증 통과
- 실제 PostgreSQL 연동 (2026-11-17): `.env`의 `localhost:15432/devflow`에 연결한 백엔드에서 `GET /api/health` HTTP 200 및 `{"status":"UP","database":"UP"}` 확인. Controller → Service → MyBatis Mapper의 `SELECT 1` 실행 검증 완료
