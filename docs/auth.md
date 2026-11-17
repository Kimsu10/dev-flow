# DevFlow 인증 및 권한 관리

## 기존 구현 분석과 적용 범위

- Vue 3 + Vite + TypeScript, Vue Router와 Composition API를 사용하며 Pinia는 설치되어 있지 않다. 인증 상태는 작은 reactive 모듈로 관리한다.
- 기존 공개 API는 `/api/blog/home`, `/api/categories`, `/api/posts`, `/api/posts/{id}`이고 응답은 `{ "data": ... }` 형식이다.
- Backend의 `blog/controller → service → mapper` 구조와 MyBatis annotation SQL 패턴을 유지했다. Controller는 SQL을 실행하지 않는다.
- 기존 공개 조회의 PUBLISHED, deleted_at, published_at 필터를 그대로 유지했다.
- Markdown 에디터의 CodeMirror, 정제된 미리보기, 이미지 삽입, 툴바를 유지했다. 이전의 저장·발행 안내 문구를 실제 API 호출로 교체했다.
- Git은 깨끗한 main에서 feat/auth를 생성했다. staging, commit, push는 하지 않았다.

## 인증 및 권한 구조

| 역할 | 인증 | 권한 |
| --- | --- | --- |
| ANONYMOUS | Spring Security 익명 인증, 계정 행 없음 | 공개 글·카테고리 조회 |
| USER | BCrypt 비밀번호 + 서버 세션 | 로그인·로그아웃·내 정보·공개 글 조회 |
| ADMIN | BCrypt 비밀번호 + 서버 세션 | 모든 글 조회·작성·수정·삭제·발행·발행 취소·검토, USER/SYSTEM 계정 관리 |
| SYSTEM | `Authorization: Bearer <service-token>` | 자신이 만든 DRAFT/IN_REVIEW 초안 생성·수정 |

브라우저와 Agent는 별도 SecurityFilterChain을 사용한다. Agent API는 stateless이며 세션 쿠키를 인증 수단으로 사용하지 않는다. 서비스 토큰으로 관리자/브라우저 인증을 할 수 없다. 클라이언트 role은 로그인 권한 판단에 사용하지 않는다. 일반 로그인은 SYSTEM을 거부한다.

브라우저 세션은 HttpOnly, SameSite=Lax, 30분 비활성 만료이며 Secure 기본값은 true다. 로컬 HTTP 개발에서만 SESSION_COOKIE_SECURE=false를 사용한다. 로그인 시 세션 ID를 갱신하고 기존 CSRF 토큰을 폐기한다. 로그인·로그아웃을 포함한 브라우저 변경 요청은 CSRF 검사를 받는다. 각 변경 요청 전에 `/api/auth/csrf`의 data.token을 data.headerName 헤더로 보낸다. 토큰은 localStorage에 저장하지 않는다. Spring Security의 명시적 세션 컨텍스트 저장 방식을 적용했다: https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html

세션 계정의 상태와 권한은 요청마다 DB에서 확인한다. 비활성화하면 기존 세션도 사용할 수 없다. 인증 실패는 401, 권한 또는 CSRF 실패는 403이며 JSON error.code/message로 응답한다. DB 장애 및 유효하지 않은 요청은 비밀값을 포함하지 않는 오류로 변환한다. SQL·요청 본문·Authorization·Cookie 헤더를 로깅하도록 설정하지 않는다.

서비스 토큰은 SecureRandom으로 생성한 256비트 난수이며 원문은 발급 응답에서 한 번만 제공한다. DB에는 SHA-256 해시만 보관한다. 유효기간은 90일이다. 교체하면 기존 토큰을 폐기하며, 계정 비활성화 시에도 폐기한다. 토큰 교체와 계정 비활성화는 계정 행 잠금으로 직렬화한다. 원문은 Agent의 비밀 저장소/환경변수에서 관리하고 셸 기록·로그·Git에 남기지 않는다.

