# DevFlow 관리자 Analytics 구현 보고서

## 기존 구조 및 구현 범위

Vue 3 / TypeScript / Vue Router / Vite, Spring Boot 3.5 / Java 21 / MyBatis, PostgreSQL 17, Flyway V1/V2 구조를 분석했다. 공개 게시글 조회는 PUBLISHED·published_at·deleted_at 필터를 사용한다. 게시글은 단일 category_id를 갖고 관리자 삭제는 소프트 삭제다. 기존 서버 세션/CSRF 및 `/api/admin/**` ADMIN 검증을 그대로 재사용했다. SYSTEM Bearer 인증은 기존 Agent API 전용 정책을 유지하며 통계 읽기 권한을 부여하지 않았다.

신규 기능은 `analytics/controller → service → mapper` 패키지에 추가했다. 게시글 조회·작성·수정·삭제 및 로그인 API의 로직은 변경하지 않았다. 공개 수집 경로만 기존 보안 설정의 permitAll에 추가하며 CSRF 검사는 유지했다. 현재 브랜치 feat/auth에서 작업했고 이번 통계 변경은 커밋하거나 푸시하지 않았다.

## DB 설계

Flyway `V3__visitor_analytics.sql`은 기존 테이블의 데이터를 변경하지 않고 다음 2개 테이블만 추가한다.

| 테이블 | 주요 컬럼 |
| --- | --- |
| blog.visitor_sessions | UUID id, visitor_hash, started_at, last_seen_at, source, referrer_domain, utm_source/medium/campaign |
| blog.page_view_events | UUID id, session_id FK, visitor_hash, post_id FK, category_id FK(수집 시점 스냅샷), path, occurred_at, dedup_bucket |

추가 인덱스:

- visitor_sessions의 started_at, last_seen_at B-tree 인덱스.
- page_view_events의 occurred_at, (post_id, occurred_at), (category_id, occurred_at) B-tree 인덱스.
- 이벤트 UUID PK와 UNIQUE(session_id, path, dedup_bucket)로 재전송과 반복 조회를 DB에서도 방지한다.
- 세션 FK 삭제는 CASCADE, 게시글/카테고리의 물리 삭제는 SET NULL이다. 현재 게시글은 소프트 삭제하므로 기존 조회 이력은 보존된다.

처음부터 일별 집계 테이블을 중복 생성하지 않았다. 범위 조건은 timestamptz 원본 컬럼에 적용하여 인덱스를 사용할 수 있도록 하고, 집계 쿼리로 필요한 값만 조회한다. 목록의 게시글 본문이나 계정 개인정보를 조회하지 않고 N+1 쿼리를 사용하지 않는다. 데이터 증가 시 EXPLAIN ANALYZE를 바탕으로 daily rollup·파티셔닝을 도입하는 것이 후속 단계다.

## 방문자·방문·조회 집계 기준

