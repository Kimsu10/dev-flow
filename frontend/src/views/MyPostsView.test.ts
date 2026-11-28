// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, type App } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import MyPostsView from './MyPostsView.vue'
import { api, auth } from '../auth'
vi.mock('../auth', () => ({ api: vi.fn(), auth: { user: { id: 5, role: 'ADMIN' } }, logout: vi.fn() }))
let app: App | undefined
afterEach(() => { app?.unmount(); document.body.innerHTML = ''; auth.user!.role = 'ADMIN'; vi.restoreAllMocks(); vi.resetAllMocks() })
it('offers users only their own edit and confirmed delete controls through user endpoints', async () => {
  auth.user!.role = 'USER'
  let deleted = false
  vi.mocked(api).mockImplementation(async (path, method) => {
    if (method === 'DELETE') { expect(path).toBe('/user/posts/7'); deleted = true; return {} }
    expect(path).toBe('/user/posts')
    return deleted ? [] : [{ id: 7, title: 'Trial post', status: 'PUBLISHED', visibility: 'PUBLIC', publishedAt: '2020-01-01T00:00:00Z', aiDraftId: 'trial-ai' }]
  })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/my-posts', component: MyPostsView }] })
  await router.push('/my-posts')
  const host = document.createElement('div'); document.body.append(host)
  app = createApp({ template: '<router-view />' }).use(router); app.mount(host)
  await vi.waitFor(() => expect(host.querySelector('.my-posts article')).not.toBeNull())
  expect(host.querySelector('a[href="/posts/create"]')).toBeNull()
  expect(host.querySelector('a[href="/my-posts/7/edit"]')).not.toBeNull()
  expect([...host.querySelectorAll('.my-posts button')].map(b => b.textContent)).toEqual(['삭제'])
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  host.querySelector<HTMLButtonElement>('.my-posts button')!.click()
  await vi.waitFor(() => expect(host.querySelector('.my-posts article')).toBeNull())
})
it('manages a post through publication, withdrawal and confirmed deletion', async () => {
  let visibility = 'PRIVATE'
  let status = 'PUBLISHED'
  let deleted = false
  vi.mocked(api).mockImplementation(async (path,method) => {
    if (method === 'PATCH') {
      if (path.includes('/unpublish')) status = 'DRAFT'
      else visibility = 'PUBLIC'
      return {}
    }
    if (method === 'DELETE') { deleted = true; return {} }
    expect(path).toBe('/admin/posts/mine')
    return deleted ? [] : [{ id: 123, title: 'My draft', status, visibility, publishedAt: '2020-01-01T00:00:00Z', version: 2, aiDraftId: 'AI-source' }]
  })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/my-posts', component: MyPostsView }] })
  await router.push('/my-posts');await router.isReady()
  const host = document.createElement('div');document.body.append(host)
  app = createApp(MyPostsView).use(router);app.mount(host)
  const findButton = (label: string) => Array.from(host.querySelectorAll('button')).find(button => button.textContent === label)!
  await vi.waitFor(() => expect(host.querySelector('.my-posts strong')?.textContent).toBe('비공개'))
  expect(host.querySelector('.my-posts a')?.getAttribute('href')).toBe('/admin/posts/123/edit')
  expect(host.textContent).toContain('AI 원래 질문과 출처도 함께 공개')
  findButton('공개로 전환').click()
  await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/admin/posts/123/publish?version=2','PATCH'))
  await vi.waitFor(() => expect(host.querySelector('.my-posts strong')?.textContent).toBe('공개 중'))
  findButton('발행 취소').click()
  await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/admin/posts/123/unpublish?version=2', 'PATCH'))
  await vi.waitFor(() => expect(host.querySelector('.my-posts strong')?.textContent).toBe('미발행'))
  const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
  findButton('삭제').click()
  expect(api).not.toHaveBeenCalledWith('/admin/posts/123', 'DELETE')
  findButton('삭제').click()
  expect(confirm).toHaveBeenCalledWith(expect.stringContaining('My draft'))
  await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/admin/posts/123', 'DELETE'))
  await vi.waitFor(() => expect(host.textContent).toContain('아직 작성한 글이 없습니다.'))
})