공개 회원가입은 제공하지 않는다. 관리자 UI에서 USER/SYSTEM을 생성하며 API로 ADMIN을 생성하거나 role을 승격할 수 없다. ADMIN의 비활성화/변경은 운영 절차에서 관리한다. 댓글·좋아요 구현 시 accounts.id를 FK로 사용할 수 있다.

## DB 마이그레이션

`V2__authentication_and_authorship.sql`은 다음을 추가한다.

- blog.accounts: id, username, email, password_hash, role, status, created_at, updated_at, last_login_at. 아이디·이메일은 대소문자 무시 unique이며 아이디에는 @를 허용하지 않아 이메일 로그인과 모호해지지 않는다.
- blog.service_tokens: account_id, token_hash, created_at, expires_at, revoked_at. SYSTEM 계정의 password_hash는 NULL이며 사람 계정은 해시가 필수다.
- blog.posts.author_id: nullable FK. 기존 글은 NULL을 유지하고 화면에 기존 게시글/LEGACY로 표시한다. 새 글은 서버가 인증 주체를 기록한다. ADMIN이 Agent 글을 수정해도 원 작성 주체는 유지한다.
- 기존 status CHECK를 확장해 DRAFT, IN_REVIEW, PUBLISHED, ARCHIVED를 허용한다. 기존 행, published_at 제약, 공개 조회 인덱스는 유지한다. 데이터 삭제나 테이블 재생성은 없다.

일반 저장은 직접 PUBLISHED 상태를 지정할 수 없다. 발행은 ADMIN 전용 API를 거친다. SYSTEM은 자신 소유의 DRAFT/IN_REVIEW만 수정할 수 있다. 행 잠금으로 발행과 Agent 수정 경합을 처리한다. 발행 취소는 DRAFT로 전환하며 published_at을 비운다. 삭제는 deleted_at을 기록하는 소프트 삭제다.

## API

모든 응답은 기존 data 래퍼를 사용한다. 오류는 `{ "error": { "code": "...", "message": "..." } }`다.

| 메서드·경로 | 접근 | 내용 |
| --- | --- | --- |
| GET /api/auth/csrf | 공개 | CSRF 토큰과 헤더명, 세션 생성 |
| POST /api/auth/login | 공개 + CSRF | username(아이디 또는 이메일), password |
| POST /api/auth/logout | 세션 + CSRF | 세션 무효화·쿠키 제거 |
| GET /api/auth/me | 세션 | id, username, email, role, status |
| GET /api/admin/posts | ADMIN | 최근 수정 순 최대 500개, 본문 포함 |
| GET /api/admin/posts/drafts | ADMIN | DRAFT/IN_REVIEW 최대 500개 |
| GET /api/admin/posts/{id} | ADMIN | 비공개 글을 포함한 상세 |
| POST /api/admin/posts | ADMIN + CSRF | 초안 생성 |
| PUT /api/admin/posts/{id} | ADMIN + CSRF | 글 수정, status 생략 시 현재 상태 유지 |
| DELETE /api/admin/posts/{id} | ADMIN + CSRF | 소프트 삭제 |
| PATCH /api/admin/posts/{id}/publish | ADMIN + CSRF | 승인·발행 |
| PATCH /api/admin/posts/{id}/unpublish | ADMIN + CSRF | 발행 취소 |
| POST /api/agent/posts/drafts | SYSTEM 토큰 | DRAFT 또는 IN_REVIEW 생성 |
| PUT /api/agent/posts/drafts/{id} | SYSTEM 토큰 | 자신의 편집 가능한 초안 수정·검토 요청 |
| GET /api/admin/accounts | ADMIN | 계정 최대 500개, 비밀번호/토큰 제외 |
| POST /api/admin/accounts | ADMIN + CSRF | username, email, role(USER/SYSTEM), password(USER만 필수) |
| PATCH /api/admin/accounts/{id}/status | ADMIN + CSRF | status: ACTIVE/DISABLED |
| POST /api/admin/accounts/{id}/token | ADMIN + CSRF | 활성 SYSTEM 토큰 발급·교체, data.token 1회 제공 |
| DELETE /api/admin/accounts/{id}/token | ADMIN + CSRF | 토큰 폐기 |

