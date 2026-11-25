// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createApp, type App as VueApp } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../App.vue'
import { routes } from '../router'
import type { Category, PostSummary } from '../types/blog'

let app: VueApp | undefined
beforeEach(() => { vi.stubGlobal('scrollTo', vi.fn()) })
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

it('submits search, resets page and preserves search across category navigation', async () => {
  const fetchMock = vi.fn().mockImplementation((url: string) => Promise.resolve(response(url === '/api/categories'
    ? [category] : { items: [post], page: 0, size: 9, totalElements: 1, totalPages: 3 })))
  vi.stubGlobal('fetch', fetchMock)
  const { host, router } = await mount('/posts?categoryId=91&page=2')
  await vi.waitFor(() => expect(host.querySelector('.post-card')).not.toBeNull())
  const input = host.querySelector<HTMLInputElement>('#post-search')!
  input.value = ' Java & Vue '
  input.dispatchEvent(new Event('input', { bubbles: true }))
  host.querySelector('.post-search')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  await vi.waitFor(() => expect(router.currentRoute.value.query.q).toBe('Java & Vue'))
  expect(router.currentRoute.value.query.page).toBeUndefined()
  await vi.waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('q=Java+%26+Vue'))).toBe(true))
  host.querySelector<HTMLAnchorElement>('.category-nav a')!.click()
  await vi.waitFor(() => expect(router.currentRoute.value.query.categoryId).toBeUndefined())
  expect(router.currentRoute.value.query.q).toBe('Java & Vue')
  await router.back()
  await vi.waitFor(() => expect(router.currentRoute.value.query.categoryId).toBe('91'))
})

it('distinguishes empty search results from an empty blog', async () => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => Promise.resolve(response(url === '/api/categories'
    ? [] : { items: [], page: 0, size: 9, totalElements: 0, totalPages: 0 }))))
  const { host } = await mount('/posts?q=missing')
  await vi.waitFor(() => expect(host.textContent).toContain('검색 결과가 없습니다'))
  expect(host.textContent).not.toContain('아직 발행된 글이 없습니다')
})

it('searches from home into the posts API and displays matching articles', async () => {
  const fetchMock = vi.fn().mockImplementation((url: string) => Promise.resolve(response(url === '/api/blog/home' ? home
    : url === '/api/categories' ? [category] : { items: [post], page: 0, size: 9, totalElements: 1, totalPages: 1 })))
  vi.stubGlobal('fetch', fetchMock)
  const { host, router } = await mount('/')
  await vi.waitFor(() => expect(host.querySelector('.post-card')).not.toBeNull())
  expect(host.querySelector('.blog-header .blog-primary-nav a[href="/"]')).toBeNull()
  const input = host.querySelector<HTMLInputElement>('#header-post-search')!
  input.value = '개발 기록';input.dispatchEvent(new Event('input', { bubbles: true }))
  host.querySelector('.blog-header-search')!.dispatchEvent(new Event('submit', { cancelable: true }))
  await vi.waitFor(() => expect(router.currentRoute.value.query.q).toBe('개발 기록'))
  expect(router.currentRoute.value.path).toBe('/posts')
  await vi.waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('q=%EA%B0%9C%EB%B0%9C+%EA%B8%B0%EB%A1%9D'))).toBe(true))
  await vi.waitFor(() => expect(host.querySelector('.post-search input')?.getAttribute('id')).toBe('post-search'))
  expect(host.querySelector<HTMLInputElement>('#post-search')?.value).toBe('개발 기록')
  expect(host.querySelectorAll('.post-card')).toHaveLength(1)
  expect(host.querySelector('.post-card-title')?.textContent).toBe(post.title)
})

it('clears a search within its category and resets all filters from an empty list', async () => {
  const fetchMock = vi.fn().mockImplementation((url: string) => {
    const items = url.includes('categoryId=') || url.includes('q=') ? [] : [post]
    return Promise.resolve(response(url === '/api/categories'
      ? [category] : { items, page: 0, size: 9, totalElements: items.length, totalPages: items.length }))
  })
  vi.stubGlobal('fetch', fetchMock)
  const { host, router } = await mount('/posts?q=missing&categoryId=91&page=2')
  const button = (label: string) => [...host.querySelectorAll('button')].find(item => item.textContent === label)
  const input = host.querySelector<HTMLInputElement>('#post-search')!

  button('검색어 지우기')!.click()
  await vi.waitFor(() => expect(router.currentRoute.value.query).toEqual({ categoryId: '91' }))
  expect(input.value).toBe('')
  expect(document.activeElement).toBe(input)
  await vi.waitFor(() => expect(host.textContent).toContain('이 카테고리에 발행된 글이 없습니다'))

  button('모든 게시글 보기')!.click()
  await vi.waitFor(() => expect(router.currentRoute.value.query).toEqual({}))
  await vi.waitFor(() => expect(host.querySelector('.post-card')).not.toBeNull())

  router.back()
  await vi.waitFor(() => expect(router.currentRoute.value.query.categoryId).toBe('91'))
  router.back()
  await vi.waitFor(() => expect(router.currentRoute.value.query).toEqual({ q: 'missing', categoryId: '91', page: '2' }))
  expect(input.value).toBe('missing')
})

