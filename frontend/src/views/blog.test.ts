// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, type App as VueApp } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../App.vue'
import { routes } from '../router'
import type { Category, PostSummary } from '../types/blog'

let app: VueApp | undefined
const category: Category = { id: 91, name: 'API가 정한 카테고리', slug: 'from-api', sortOrder: 0 }
const post: PostSummary = { id: 123, title: '데이터에서 읽은 제목', summary: '서버에서 전달한 게시글 요약입니다.', thumbnailUrl: null,
  publishedAt: '2026-11-17T00:00:00Z', createdAt: '2026-11-16T00:00:00Z', category }
const home = { categories: [{ category, posts: [post] }] }
const response = (data: unknown) => new Response(JSON.stringify({ data }))
async function mount(path = '/') {
  const host = document.createElement('div')
  document.body.append(host)
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  app = createApp(App).use(router)
  app.mount(host)
  return { host, router }
}
afterEach(() => { app?.unmount(); document.body.innerHTML = ''; vi.unstubAllGlobals() })

it('loads home with one request, dynamic categories and at most three cards per category', async () => {
  const data = { categories: [
    { category, posts: [post, { ...post, id: 124 }, { ...post, id: 125 }, { ...post, id: 126 }] },
    { category: { ...category, id: 92, name: '두 번째' }, posts: [{ ...post, id: 127 }] },
    { category: { ...category, id: 93, name: '빈 카테고리' }, posts: [] },
  ] }
  const fetchMock = vi.fn().mockResolvedValue(response(data)); vi.stubGlobal('fetch', fetchMock)
  const { host } = await mount()
  await vi.waitFor(() => expect(host.querySelectorAll('.category-section')).toHaveLength(2))
  expect([...host.querySelectorAll('.category-heading h2')].map(el => el.textContent)).toEqual([category.name, '두 번째'])
  expect(host.querySelectorAll('.category-section')[0]?.querySelectorAll('.post-card')).toHaveLength(3)
  expect(host.querySelectorAll('.category-section')[1]?.querySelectorAll('.post-card')).toHaveLength(1)
  expect(host.textContent).not.toContain('빈 카테고리')
  expect(fetchMock).toHaveBeenCalledTimes(1)
  expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/blog/home')
  expect(host.querySelector('h1')?.textContent).toBe('Blog.')
})
it('shows a genuine empty state without invented articles', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ categories: [] })))
  const { host } = await mount()
  await vi.waitFor(() => expect(host.textContent).toContain('아직 발행된 글이 없습니다'))
  expect(host.querySelector('.post-card')).toBeNull()
})
it('shows loading while pending and allows retry after network failure', async () => {
  let reject!: (cause: Error) => void
  const fetchMock = vi.fn().mockImplementationOnce(() => new Promise((_, fail) => { reject = fail }))
    .mockResolvedValueOnce(response(home))
  vi.stubGlobal('fetch', fetchMock)
  const { host } = await mount()
  expect(host.querySelector('[role="status"]')?.textContent).toContain('불러오는 중')
  reject(new Error('offline'))
  await vi.waitFor(() => expect(host.querySelector('[role="alert"]')).not.toBeNull())
  host.querySelector<HTMLButtonElement>('[role="alert"] button')!.click()
  await vi.waitFor(() => expect(host.querySelectorAll('.post-card')).toHaveLength(1))
  expect(fetchMock).toHaveBeenCalledTimes(2)
})
it('replaces absent and broken thumbnails with a local fallback', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ categories: [{ category, posts: [post, { ...post, id: 124, thumbnailUrl: '/broken.png' }] }] })))
  const { host } = await mount()
  await vi.waitFor(() => expect(host.querySelectorAll('.post-card img')).toHaveLength(2))
  const images = host.querySelectorAll<HTMLImageElement>('.post-card img')
  expect(images[0]?.getAttribute('src')).toBe('/images/post-placeholder.svg')
  images[1]!.dispatchEvent(new Event('error'))
  await vi.waitFor(() => expect(images[1]?.getAttribute('src')).toBe('/images/post-placeholder.svg'))
})
it('navigates from a card to the real detail and safely reuses the Markdown renderer', async () => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => Promise.resolve(response(url === '/api/blog/home'
    ? home : { post, contentMarkdown: '# 본문 제목\n\n```java\nint n = 1;\n```\n\n<script>alert(1)</script>' }))))
  const { host, router } = await mount()
  await vi.waitFor(() => expect(host.querySelector('.post-card a')).not.toBeNull())
  host.querySelector<HTMLAnchorElement>('.post-card a')!.click()
  await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/posts/123'))
  await vi.waitFor(() => expect(host.querySelector('.post-body h1')?.textContent).toBe('본문 제목'))
  expect(host.querySelector('.post-body script')).toBeNull()
  expect(host.querySelector('.post-body .hljs-number')).not.toBeNull()
})
it('opens category all-view with API-backed filtering', async () => {
  const fetchMock = vi.fn().mockImplementation((url: string) => Promise.resolve(response(url === '/api/blog/home' ? home
    : url === '/api/categories' ? [category] : { items: [post], page: 0, size: 9, totalElements: 1, totalPages: 1 })))
  vi.stubGlobal('fetch', fetchMock)
  const { host, router } = await mount()
  await vi.waitFor(() => expect(host.querySelector('.category-heading a')).not.toBeNull())
  host.querySelector<HTMLAnchorElement>('.category-heading a')!.click()
  await vi.waitFor(() => expect(router.currentRoute.value.query.categoryId).toBe('91'))
  await vi.waitFor(() => expect(host.querySelector('.list-intro h1')?.textContent).toBe(category.name))
  expect(fetchMock.mock.calls.some(call => String(call[0]).includes('categoryId=91'))).toBe(true)
})
it('shows a not-found message for nonpublic or missing details', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 404 })))
  const { host } = await mount('/posts/999')
  await vi.waitFor(() => expect(host.querySelector('[role="alert"]')?.textContent).toContain('게시글을 찾을 수 없습니다'))
  expect(host.querySelector('.post-body')).toBeNull()
})
it('aborts an in-flight request when leaving home', async () => {
  let signal: AbortSignal | undefined
  vi.stubGlobal('fetch', vi.fn().mockImplementation((url, options) => {
    if (url === '/api/blog/home') { signal = options.signal; return new Promise(() => {}) }
    return Promise.resolve(new Response(JSON.stringify({ status: 'UP', database: 'UP' })))
  }))
  const { router } = await mount()
  await router.push('/status')
  await vi.waitFor(() => expect(signal?.aborted).toBe(true))
})