게시글 요청 예시(Agent는 Bearer 헤더 추가):

```json
{
  "title": "검토할 글",
  "contentMarkdown": "# 제목\n\nMarkdown 본문",
  "summary": "요약",
  "categoryId": null,
  "status": "IN_REVIEW"
}
```

제목 200자, 본문 1,000,000자, 요약 500자까지 허용한다. 비밀번호는 최소 12자, UTF-8 최대 72바이트다. Agent는 초안 생성 응답의 data.id를 보관하고 PUT으로 수정하거나 IN_REVIEW로 전환한다. ADMIN이 `/admin`에서 제목을 클릭해 기존 에디터로 검토한 뒤 발행한다. Orca/Codex 실행기 자체에 자동 콘텐츠 생성을 연결하는 작업은 향후 범위다.

## 화면과 실행

`/login`에서 로그인한다. `/admin`, `/posts/create`, `/admin/posts/{id}/edit`는 ADMIN 가드가 적용되며 서버도 별도로 검증한다. 새로고침은 `/api/auth/me`로 상태를 복원한다. 익명은 로그인, 권한 부족은 `/forbidden`으로 이동한다. 메뉴는 로그인과 역할에 따라 표시한다.

에디터는 서버 초안 저장·발행을 지원하며 800ms 지연으로 브라우저 localStorage에도 임시저장한다. 복원/삭제 버튼을 제공하며 계정·글별 키를 사용한다. 공용 기기에서는 임시저장을 삭제한다. 태그는 기존 DB에 저장 모델이 없으므로 화면·로컬 임시저장으로만 유지한다. 카테고리는 실제 카테고리 목록에서 선택한다.

1. `.env.example`을 참고해 로컬 `.env`의 PostgreSQL 설정을 채운다. `.env`는 Git에서 제외된다.
2. 최초 실행에만 AUTH_ADMIN_USERNAME, AUTH_ADMIN_EMAIL, AUTH_ADMIN_PASSWORD를 안전하게 설정한다. 아이디는 영문/숫자/_.- 3~100자다. 설정은 로그에 출력하지 않는다. 기존 동일 ADMIN이 있으면 비밀번호를 덮어쓰지 않는다. 생성 후 bootstrap 환경변수를 제거한다. 이 검증 과정에서 사용자의 `.env`를 수정하거나 영구 ADMIN을 만들지 않았다.
3. PostgreSQL이 준비된 상태에서 아래 명령을 실행한다. Flyway V2는 시작 시 적용된다.

```powershell
# 프로젝트 루트
.\scripts\start-backend.ps1
# 별도 터미널
cd frontend
npm.cmd ci
npm.cmd run dev
```

개발은 Vite `/api` 프록시를 유지한다. 배포는 HTTPS의 동일 출처 reverse proxy에서 `/api`를 backend로 전달하고 나머지 경로는 Vue index.html로 fallback한다. SESSION_COOKIE_SECURE=true를 사용한다. FORWARD_HEADERS_STRATEGY=NATIVE는 신뢰할 수 있는 프록시가 외부의 Forwarded/X-Forwarded-*를 정리하는 환경에서만 사용한다. 다중 backend 인스턴스의 세션 공유는 현재 범위에 포함하지 않는다.

## 검증과 남은 작업