it('renders a linked table of contents and restores a deep link after the body loads', async () => {
  const scroll = vi.fn()
  const original = HTMLElement.prototype.scrollIntoView
  HTMLElement.prototype.scrollIntoView = scroll
  try {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(response({ post, contentMarkdown: '# Intro\n\n## Install\n\nText' }))))
    const { host, router } = await mount('/posts/123#section-install')
    await vi.waitFor(() => expect(host.querySelectorAll('.article-toc a')).toHaveLength(2))
    expect(host.querySelectorAll('.article-toc a')[1]?.getAttribute('href')).toBe('#section-install')
    expect(host.querySelector('#section-install')?.textContent).toBe('Install')
    expect(host.textContent).toContain('약 1분 읽기')
    await vi.waitFor(() => expect(scroll).toHaveBeenCalled())
    await router.push('/posts/124')
    await vi.waitFor(() => expect(host.querySelectorAll('.article-toc a')).toHaveLength(2))
    expect(host.querySelectorAll('#section-install')).toHaveLength(1)
  } finally { HTMLElement.prototype.scrollIntoView = original }
})

it('recovers unknown URLs with home and search links and an accessible main target', async () => {
  vi.stubGlobal('fetch', vi.fn())
  const { host, router } = await mount('/unknown/page')
  expect(host.querySelector('h1')?.textContent).toBe('페이지를 찾을 수 없습니다.')
  expect(host.querySelector('.not-found-links a[href="/posts"]')).not.toBeNull()
  expect(host.querySelector('.skip-to-content')?.getAttribute('href')).toBe('#main-content')
  expect(host.querySelector('main')?.getAttribute('tabindex')).toBe('-1')
  expect(router.currentRoute.value.name).toBe('not-found')
})

it('offers API-backed related posts and replaces recommendations when navigating to a peer', async () => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => Promise.resolve(response(url.includes('/related?') ? {
    items: url.includes('/124/') ? [] : [{ ...post, id: 124, title: 'Related article' }], page: 0, size: 3, totalElements: url.includes('/124/') ? 0 : 1, totalPages: url.includes('/124/') ? 0 : 1,
  } : {
    post: { ...post, id: url.endsWith('/124') ? 124 : 123 }, contentMarkdown: '# Body',
    relatedPosts: url.endsWith('/124') ? [] : [{ ...post, id: 124, title: 'Related article' }],
  }))))
  const { host, router } = await mount('/posts/123')
  await vi.waitFor(() => expect(host.querySelectorAll('.related-posts .post-card')).toHaveLength(1))
  expect(host.querySelector('.related-posts h2')?.textContent).toBe('같은 카테고리의 다른 글')
  host.querySelector<HTMLAnchorElement>('.related-posts a')!.click()
  await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/posts/124'))
  await vi.waitFor(() => expect(host.querySelector('.post-body')).not.toBeNull())
  await vi.waitFor(() => expect(host.querySelector('.related-empty')?.textContent).toContain('다른 글이 없습니다.'))
  expect(window.scrollTo).toHaveBeenCalledWith({ top: 0, left: 0 })
})

it('shows applied AI disclosure above the title and sources below the body without interpreting question HTML', async () => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(response({ post, contentMarkdown: '# Body', relatedPosts: [], aiAttribution: { draftId: 'id', question: '<img src=x onerror=alert(1)> 원래 질문', sources: [{ title: '실제 출처', url: 'https://example.com/docs' }] } }))))
  const { host } = await mount('/posts/123')
  await vi.waitFor(() => expect(host.querySelector('.ai-disclosure')).not.toBeNull())
  expect(host.querySelector('.ai-disclosure')?.textContent).toContain('원래 질문')
  expect(host.querySelector('.ai-disclosure img')).toBeNull()
  expect(host.querySelector('.ai-disclosure')!.compareDocumentPosition(host.querySelector('h1')!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  expect(host.querySelector('.post-body')!.compareDocumentPosition(host.querySelector('.ai-sources')!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  expect(host.querySelector('.ai-sources a')?.getAttribute('href')).toBe('https://example.com/docs')
})
