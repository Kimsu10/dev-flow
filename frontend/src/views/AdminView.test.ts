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
  sessionStorage.clear()
  vi.resetAllMocks()
})

it('filters both queues and loads accounts independently when post loading fails', async () => {
  const agentDraft = { id: 1, title: 'Agent 검토 글', authorId: 9, authorRole: 'SYSTEM', status: 'IN_REVIEW', visibility: 'PUBLIC' }
  const ownDraft = { id: 2, title: '내 초안', authorId: 5, status: 'DRAFT', visibility: 'PUBLIC' }
  const scheduledPost = { id: 3, title: '다른 작성자의 예약 글', authorId: 8, authorName: 'Dev Writer', status: 'PUBLISHED', visibility: 'PUBLIC', publishedAt: '2099-01-01T00:00:00Z' }
  const accounts = [
    { id: 8, username: 'writer', email: 'writer@example.test', role: 'USER', status: 'ACTIVE' },
    { id: 9, username: 'review-bot', email: 'bot@example.test', role: 'SYSTEM', status: 'DISABLED' },
  ]
  let failPosts = true
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/admin/accounts') return accounts
    if (path === '/admin/posts/drafts' && failPosts) throw new Error('글 요청 실패')
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
  const accountList = () => host.querySelector('.account-list')?.textContent
  await vi.waitFor(() => expect(host.querySelector('[role="alert"]')?.textContent).toContain('글 요청 실패'))
  expect(accountList()).toContain('review-bot')
  const accountFilters = host.querySelectorAll<HTMLSelectElement>('[aria-labelledby="admin-accounts-heading"] select')
  accountFilters[0]!.value = 'SYSTEM'
  accountFilters[0]!.dispatchEvent(new Event('change'))
  await vi.waitFor(() => expect(accountList()).not.toContain('writer'))
  expect(accountList()).toContain('review-bot')
  failPosts = false
  host.querySelector<HTMLButtonElement>('[role="alert"] button')!.click()
  await vi.waitFor(() => expect(list()).toContain('Agent 검토 글'))
  expect(list()).toContain('검토 대기')
  expect(list()).not.toContain('내 초안')
  expect(list()).not.toContain('다른 작성자의 예약 글')

  const select = host.querySelector<HTMLSelectElement>('#admin-posts-heading + .admin-filters select')!
  select.selectedIndex = 1
  select.dispatchEvent(new Event('change'))
  await vi.waitFor(() => expect(list()).toContain('다른 작성자의 예약 글'))
  expect(list()).toContain('예약 공개')
  expect(list()).toContain('예약 취소')
  expect(list()).not.toContain('내 초안')
  const query = host.querySelector<HTMLInputElement>('[aria-labelledby="admin-posts-heading"] input')!
  query.value = '  DEV writer '
  query.dispatchEvent(new Event('input'))
  await vi.waitFor(() => expect(list()).not.toContain('Agent 검토 글'))
  expect(list()).toContain('예약 글')
  expect(vi.mocked(api).mock.calls.filter(([path]) => path === '/admin/accounts')).toHaveLength(1)

  const postStatus = host.querySelectorAll<HTMLSelectElement>('#admin-posts-heading + .admin-filters select')[1]!
  postStatus.value = 'scheduled'
  postStatus.dispatchEvent(new Event('change'))
  app.unmount()
  app = createApp(AdminView).use(router)
  app.mount(host)

  await vi.waitFor(() => expect(list()).toContain('예약 글'))
  expect(list()).not.toContain('Agent 검토 글')
  expect(accountList()).toContain('review-bot')
  expect(accountList()).not.toContain('writer')
  expect(host.querySelector<HTMLInputElement>('[aria-labelledby="admin-posts-heading"] input')?.value).toBe('')
  expect(sessionStorage.getItem('devflow:admin:filters:5')).not.toContain('DEV writer')

  const reset = Array.from(host.querySelectorAll('button')).find(button => button.textContent === '글 검색 조건 초기화')!
  reset.click()
  await vi.waitFor(() => expect(list()).toContain('Agent 검토 글'))
  expect(list()).not.toContain('예약 글')
})
