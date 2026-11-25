// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import PostDetailView from './PostDetailView.vue'
import { api, auth } from '../auth'

vi.mock('../auth', async () => {
  const { reactive } = await import('vue')
  return { api: vi.fn(), auth: reactive({ user: { id: 5, role: 'ADMIN' }, ready: true }), logout: vi.fn() }
})
vi.mock('../analytics/tracking', () => ({ usePageView: vi.fn() }))
vi.mock('../components/blog/AnalyticsConsent.vue', () => ({ default: { template: '<div />' } }))
let app: App | undefined
const detail = (id = 1) => ({ post: { id, title: `Post ${id}`, summary: '', createdAt: '2020-01-01Z', publishedAt: '2020-01-01Z', category: null }, contentMarkdown: 'Body', visibility: 'PUBLIC', status: 'PUBLISHED', canEdit: true, canDelete: true, relatedPosts: [], aiAttribution: null })
beforeEach(() => {
  auth.user = { id: 5, role: 'ADMIN' } as typeof auth.user
  vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify({ data: detail(Number(url.match(/posts\/(\d+)/)?.[1] || 1)) }))))
  vi.stubGlobal('scrollTo', vi.fn())
  vi.mocked(api).mockReset()
})
afterEach(() => { app?.unmount(); app = undefined; document.body.innerHTML = ''; vi.restoreAllMocks(); vi.unstubAllGlobals() })
async function mount(path = '/posts/1') {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/posts/:id', component: PostDetailView },
    { path: '/my-posts', component: { template: '<p>My posts</p>' } },
    { path: '/admin/posts/:id/edit', component: { template: '<p>Editor</p>' } },
    { path: '/:pathMatch(.*)*', component: { template: '<p>Other</p>' } },
  ] })
  await router.push(path)
  const host = document.createElement('div'); document.body.append(host)
  app = createApp({ template: '<router-view />' }).use(router); app.mount(host)
  await vi.waitFor(() => expect(host.querySelector('.post-body')).not.toBeNull())
  return { host, router }
}
it('shows permitted management links and supports editing', async () => {
  const { host, router } = await mount()
  const edit = host.querySelector<HTMLAnchorElement>('.post-management a')!
  expect(edit.textContent).toBe('글 수정')
  expect(edit.getAttribute('href')).toBe('/admin/posts/1/edit')
  edit.click()
  await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/admin/posts/1/edit'))
})
it.each(['USER', 'SYSTEM', null])('hides management controls for %s even with stale server flags', async role => {
  auth.user = (role ? { id: 5, role } : null) as typeof auth.user
  const { host } = await mount()
  expect(host.querySelector('.post-management')).toBeNull()
})
it('respects server-denied permissions for administrators', async () => {
  vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ data: { ...detail(), canEdit: false, canDelete: false } })))
  const { host } = await mount()
  expect(host.querySelector('.post-management')).toBeNull()
})
it('verifies the existing admin endpoint when an older backend omits capability flags', async () => {
  vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ data: { ...detail(), canEdit: undefined, canDelete: undefined } })))
  vi.mocked(api).mockResolvedValue({ id: 1 })
  const { host } = await mount()
  await vi.waitFor(() => expect(host.querySelector('.post-management')).not.toBeNull())
  expect(api).toHaveBeenCalledWith('/admin/posts/1', 'GET', undefined, expect.any(AbortSignal))
})
it('does not infer permission from ADMIN when the legacy management endpoint denies it', async () => {
  vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ data: { ...detail(), canEdit: undefined, canDelete: undefined } })))
  vi.mocked(api).mockRejectedValue(new Error('Forbidden'))
  const { host } = await mount()
  await nextTick()
  expect(host.querySelector('.post-management')).toBeNull()
})
it('does not delete when confirmation is cancelled', async () => {
  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
  const { host, router } = await mount()
  host.querySelector<HTMLButtonElement>('.post-management button')!.click()
  expect(confirm).toHaveBeenCalledWith('“Post 1” 글을 삭제하시겠습니까?')
  expect(api).not.toHaveBeenCalled()
  expect(router.currentRoute.value.path).toBe('/posts/1')
})
it('prevents duplicate deletes and navigates only after success', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  let complete!: () => void
  vi.mocked(api).mockImplementation(() => new Promise(resolve => { complete = () => resolve(undefined) }))
  const { host, router } = await mount()
  const button = host.querySelector<HTMLButtonElement>('.post-management button')!
  button.click(); button.click(); await nextTick()
  expect(button.disabled).toBe(true)
  expect(host.querySelector('.post-management a')?.getAttribute('aria-disabled')).toBe('true')
  expect(api).toHaveBeenCalledExactlyOnceWith('/admin/posts/1', 'DELETE')
  expect(router.currentRoute.value.path).toBe('/posts/1')
  complete()
  await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/my-posts'))
})
it('keeps the article and allows retry when deletion fails', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  vi.mocked(api).mockRejectedValue(new Error('Permission changed'))
  const { host, router } = await mount()
  host.querySelector<HTMLButtonElement>('.post-management button')!.click()
  await vi.waitFor(() => expect(host.querySelector('[role="alert"]')?.textContent).toBe('Permission changed'))
  expect(host.querySelector('.post-body')?.textContent).toContain('Body')
  expect(host.querySelector<HTMLButtonElement>('.post-management button')!.disabled).toBe(false)
  expect(router.currentRoute.value.path).toBe('/posts/1')
})
it('does not redirect a later detail visit when an earlier deletion completes', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  let complete!: () => void
  vi.mocked(api).mockImplementation(() => new Promise(resolve => { complete = () => resolve(undefined) }))
  const { host, router } = await mount()
  host.querySelector<HTMLButtonElement>('.post-management button')!.click()
  await router.push('/posts/2')
  await vi.waitFor(() => expect(host.querySelector('h1')?.textContent).toBe('Post 2'))
  complete(); await nextTick(); await nextTick()
  expect(router.currentRoute.value.path).toBe('/posts/2')
})
it('requests an explicit preview and explains unpublished state without changing it', async () => {
  vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ data: { ...detail(), status: 'DRAFT' } })))
  const { host } = await mount('/posts/1?preview=true')
  expect(fetch).toHaveBeenCalledWith('/api/posts/1?preview=true', expect.anything())
  expect(host.textContent).toContain('미발행 글 미리보기')
  expect(api).not.toHaveBeenCalled()
})