- **순 방문자:** 선택 기간 이벤트의 distinct visitor_hash. 일별 순 방문자는 서울 날짜마다 distinct로 계산하므로 일별 방문자의 합은 기간 순 방문자와 다를 수 있다.
- **방문:** 통계용 세션. 30분 비활성 시 새 세션이 시작된다. 선택 기간 방문 횟수는 해당 기간 이벤트가 있는 distinct session_id다.
- **페이지 조회:** 정상 로드된 공개 홈·게시글 목록·공개 게시글 상세의 이벤트. API 호출, 관리자 페이지, 로그인 페이지, 오류 게시글, 비공개 글은 페이지 조회로 집계하지 않는다.
- **중복:** 이벤트 UUID는 한 번만 허용한다. 같은 세션과 정규화된 페이지는 UTC epoch 기준 5분 고정 구간마다 한 번 허용한다. 구간 경계 직전/직후에는 두 번 집계될 수 있다. 무제한 단순 view_count 증가 방식이 아니다.
- **SPA:** 각 공개 화면의 데이터 조회 성공 후 수집한다. 최초 진입·SPA 페이지 변경·브라우저 새로고침을 처리하고, 해시 이동과 UTM/임의 쿼리 변경을 다른 페이지로 세지 않는다. 목록의 categoryId/page만 정규화된 경로에 포함한다. 숨겨진 탭은 표시된 뒤 수집한다.
- **게시글:** 공개 상태를 서버에서 확인한 뒤 수집한다. 본문 ID와 경로의 ID가 다르면 거부한다. 현재 공개 글만 최근/인기 목록에 표시하고, 작성일·수정일·누적 조회·선택 기간 조회를 별도로 제공한다.
- **카테고리:** 현재 모델은 한 글당 하나다. 각 게시글 조회는 수집 시점 category_id에 한 번만 귀속되며 이후 카테고리 변경으로 과거 수치가 이동하지 않는다. category_id=NULL은 미분류다. 카테고리 게시글 수는 현재 공개 글 수이고, 조회 비율의 분모는 게시글 조회만 포함한다(홈·목록 조회 제외).
- **시간대:** 일별 표시와 입력 날짜 범위는 Asia/Seoul이다. 시작일 00:00 포함, 종료일 다음날 00:00 미포함이다. 날짜 구멍은 0으로 채운다. 7/30/90일은 오늘을 포함한다. 사용자 지정은 과거부터 오늘까지 최대 366일이다.
- **증감률:** `(현재 - 이전) / 이전 * 100`. 선택 기간 바로 이전의 같은 일수와 비교한다. 오늘 방문자는 어제와도 비교한다. 이전 값이 0이면 백분율을 만들지 않고 이전 데이터 없음으로 표시한다. 오늘은 진행 중이므로 완료된 전일/동기간 비교와 차이가 날 수 있다.

집계는 기능 도입 이후의 데이터로 시작한다. 과거 방문 기록을 임의로 생성하거나 기존 글에 가짜 조회수를 넣지 않았다.

## 유입 경로

초기 문서의 `document.referrer`(브라우저가 제공하는 최초 HTTP 유입 정보)와 허용된 utm_source/medium/campaign을 전달한다. SPA 이동은 이전 공개 URL을 사용한다. 수집 요청 자체의 HTTP Referer는 현재 블로그 문서를 가리키므로 외부 유입 판정에 그대로 사용하지 않는다. 서버는 Origin을 검증하고 전체 유입 URL을 저장하지 않은 채 hostname만 추출한다.

세션 최초 이벤트에 Google(.com/.co.kr), Naver, Daum, GitHub, 기타 외부, 직접/출처 불명, 내부 이동으로 시작 중 하나를 기록한다. 알려진 utm_source도 소스 분류에 반영하지만 유입을 검증된 실제 검색이라고 보증하지 않는다. 출처가 없으면 DIRECT다. 동일 사이트 도메인이면 INTERNAL이며 UTM이 있어도 새로운 외부 유입으로 바꾸지 않는다. 기존 세션의 내부 이동은 최초 유입을 덮어쓰거나 유입 수를 늘리지 않는다. 유입 차트는 선택 기간에 **시작한 세션**을 분모로 사용하므로 해당 기간에 활동한 방문 횟수와 경계에서 차이가 날 수 있다.

## API

기존 `{ data: ... }` 응답과 JSON error 형식을 사용한다. 아래 ADMIN GET API는 모두 `days=7|30|90` 또는 `start=YYYY-MM-DD&end=YYYY-MM-DD`를 받는다. 사용자 지정 start/end를 함께 주면 해당 범위를 우선한다.

