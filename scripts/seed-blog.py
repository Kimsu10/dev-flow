"""Explicit, local-only sample data. Never updates or deletes existing rows."""
import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from agent.config import load_env, database_settings
import psycopg

TOPICS = [
    ('spring-boot', 'Spring Boot', 10, [
        ('설정 파일을 넘어, Spring Boot의 자동 구성 이해하기', '자동 구성의 조건과 우선순위를 살펴보고, 명시적인 설정이 필요한 순간을 정리합니다.', 'configuration', 'java', '@SpringBootApplication\npublic class Application {\n    public static void main(String[] args) {\n        SpringApplication.run(Application.class, args);\n    }\n}'),
        ('Controller와 Service 사이에 책임을 나누는 기준', '변경에 강한 API를 위해 요청 처리와 업무 규칙을 어디에서 나눠야 하는지 살펴봅니다.', 'layers', 'java', '@Transactional(readOnly = true)\npublic List<Post> findPublishedPosts() {\n    return mapper.findPublishedPosts();\n}'),
        ('작은 테스트로 시작하는 안정적인 REST API', '정상 응답뿐 아니라 빈 결과와 예외 상황까지 확인하는 테스트 작성 경험을 기록합니다.', 'testing', 'java', 'mockMvc.perform(get("/api/blog/home"))\n    .andExpect(status().isOk());'),
    ]),
    ('vue', 'Vue3', 20, [
        ('Composition API로 화면의 관심사 분리하기', '컴포넌트의 데이터 조회와 표현 로직을 작고 읽기 쉬운 단위로 나누는 방법을 소개합니다.', 'composition', 'typescript', 'const loading = ref(true)\nconst posts = shallowRef<Post[]>([])\nonMounted(loadPosts)'),
        ('재사용할 수 있는 게시글 카드 만들기', '한 장의 카드에서 시작해 접근성, 이미지 비율, 반응형 레이아웃까지 다듬어 봅니다.', 'components', 'css', '.post-grid {\n  display: grid;\n  grid-template-columns: repeat(3, minmax(0, 1fr));\n}'),
        ('비동기 요청의 로딩과 오류를 다루는 방법', '요청 취소와 재시도를 고려해 사용자가 현재 상태를 이해할 수 있는 화면을 만듭니다.', 'async', 'typescript', 'const controller = new AbortController()\nawait fetch("/api/blog/home", { signal: controller.signal })'),
    ]),
    ('postgresql', 'PostgreSQL', 30, [
        ('카테고리별 최신 글을 한 번의 쿼리로 조회하기', '윈도 함수로 그룹 안의 순위를 계산해 반복 조회 없이 최신 게시글을 가져옵니다.', 'window', 'sql', 'SELECT id, category_id,\n       row_number() OVER (\n         PARTITION BY category_id\n         ORDER BY published_at DESC, id DESC\n       ) AS position\nFROM blog.posts;'),
        ('인덱스를 추가하기 전에 확인해야 할 것들', '실제 조회 조건과 정렬 순서를 기준으로 인덱스를 설계하는 과정을 정리합니다.', 'indexes', 'sql', 'CREATE INDEX posts_published_idx\nON blog.posts (published_at DESC, id DESC)\nWHERE status = \'PUBLISHED\' AND deleted_at IS NULL;'),
        ('데이터를 지키면서 스키마를 변경하는 방법', 'Flyway의 버전 관리로 기존 데이터를 보존하며 데이터베이스 구조를 확장합니다.', 'migrations', 'sql', 'ALTER TABLE blog.posts\nADD COLUMN IF NOT EXISTS summary varchar(500)\nNOT NULL DEFAULT \'\';'),
    ]),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--confirm-development', action='store_true')
    args = parser.parse_args()
    if not args.confirm_development:
        parser.error('Use --confirm-development to explicitly insert local sample data.')
    load_env()
    settings = database_settings()
    if settings['host'] not in ('localhost', '127.0.0.1', '::1') or settings['dbname'] != 'devflow':
        raise SystemExit('Sample data is restricted to the local devflow database.')
    added = 0
    with psycopg.connect(**settings) as connection:
        for category_slug, name, order, articles in TOPICS:
            slug = 'dev-demo-' + category_slug
            connection.execute('INSERT INTO blog.categories(name,slug,sort_order) VALUES (%s,%s,%s) ON CONFLICT (slug) DO NOTHING', (name, slug, order))
            category_id = connection.execute('SELECT id FROM blog.categories WHERE slug=%s', (slug,)).fetchone()[0]
            for index, (title, summary, cover, language, code) in enumerate(articles):
                body = (f'> 이 글은 메인 페이지 검증을 위한 개발용 샘플입니다.\n\n# {title}\n\n{summary}\n\n'
                        f'## 작은 예제로 살펴보기\n\n```{language}\n{code}\n```\n\n'
                        '## 기억할 점\n\n- 먼저 요구사항과 실제 동작을 확인합니다.\n- 책임을 나누고 필요한 부분만 구현합니다.\n- 정상 흐름과 실패 상황을 함께 검증합니다.\n\n'
                        '개발 과정에서 얻은 배움을 기록하고, 다음 구현에서 조금 더 나은 선택을 해 봅니다.\n')
                result = connection.execute('''INSERT INTO blog.posts(category_id,slug,title,summary,content_markdown,thumbnail_url,status,published_at)
                    VALUES (%s,%s,%s,%s,%s,%s,'PUBLISHED',now()-(%s * interval '1 day')) ON CONFLICT (slug) DO NOTHING''',
                    (category_id, f'{slug}-{cover}', '[개발 샘플] ' + title, summary, body,
                     f'/images/covers/{category_slug}-{cover}.svg', index + 1))
                added += result.rowcount
        # Deliberately hidden samples verify that publication filters are applied.
        for suffix, status, future, deleted in [('draft', 'DRAFT', False, False), ('deleted', 'PUBLISHED', False, True), ('future', 'PUBLISHED', True, False)]:
            result = connection.execute('''INSERT INTO blog.posts(category_id,slug,title,content_markdown,status,published_at,deleted_at)
                VALUES (%s,%s,%s,'개발용 비공개 필터 검증 데이터',%s,
                    CASE WHEN %s='DRAFT' THEN NULL WHEN %s THEN now()+interval '365 days' ELSE now() END,
                    CASE WHEN %s THEN now() ELSE NULL END)
                ON CONFLICT (slug) DO NOTHING''',
                (category_id, 'dev-demo-hidden-' + suffix, '[개발 샘플] 비공개 검증 ' + suffix, status, status, future, deleted))
            added += result.rowcount
    print(f'Development samples ready: {added} new posts (9 published + 3 hidden on first run). Existing data preserved.')


if __name__ == '__main__':
    main()
