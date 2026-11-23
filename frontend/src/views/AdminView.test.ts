// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, type App } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminView from './AdminView.vue'
import { api } from '../auth'

vi.mock('../auth', () => ({ api: vi.fn(), auth: { user: { id: 5, role: 'ADMIN' } }, logout: vi.fn() }))

let app: App | undefined
afterEach(() => {
  app?.unmount()
  document.body.innerHTML = ''
  vi.resetAllMocks()
})

it('opens the Agent queue and keeps personal posts out of both operations lists', async () => {
  const agentDraft = { id: 1, title: 'Agent 검토 글', authorId: 9, authorRole: 'SYSTEM', status: 'IN_REVIEW', visibility: 'PUBLIC' }
  const ownDraft = { id: 2, title: '내 초안', authorId: 5, status: 'DRAFT', visibility: 'PUBLIC' }
  const scheduledPost = { id: 3, title: '다른 작성자의 예약 글', authorId: 8, status: 'PUBLISHED', visibility: 'PUBLIC', publishedAt: '2099-01-01T00:00:00Z' }
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/admin/posts/drafts') return [agentDraft, ownDraft]
    if (path === '/admin/posts') return [agentDraft, ownDraft, scheduledPost]
    return []
  })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/admin', component: AdminView }] })
  await router.push('/admin')
  await router.isReady()
  const host = document.createElement('div')
  document.body.append(host)
  app = createApp(AdminView).use(router)
  app.mount(host)

  const list = () => host.querySelector('.admin-posts')?.textContent
  await vi.waitFor(() => expect(list()).toContain('Agent 검토 글'))
  expect(list()).toContain('검토 대기')
  expect(list()).not.toContain('내 초안')
  expect(list()).not.toContain('다른 작성자의 예약 글')

  const select = host.querySelector<HTMLSelectElement>('#admin-posts-heading + label select')!
  select.selectedIndex = 1
  select.dispatchEvent(new Event('change'))
  await vi.waitFor(() => expect(list()).toContain('다른 작성자의 예약 글'))
  expect(list()).toContain('예약 공개')
  expect(list()).toContain('예약 취소')
  expect(list()).not.toContain('내 초안')
})
