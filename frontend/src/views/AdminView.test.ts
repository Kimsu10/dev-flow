// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
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

  const accountsSection = host.querySelector('[aria-labelledby="admin-users-heading"]')!
  const analyticsSection = host.querySelector('[aria-labelledby="admin-analytics-heading"]')!
  const postsSection = host.querySelector('[aria-labelledby="admin-posts-heading"]')!
  const accountsPanel = host.querySelector<HTMLElement>('#admin-accounts')!
  const accountsButton = host.querySelector<HTMLButtonElement>('[aria-controls="admin-accounts"]')!
  expect(accountsSection.nextElementSibling).toBe(analyticsSection)
  expect(analyticsSection.parentElement?.nextElementSibling).toBe(postsSection)
  const analyticsLink = analyticsSection.querySelector('h2 + a')
  expect(analyticsLink?.getAttribute('href')).toBe('/admin/analytics')
  expect(analyticsLink?.textContent).toBe('이동하기')
  expect(analyticsSection.querySelector('p')).toBeNull()
  expect(accountsSection.querySelector('h2 + button')).toBe(accountsButton)
  expect(accountsButton.nextElementSibling).toBeNull()
  expect(host.querySelector('.blog-header a[href="/admin/analytics"]')).toBeNull()
  expect(host.querySelector('.admin-heading nav')).toBeNull()
  expect(accountsPanel.style.display).toBe('none')
  expect(accountsButton.getAttribute('aria-expanded')).toBe('false')
  accountsButton.click()
  await nextTick()
  expect(accountsPanel.style.display).toBe('')
  expect(accountsButton.getAttribute('aria-expanded')).toBe('true')

  const list = () => host.querySelector('.admin-posts')?.textContent
  const accountList = () => host.querySelector('.account-list')?.textContent
  await vi.waitFor(() => expect(host.querySelector('[role="alert"]')?.textContent).toContain('글 요청 실패'))
  expect(accountList()).toContain('review-bot')
  expect(accountsPanel.querySelector('.deactivate-account')?.textContent).toBe('비활성화')
  expect(accountsPanel.querySelector('[aria-label="writer 비밀번호 변경"]')).not.toBeNull()
  expect(accountsPanel.querySelector('[aria-label="review-bot 비밀번호 변경"]')).toBeNull()
  const accountFilters = accountsPanel.querySelectorAll<HTMLSelectElement>('.admin-filters select')
  accountFilters[0]!.value = 'SYSTEM'
  accountFilters[0]!.dispatchEvent(new Event('change'))
  await vi.waitFor(() => expect(accountList()).not.toContain('writer'))
  expect(accountList()).toContain('review-bot')
  accountsButton.click()
  await nextTick()
  expect(accountsPanel.style.display).toBe('none')
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

it('checks password confirmation, reports errors, and clears the form after a successful change', async () => {
  const targetPath = '/admin/accounts/8/password'
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/admin/accounts') return [
      { id: 5, username: 'owner', role: 'ADMIN', status: 'ACTIVE' },
      { id: 8, username: 'writer', role: 'USER', status: 'ACTIVE' },
      { id: 9, username: 'other-admin', role: 'ADMIN', status: 'ACTIVE' },
    ]
    if (path === targetPath) throw new Error('현재 비밀번호가 올바르지 않습니다.')
    return []
  })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/admin', component: AdminView }] })
  await router.push('/admin')
  const host = document.createElement('div')
  document.body.append(host)
  app = createApp(AdminView).use(router)
  app.mount(host)

  const section = host.querySelector<HTMLDialogElement>('dialog[aria-labelledby="admin-password-heading"]')!
  // jsdom lacks the native dialog methods.
  section.showModal = () => { section.open = true }
  section.close = () => {
    section.open = false
    section.dispatchEvent(new Event('close'))
  }
  host.querySelector<HTMLButtonElement>('[aria-controls="admin-accounts"]')!.click()
  const passwordButton = (user: string) => host.querySelector<HTMLButtonElement>(`[aria-label="${user} 비밀번호 변경"]`)
  await vi.waitFor(() => expect(passwordButton('writer')).not.toBeNull())
  expect(passwordButton('other-admin')).toBeNull()
  const openPassword = passwordButton('writer')!
  expect(section.open).toBe(false)
  openPassword.click()
  await nextTick()
  expect(section.open).toBe(true)
  expect(section.textContent).toContain('writer 비밀번호 변경')
  expect(section.textContent).toContain('내 관리자 비밀번호')
  const form = section.querySelector('form')!
  const inputs = [...form.querySelectorAll('input')]
  const [current, next, confirmation] = inputs as [HTMLInputElement, HTMLInputElement, HTMLInputElement]
  const newValue = crypto.randomUUID()
  const setPassword = (input: HTMLInputElement, value: string) => {
    input.value = value
    input.dispatchEvent(new Event('input'))
  }
  const submit = () => form.dispatchEvent(new Event('submit', { cancelable: true }))
  const requests = () => vi.mocked(api).mock.calls.filter(([path]) => path === targetPath)

  setPassword(current, crypto.randomUUID())
  setPassword(next, newValue)
  setPassword(confirmation, crypto.randomUUID())
  submit()
  await vi.waitFor(() => expect(section.querySelector('[role="alert"]')?.textContent).toContain('일치하지 않습니다'))
  expect(requests().length).toBe(0)

  setPassword(confirmation, newValue)
  submit()
  await vi.waitFor(() => expect(section.querySelector('[role="alert"]')?.textContent).toContain('현재 비밀번호가 올바르지 않습니다'))
  expect(current.value.length).toBe(0)
  expect(next.value === newValue).toBe(true)

  vi.mocked(api).mockResolvedValueOnce(null)
  const currentValue = crypto.randomUUID()
  setPassword(current, currentValue)
  submit()
  await vi.waitFor(() => expect(section.querySelector('[role="status"]')?.textContent).toContain('비밀번호를 변경했습니다'))
  expect(requests().map(([, method]) => method)).toEqual(['PATCH', 'PATCH'])
  const body = requests()[1]?.[2] as { currentPassword: string; newPassword: string }
  expect(body.currentPassword === currentValue && body.newPassword === newValue).toBe(true)
  expect(inputs.every(input => input.value === '')).toBe(true)
  form.querySelector<HTMLButtonElement>('button[type="button"]')!.click()
  expect(section.open).toBe(false)
  await vi.waitFor(() => expect(section.querySelector('[role="status"]')).toBeNull())
  passwordButton('owner')!.click()
  await nextTick()
  expect(section.open).toBe(true)
  expect(section.textContent).toContain('owner 비밀번호 변경')
  expect(inputs.every(input => input.value === '')).toBe(true)
  setPassword(current, crypto.randomUUID())
  section.dispatchEvent(new Event('cancel', { cancelable: true }))
  expect(section.open).toBe(false)
  await nextTick()
  expect(current.value.length).toBe(0)
})