| API | 내용 |
| --- | --- |
| GET /api/admin/analytics/overview | 기간 순 방문자·조회·방문·현재 공개 글 수·방문자당 조회, 오늘/어제 방문자, 보존기간 내 전체 조회/순 방문자, 증감률 |
| GET /api/admin/analytics/traffic | 서울 날짜별 순 방문자/페이지 조회 |
| GET /api/admin/analytics/referrers | 최초 유입 소스별 세션 수/비율 |
| GET /api/admin/analytics/posts/popular | 선택 기간 TOP 10, allTime=true면 누적 TOP 10. 각 행은 누적과 기간 조회 모두 포함 |
| GET /api/admin/analytics/posts/recent | 기간 내 작성된 현재 공개 글 최신 10개 |
| GET /api/admin/analytics/posts/{id}/traffic | 게시글별 날짜 추이 |
| GET /api/admin/analytics/categories | 카테고리별 현재 글 수·누적/기간 조회·기간 비율 |
| POST /api/analytics/page-views | 공개 수집. CSRF·동의·검증·제한 적용, data.counted 반환 |

수집 body:

```json
{
  "eventId": "서버에 재전송해도 동일하게 유지할 UUID",
  "path": "/posts/123",
  "postId": 123,
  "referrer": "https://www.google.com/",
  "utmSource": "google",
  "utmMedium": "organic",
  "utmCampaign": "release-notes",
  "consent": true
}
```

UUID·글 ID·허용 경로·필드 길이를 검증한다. path 최대 200자, referrer 최대 2048자(원문 비저장), UTM 각 100자 및 제한된 영문/숫자 기호만 허용한다. 클라이언트의 조회수·역할·방문 시각·카테고리를 집계에 신뢰하지 않는다. 서버가 시각·역할·글 공개 여부·카테고리를 결정한다.

## 개인정보·보안·보존 정책

- 기본은 수집 안 함이다. 공개 화면 하단에서 수집 동의/거부를 선택하고 다시 변경할 수 있다. 거부해도 모든 공개 기능을 사용할 수 있다. DNT=1 또는 Global Privacy Control도 존중한다.
- 통계용 dv_visitor/dv_visit 쿠키는 로그인 JSESSIONID와 별개다. HttpOnly, SameSite=Lax이고 Secure는 기존 환경변수 SESSION_COOKIE_SECURE를 따른다. 쿠키 Path는 /api/analytics다. 방문자 쿠키는 365일, 세션 쿠키는 30분이다. DB는 난수 방문자 ID의 SHA-256만 저장한다. DB 세션 UUID도 인증 세션과 관계없다.
- 동의 철회 시 신규 수집을 중단한다. 기존 쿠키는 자동 만료되며 기존에 수집한 이벤트는 보존 정책에 따라 만료된다. 이 UI는 과거 데이터 삭제 요청을 처리하는 기능은 아니다.
- 원본 IP, 전체 User-Agent, 전체 referrer URL, 로그인 세션 ID는 DB에 저장하지 않는다. 인증정보를 로그나 결과에 출력하지 않는다.
- 관리자는 클라이언트와 서버 양쪽에서 제외한다. 서버는 ADMIN/SYSTEM 주체, Authorization 헤더가 있는 수집 요청, 알려진 봇 User-Agent를 제외한다. USER는 동의하면 포함한다. SYSTEM은 통계 조회도 허용하지 않는다.
- 공개 수집도 기존 CSRF를 요구한다. Origin이 있으면 같은 출처인지 확인한다. 프록시 환경은 ANALYTICS_PUBLIC_ORIGIN에 정확한 외부 origin을 설정할 수 있다.
- IP 기반 분당 60회 수집 요청 제한은 메모리에만 salted hash로 유지하며 매 분 삭제한다. 최대 10,000개 키로 메모리를 제한한다. 원본 IP를 저장하지 않으며 위조 가능한 X-Forwarded-For를 직접 신뢰하지 않는다. 한 서버 기준 제한이며 여러 서버·공유 NAT에서는 제한 오차가 있다.
- 기본 원시 이벤트 보존기간은 365일이다. ANALYTICS_RETENTION_DAYS는 90~3650일이다. 매일 서울 03:20에 만료 이벤트·세션을 삭제한다. 따라서 UI의 누적/전체 값도 **보존기간 내 누적**이며 영구 누적이 아니다. API·UI에 이를 명시한다.
- 추적 차단, 쿠키 제한/삭제, 다른 브라우저, 동의, 가짜 User-Agent, 악의적 클라이언트와 봇 위장 때문에 실제 인원과 차이가 있다. 익명 이벤트는 과금·보안 감사의 증거로 사용하면 안 된다.
- 수집 요청은 짧은 timeout과 예외 격리로 게시글 열람을 막지 않으며 실패를 사용자 오류 알림으로 띄우지 않는다. 브라우저 수집을 재시도해 무제한 부풀리지 않는다.

