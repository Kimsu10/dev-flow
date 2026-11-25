// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, type App as VueApp } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { EditorView } from '@codemirror/view'
import { routes } from './router'
import App from './App.vue'
import { auth } from './auth'
let app: VueApp | undefined
async function mountApp(path = '/status') {
  const host = document.createElement('div')
  document.body.append(host)
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  app = createApp(App).use(router)
  app.mount(host)
  return { host, router }
}
afterEach(() => {
  app?.unmount()
  document.body.innerHTML = ''
  vi.unstubAllGlobals()
  auth.user = null; auth.ready = false
})
it.each([
  [200, { status: 'UP', database: 'UP' }, '정상 동작'],
  [503, { status: 'DOWN', database: 'DOWN' }, '데이터베이스 연결'],
  [200, {}, '연결할 수 없습니다'],
])('renders HTTP %s response', async (status, body, message) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })))
  const { host } = await mountApp()
  expect(host.querySelector('button')?.disabled).toBe(true)
  await vi.waitFor(() => expect(host.querySelector('[role="status"]')?.textContent).toContain(message))
})
it('retries after network failure', async () => {
  const fetchMock = vi.fn().mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'UP', database: 'UP' })))
  vi.stubGlobal('fetch', fetchMock)
  const { host } = await mountApp()
  await vi.waitFor(() => expect(host.textContent).toContain('연결할 수 없습니다'))
  host.querySelector('button')?.click()
  await vi.waitFor(() => expect(host.textContent).toContain('정상 동작'))
  expect(fetchMock).toHaveBeenCalledTimes(2)
})
it('keeps the editor off home and navigates to a blank create page for ADMIN', async () => {
  auth.user = { id: 1, username: 'admin', email: 'admin@example.test', role: 'ADMIN', status: 'ACTIVE' }; auth.ready = true
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: { categories: [] } }))))
  const { host, router } = await mountApp('/')
  expect(host.querySelector('.cm-editor')).toBeNull()
  const link = host.querySelector<HTMLAnchorElement>('a[href="/posts/create"]')!
  link.click()
  await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/posts/create'))
  await vi.waitFor(() => expect(host.querySelector('.cm-placeholder')?.textContent).toBe('글을 작성하세요'))
  const editor = EditorView.findFromDOM(host.querySelector<HTMLElement>('.cm-editor')!)!
  expect(editor.state.doc.toString()).toBe('')
  expect(host.querySelector('.md-preview article')?.innerHTML).toBe('')
  expect(host.querySelector<HTMLInputElement>('.md-title input')?.value).toBe('')
})
it('supports direct create navigation and the uncategorized option', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: [] }))); vi.stubGlobal('fetch', fetchMock)
  const { host } = await mountApp('/posts/create')
  const category = host.querySelector<HTMLInputElement>('#post-category')!
  expect(category.value).toBe('')
  expect(host.querySelector('#post-category option')?.textContent).toBe('카테고리')
  expect(host.querySelector('#post-tags')).toBeNull()
  const editor = EditorView.findFromDOM(host.querySelector<HTMLElement>('.cm-editor')!)!
  editor.dispatch({ changes: { from: 0, insert: '# 새 글' } })
  await vi.waitFor(() => expect(host.querySelector('.cm-placeholder')).toBeNull())
  expect(fetchMock).toHaveBeenCalledWith('/api/categories', expect.any(Object))
})
