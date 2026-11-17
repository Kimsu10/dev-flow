# 블로그 메인 페이지 및 공개 조회

`feat/blog`에서 구현한 공개 블로그 조회 기능입니다. 기존 Markdown 에디터 내부와 Agent 코드는 수정하지 않았습니다. 현재 메시지에서 참고 이미지를 확인할 수 없어, 요청한 흰색 배경·최소한의 테두리·넓은 여백·3:2 썸네일·카테고리별 카드 배치를 디자인 기준으로 사용했습니다.

## 분석 결과와 범위

- 기존 Vue Router와 `/posts/create`의 CodeMirror Markdown 에디터를 재사용합니다.
- 기존 편집 화면의 저장·발행은 아직 API 미연결 안내를 표시합니다.
- Markdown 렌더링은 기존 `renderMarkdown`을 재사용합니다 (markdown-it, DOMPurify, highlight.js).
- 기존 DB에는 `devflow_reporting`의 세 테이블만 있었고, 게시글·카테고리·이미지 테이블은 없었습니다.
- S3 로컬 인증·파일 작업 검증은 완료돼 있지만 애플리케이션 이미지 업로드 API는 없습니다.
- 이번 변경은 공개 조회이며, 인증 없는 게시글 쓰기 API를 추가하지 않았습니다.

## 화면

| 경로 | 역할 |
| --- | --- |
| `/` | `BlogHomeView`: 카테고리별 최신 글 |
| `/posts?categoryId={id}&page=0` | `PostListView`: 전체·카테고리별 목록, 페이지 이동 |
| `/posts/{id}` | `PostDetailView`: 공개 글과 Markdown 본문 |
| `/posts/create` | 기존 글 작성 화면 유지 |
| `/status` | 기존 서비스 상태 화면 보존 |

`CategorySection`은 카테고리 이름·전체 보기·최대 3개 카드를, `PostCard`는 날짜·썸네일·두 줄 제목/요약·상세 링크를 담당합니다. 카테고리 이름과 표시 순서는 API 데이터로 결정하며 프런트엔드에 고정된 카테고리 목록을 두지 않습니다.

1024px 이상은 3열, 640~1023px은 2열, 639px 이하는 1열입니다. 썸네일은 3:2, `object-fit: cover`이며 없거나 로딩에 실패하면 로컬 기본 SVG로 대체합니다. 이미지 파일을 외부 서버에서 가져오지 않아도 기본 화면과 샘플을 표시할 수 있습니다.

## API

| Method·경로 | 응답 |
| --- | --- |
| `GET /api/blog/home` | `{data:{categories:[{category,posts:[]}]}}` |
| `GET /api/categories` | `{data:[{id,name,slug,sortOrder}]}` |
| `GET /api/posts?categoryId={id}&page=0&size=10` | `{data:{items,page,size,totalElements,totalPages}}` |
| `GET /api/posts/{id}` | `{data:{post,contentMarkdown}}` |
| `GET /api/health` | 기존 응답 유지 |

게시글 요약에는 `id`, `title`, `summary`, `thumbnailUrl`, `createdAt`, `publishedAt`, `category`가 들어갑니다. 목록과 메인 응답은 Markdown 본문을 포함하지 않습니다. 페이지 번호는 0부터, size는 1~50입니다. 존재하지 않는 카테고리의 목록은 빈 결과를 반환합니다.

모든 공개 게시글 조회는 `status='PUBLISHED'`, `deleted_at IS NULL`, `published_at <= CURRENT_TIMESTAMP`를 적용합니다. 초안·삭제·미래 발행·없는 게시글의 상세는 모두 동일한 404입니다. 빈 카테고리는 메인에서 생략하지만 카테고리 목록 API에는 포함합니다. 미분류 글은 전체 목록과 상세에서 조회할 수 있지만 카테고리 섹션에는 포함되지 않습니다.

메인 API는 `row_number() OVER (PARTITION BY category_id ORDER BY published_at DESC,id DESC)`로 카테고리별 3개를 SQL 한 번에 가져옵니다. 카테고리별 반복 API/SQL 호출은 없습니다. 카테고리는 `sort_order,id`, 글은 `published_at DESC,id DESC`로 안정적으로 정렬합니다.

오류는 `{error:{code,message}}` 형식이며 잘못된 조회 조건 400, 비공개/없는 글 404, DB 접근 장애 503을 반환합니다. SQL과 인증정보를 응답에 노출하지 않습니다.

## DB 마이그레이션

Flyway가 **새 `blog` 스키마만** 관리합니다.

```text
backend/src/main/resources/db/migration/blog/V1__create_blog_read_model.sql
```

- `blog.categories`: id, name, slug, sort_order, created_at, updated_at
- `blog.posts`: id, category_id, slug, title, content_markdown, summary, thumbnail_url, status, created_at, updated_at, published_at, deleted_at
- `blog.flyway_schema_history`: Flyway 관리 이력