## 화면 및 라이브러리

ADMIN 메뉴에 Analytics와 `/admin/analytics` 경로를 추가했다. 기존 흰색 블로그 레이아웃과 초록 포인트, 모바일 반응형을 유지했다. 화면 순서는 기간 필터 → 4개 요약 카드 → 일별 라인 → 유입 도넛 → 카테고리 막대 → 최근 작성 글 → 인기 TOP 10이다. 인기 기준은 선택 기간/전체 누적/최근 7일로 전환한다. 제목은 공개 글 상세로 이동하며 일별 보기 버튼은 해당 글의 선택 기간 차트를 표시한다. 로딩·빈 데이터·오류·재시도와 데이터 표를 제공한다.

Chart.js 한 개만 추가하고 Vue 전용 wrapper는 추가하지 않았다. 라인·막대·도넛에 필요한 컨트롤러만 등록하고 관리자 화면을 lazy load한다. canvas에는 접근성 이름을 제공하며 동일 수치를 표/목록으로도 확인할 수 있다. 공식 문서: [Vue 호환 및 TypeScript](https://www.chartjs.org/docs/), [반응형 차트](https://www.chartjs.org/docs/latest/configuration/responsive.html).

## 실행·검증 결과

기존 `.env`는 수정하지 않았다. `.env.example`에 ANALYTICS_RETENTION_DAYS와 ANALYTICS_PUBLIC_ORIGIN만 추가하고 시작 스크립트가 이를 안전하게 전달하도록 확장했다. 기존 PostgreSQL에 V3를 적용했다. 실행 방법은 기존과 같다.

```powershell
# 루트에서 backend (.env의 DB 설정 사용)
.\scripts\start-backend.ps1
# 별도 터미널
cd frontend
npm.cmd ci
npm.cmd run dev
```

로컬 backend(8080)와 Vite(5173)를 최신 빌드로 복구했다. 서버 재시작으로 기존 로그인 세션은 만료되므로 ADMIN으로 다시 로그인 후 `http://127.0.0.1:5173/admin/analytics`에 접속한다. 관리자 자신의 방문은 집계하지 않는다. 통계는 로그아웃/별도 일반 브라우저에서 동의 후 공개 페이지를 방문해야 쌓인다.

검증:

- npm ci 성공, 의존성 취약점 보고 0개.
- npm test: 40개 성공(기존 에디터·블로그·인증 회귀 포함).
- npm run build: TypeScript 및 Vite production build 성공.
- gradlew.bat build: 기본 테스트 14개 성공.
- gradlew.bat postgresTest: PostgreSQL 통합 14개 성공(기존 인증/게시글 10 + 통계 4). 테스트 데이터는 롤백한다.
- PostgreSQL 17.12 실연동: V3, UUID MyBatis handler, 동일 이벤트/동일 구간 중복, 세션 만료, 최초 유입 유지, 비공개 글 제외, CSRF/Origin, ADMIN/USER 권한, 봇/DNT/Authorization 제외, 서울 자정 날짜 경계, 순 방문자·조회·카테고리 수치를 검증했다.
- 실제 Chrome + 별도 일회용 PostgreSQL DB: 비동의 미수집, 동의 후 쿠키, 새로고침 중복, SPA 이동, 순 방문자 1/페이지 조회 2/방문 1 일치, 빈 상태, 실제 차트 3종, 글 추이, 30일 필터, 데스크톱/모바일 레이아웃을 확인했다. JS 오류와 가로 넘침 없음. 검증 DB 및 임시 브라우저 프로필은 정리한다.
- Docker CLI는 현재 환경에서 찾을 수 없어 컨테이너 기동/재기동은 검증하지 못했다. 실제 PostgreSQL을 사용했으며 H2 테스트를 PostgreSQL 실연동 대신 제시하지 않았다.

브라우저 재검증은 built backend JAR, npm ci, 로컬 psql, Chromium, 로컬 DB CREATEDB 권한이 필요하다.

```powershell
python scripts/verify-auth-e2e.py --analytics
```

결과는 Git에서 제외된 artifacts/analytics-ui/<실행시각>/result.json 및 desktop.png/mobile.png에 남는다.

## 향후 개선

대량 데이터용 일별 집계 및 파티셔닝, 보존기간을 넘어서는 누적 지표 설계, 여러 서버의 Redis 공유 rate limit, 프록시/NAT별 정책, 더 넓은 검색 도메인 분류, 봇 탐지 강화가 후속 작업이다. 익명 식별자는 사람 수의 정확한 증명이 아니며 로그인 계정과 연결하지 않았다. 동의 UI의 조직별 법적/운영 정책과 HTTPS·reverse proxy 설정은 실제 배포 환경에서 검토해야 한다.

## 생성·수정 파일

- `.env.example`
- `backend/src/main/java/com/devflow/analytics/controller/AnalyticsController.java`
- `backend/src/main/java/com/devflow/analytics/controller/VisitController.java`
- `backend/src/main/java/com/devflow/analytics/dto/AnalyticsDtos.java`
- `backend/src/main/java/com/devflow/analytics/mapper/AnalyticsMapper.java`
- `backend/src/main/java/com/devflow/analytics/mapper/VisitMapper.java`
- `backend/src/main/java/com/devflow/analytics/service/AnalyticsRange.java`
- `backend/src/main/java/com/devflow/analytics/service/AnalyticsRetention.java`
- `backend/src/main/java/com/devflow/analytics/service/AnalyticsService.java`
- `backend/src/main/java/com/devflow/analytics/service/VisitRateLimiter.java`
- `backend/src/main/java/com/devflow/analytics/service/VisitService.java`
- `backend/src/main/java/com/devflow/auth/config/SecurityConfig.java`
- `backend/src/main/java/com/devflow/common/mybatis/UuidTypeHandler.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/blog/V3__visitor_analytics.sql`
- `backend/src/test/java/com/devflow/analytics/AnalyticsPolicyTest.java`
- `backend/src/test/java/com/devflow/analytics/AnalyticsPostgresIntegrationTest.java`
- `docs/analytics.md`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/src/analytics/tracking.test.ts`
- `frontend/src/analytics/tracking.ts`
- `frontend/src/api/analytics.ts`
- `frontend/src/components/AnalyticsChart.vue`
- `frontend/src/components/AnalyticsPosts.vue`
- `frontend/src/components/blog/AnalyticsConsent.vue`
- `frontend/src/components/blog/BlogShell.vue`
- `frontend/src/router.ts`
- `frontend/src/styles/analytics.css`
- `frontend/src/views/AnalyticsView.test.ts`
- `frontend/src/views/AnalyticsView.vue`
- `frontend/src/views/BlogHomeView.vue`
- `frontend/src/views/PostDetailView.vue`
- `frontend/src/views/PostListView.vue`
- `scripts/start-backend.ps1`
- `scripts/verify-analytics-ui.mjs`
- `scripts/verify-auth-e2e.py`