- frontend: npm ci, npm test 34개, npm run build 성공.
- backend: gradlew.bat build, 기본 테스트 10개 성공.
- PostgreSQL: postgresTest 10개(기존 조회 8 + 신규 인증 흐름 2), 로컬 PostgreSQL 17.12에서 검증. 테스트 계정·글은 트랜잭션 롤백한다. Flyway V2는 개발 DB에 적용되었다.
- 실제 Chrome: 일회용 별도 PostgreSQL DB에서 로그인 실패/성공, 익명 가드, Markdown 미리보기, 로컬 임시저장, 서버 저장, 발행, 검토 편집, 새로고침 세션 복원, SYSTEM IN_REVIEW 생성, 데스크톱/모바일 레이아웃, 로그아웃을 검증했다. 브라우저 JS 오류 및 가로 넘침 없음. QA DB와 브라우저 임시 프로필은 종료 시 제거한다.
- Docker CLI는 이 환경에서 찾지 못해 `docker compose up`에 의한 컨테이너 기동/재기동 검증은 수행하지 못했다. H2 Health 테스트를 PostgreSQL 실연동 근거로 사용하지 않았다.
- 운영 배포 전 실제 도메인 HTTPS에서 Secure 쿠키·프록시 설정을 확인해야 한다. 초기 ADMIN 설정과 실제 Orca/Codex 비밀 저장소 연결은 운영자가 수행한다.
- 관리자 목록은 최근 500개 제한이다. 대규모 운영 시 페이지네이션을 추가할 수 있다. 비밀번호 재설정·공개 가입·댓글·좋아요는 이번 범위에 포함하지 않는다.

재검증 명령:

```powershell
cd frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
cd ../backend
.\gradlew.bat build
# 로컬 DB 환경변수를 주입한 상태에서:
.\gradlew.bat postgresTest
cd ..
# built backend JAR, npm ci, native psql, Chromium과 로컬 DB의 CREATEDB 권한 필요
python scripts/verify-auth-e2e.py
```

브라우저 결과는 Git에서 제외된 artifacts/auth-ui/<실행시각>/result.json, desktop.png, mobile.png에 남는다. 토큰·비밀번호·세션 ID는 결과에 기록하지 않는다.

## 변경 파일

- `.env.example`
- `backend/build.gradle`
- `backend/src/main/java/com/devflow/DevFlowApplication.java`
- `backend/src/main/java/com/devflow/auth/config/AdminBootstrap.java`
- `backend/src/main/java/com/devflow/auth/config/SecurityConfig.java`
- `backend/src/main/java/com/devflow/auth/controller/AccountController.java`
- `backend/src/main/java/com/devflow/auth/controller/AuthController.java`
- `backend/src/main/java/com/devflow/auth/domain/Account.java`
- `backend/src/main/java/com/devflow/auth/domain/Actor.java`
- `backend/src/main/java/com/devflow/auth/mapper/AccountMapper.java`
- `backend/src/main/java/com/devflow/auth/service/AccountService.java`
- `backend/src/main/java/com/devflow/blog/controller/PostWriteController.java`
- `backend/src/main/java/com/devflow/blog/domain/ManagedPost.java`
- `backend/src/main/java/com/devflow/blog/dto/PostWrite.java`
- `backend/src/main/java/com/devflow/blog/mapper/PostWriteMapper.java`
- `backend/src/main/java/com/devflow/blog/service/PostWriteService.java`
- `backend/src/main/java/com/devflow/common/exception/ApiExceptionHandler.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/blog/V2__authentication_and_authorship.sql`
- `backend/src/test/java/com/devflow/auth/AuthPostgresIntegrationTest.java`
- `backend/src/test/java/com/devflow/auth/AuthSecurityTest.java`
- `backend/src/test/java/com/devflow/health/HealthControllerTest.java`
- `docs/auth.md`
- `frontend/src/App.test.ts`
- `frontend/src/api/admin.ts`
- `frontend/src/auth.test.ts`
- `frontend/src/auth.ts`
- `frontend/src/components/blog/BlogShell.vue`
- `frontend/src/router.ts`
- `frontend/src/style.css`
- `frontend/src/views/AdminView.vue`
- `frontend/src/views/ForbiddenView.vue`
- `frontend/src/views/LoginView.vue`
- `frontend/src/views/PostCreateView.vue`
- `scripts/start-backend.ps1`
- `scripts/verify-auth-e2e.py`
- `scripts/verify-auth-ui.mjs`
- `scripts/verify-blog-ui.mjs`