PK·slug 유일성·카테고리 FK·게시 상태/발행 시각 제약을 적용합니다. 발행 목록과 카테고리별 최신 목록에 맞춘 부분 인덱스를 추가했습니다. 기존 Agent 스키마를 baseline, 수정, 삭제하지 않습니다. 마이그레이션 파일은 적용 후 수정하지 말고 다음 버전 SQL로 확장해야 합니다.

## 실행과 개발용 데이터

기존 실행 방식으로 백엔드를 시작하면 마이그레이션이 적용됩니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1
```

별도 터미널에서 선택적으로 샘플을 추가합니다.

```powershell
agent/.venv/Scripts/python.exe scripts/seed-blog.py --confirm-development
```

샘플은 로컬 `devflow` DB에서만 추가할 수 있습니다. 카테고리 3개, 발행 글 9개와 공개되지 않아야 하는 초안·삭제·미래 글 3개를 만듭니다. 제목은 `[개발 샘플]`, slug는 `dev-demo-` 접두사로 실제 데이터와 구별합니다. 기존 행을 수정·삭제하지 않고, 동일 slug가 있으면 건너뛰므로 재실행 시 중복되지 않습니다. 샘플은 Flyway에 포함되지 않으며 애플리케이션 시작 시 자동 생성되지 않습니다.

```powershell
Set-Location frontend
npm ci
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

샘플 없이 실행하면 실제 빈 상태가 표시됩니다. 샘플 커버는 `frontend/public/images/covers/`의 로컬 SVG이며 실제 업로드한 S3 이미지로 가장하지 않습니다.

## 테스트

프런트엔드: `npm ci`, `npm test`, `npm run build`.

백엔드: `gradlew.bat build --no-daemon`. 기존 H2 상태 검사 테스트에서는 Flyway를 끄고, PostgreSQL SQL과 마이그레이션 검증은 별도 `postgresTest`로 실행합니다. 실제 DB 통합 테스트는 자체 fixture 변경을 테스트 트랜잭션으로 롤백하며 기존 행을 삭제하지 않습니다. 마이그레이션은 DB에 유지됩니다.

실제 PostgreSQL 테스트를 실행하려면 루트 `.env`와 일치하는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 환경변수로 전달한 터미널에서 다음을 실행합니다. 인증정보를 명령 인자나 로그에 출력하지 마세요.

```powershell
Set-Location backend
.\gradlew.bat postgresTest --no-daemon
```

샘플 데이터와 두 서버가 실행 중인 상태에서 브라우저 검증:

```powershell
node scripts/verify-blog-ui.mjs
```

이 스크립트는 Chrome/Edge를 독립적인 임시 프로파일로 헤드리스 실행합니다. `BLOG_TEST_BROWSER`로 실행 파일 경로를 지정할 수 있습니다. 현재 검증 시나리오는 개발 샘플의 3개 카테고리·9개 카드를 기준으로 합니다. 화면 캡처와 JSON 결과는 Git에서 제외한 `artifacts/blog-ui/`에 저장됩니다. 기존 사용자 브라우저 프로파일과 세션은 사용하지 않습니다.

## 실제 검증 결과 (2026-11-17)

- 프런트엔드: 테스트 30개 통과, TypeScript 검사 및 프로덕션 빌드 성공, npm ci 취약점 0건
- 백엔드: 빌드 성공, 기존 테스트 6개 통과
- 실제 PostgreSQL: 통합 테스트 8개 통과 (정렬·3개 제한·동일 날짜 정렬·페이지네이션·비공개 제외·400/404·본문 제외)
- 샘플 데이터 재실행: 추가 0건, 중복·기존 데이터 변경 없음
- 실제 Vue 프록시 `/api/blog/home`: 카테고리 3개 × 공개 글 3개, 카테고리 필터 및 상세 API 성공
- Chrome 실제 렌더링: 1440px 3열, 820px 2열, 390px 1열, 흰색 배경, 3:2 비율, 가로 넘침 없음
- 각 화면의 메인 API 호출 1회, 상세·카테고리·기존 에디터 이동 및 깨진 이미지 대체 성공, JavaScript 예외 0건
- 기존 Markdown 에디터 내부 파일 변경 없음

## 다음 연동

기존 편집 화면의 `title`, Markdown 원문, 카테고리 입력을 재사용하되 저장 기능 구현 시 카테고리 ID 선택·요약·썸네일 입력이 필요합니다. 다음 작업에서 인증된 관리자 저장 API와 연결할 요청 예시는 다음과 같습니다 (현재 구현된 API가 아닙니다).

```json
{
  "title": "게시글 제목",
  "contentMarkdown": "# 본문",
  "summary": "목록에 표시할 요약",
  "categoryId": 1,
  "thumbnailUrl": "/api/images/이미지ID"
}
```

관리자 인증/쓰기 API → S3 이미지 업로드와 이미지 메타데이터 → 에디터 저장·발행 순서로 연결하는 것을 권장합니다. 현재 썸네일 URL 조회·대체 기능은 구현했지만 새 업로드 API, 이미지 테이블, 관리자 인증, 태그 저장·조회, 검색은 이번 범위에 포함하지 않았습니다.
