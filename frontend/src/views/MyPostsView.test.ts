// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, type App } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import MyPostsView from './MyPostsView.vue'
import { api } from '../auth'
vi.mock('../auth', () => ({ api: vi.fn(), auth: { user: { id: 5, role: 'ADMIN' } }, logout: vi.fn() }))
let app: App | undefined
afterEach(() => { app?.unmount(); document.body.innerHTML = ''; vi.resetAllMocks() })
it('marks private posts, links to editing and refreshes after explicit public conversion', async () => {
  let visibility = 'PRIVATE'
  vi.mocked(api).mockImplementation(async (path,method) => {
    if (method === 'PATCH') { visibility = 'PUBLIC'; return {} }
    expect(path).toBe('/admin/posts/mine')
    return [{ id: 123, title: 'My draft', status: 'PUBLISHED', visibility, publishedAt: '2020-01-01T00:00:00Z', version: 2, aiDraftId: 'AI-source' }]
  })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/my-posts', component: MyPostsView }] })
  await router.push('/my-posts');await router.isReady()
  const host = document.createElement('div');document.body.append(host)
  app = createApp(MyPostsView).use(router);app.mount(host)
  await vi.waitFor(() => expect(host.querySelector('.my-posts')?.textContent).toContain('비공개 · 작성자만'))
  expect(host.querySelector('.my-posts a')?.getAttribute('href')).toBe('/admin/posts/123/edit')
  expect(host.textContent).toContain('AI 원래 질문과 출처도 함께 공개')
  Array.from(host.querySelectorAll('button')).find(b => b.textContent === '공개로 전환')!.click()
  await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/admin/posts/123/publish?version=2','PATCH'))
  await vi.waitFor(() => expect(host.querySelector('.my-posts strong')?.textContent).toBe('공개'))
})
