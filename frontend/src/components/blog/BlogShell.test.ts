// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import BlogShell from './BlogShell.vue'
import { auth, logout } from '../../auth'

vi.mock('../../auth', async () => {
  const { reactive } = await import('vue')
  return { auth: reactive({ user: null, ready: false }), logout: vi.fn().mockResolvedValue(undefined) }
})
let app: App | undefined
afterEach(() => { app?.unmount(); document.body.innerHTML = ''; auth.user = null; auth.ready = false; vi.clearAllMocks() })
async function mount(role?: 'ADMIN' | 'USER', ready = true) {
  auth.ready = ready
  auth.user = role ? { id: 1, username: 'nav-test', email: 'nav@example.test', role, status: 'ACTIVE' } : null
  const router = createRouter({ history: createMemoryHistory(), routes: ['/', '/posts', '/my-posts', '/admin', '/admin/analytics', '/posts/create', '/login', '/status'].map(path => ({ path, name: path === '/posts' ? 'post-list' : undefined, component: { template: '<div />' } })) })
  await router.push('/');await router.isReady()
  const host = document.createElement('div');document.body.append(host)
  app = createApp(BlogShell).use(router);app.mount(host)
  return { host, router }
}
it('preserves administrator destinations while separating navigation and account actions', async () => {
  const { host, router } = await mount('ADMIN')
  expect([...host.querySelectorAll('.blog-primary-nav a')].map(a => a.getAttribute('href'))).toEqual(['/my-posts', '/admin'])
  expect(host.querySelector('.blog-header a[href="/admin/analytics"]')).toBeNull()
  expect(host.querySelector('.blog-header a[href="/posts"]')).toBeNull()
  expect(host.querySelector('.blog-account-actions a')?.getAttribute('href')).toBe('/posts/create')
  expect(host.querySelector('.blog-account-actions button')?.textContent).toBe('로그아웃')
  await router.push('/admin');await nextTick()
  expect(host.querySelectorAll('.blog-primary-nav .is-current')).toHaveLength(1)
  expect(host.querySelector('.blog-primary-nav .is-current')?.textContent).toBe('관리자')
})
it('keeps regular users out of administrator menus and retains logout behavior', async () => {
  const { host } = await mount('USER')
  expect(host.querySelector('.blog-primary-nav')).toBeNull()
  expect(host.querySelector('.blog-account-actions a')).toBeNull()
  host.querySelector<HTMLButtonElement>('.blog-account-actions button')!.click()
  expect(logout).toHaveBeenCalledOnce()
})

it('submits the accessible header search to posts and restores the query on navigation', async () => {
  const { host, router } = await mount('USER')
  const input = host.querySelector<HTMLInputElement>('#header-post-search')!
  const submit = host.querySelector<HTMLButtonElement>('.blog-header-search button')!
  expect(submit.type).toBe('submit')
  expect(submit.getAttribute('aria-label')).toBe('글 검색')
  expect(submit.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true')
  input.value = ' Java & Vue ';input.dispatchEvent(new Event('input'));await nextTick()
  host.querySelector('.blog-header-search')!.dispatchEvent(new Event('submit', { cancelable: true }))
  await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/posts?q=Java+%26+Vue'))
  await router.push('/posts?q=Spring');await nextTick()
  expect(input.value).toBe('Spring')
  await router.back()
  await vi.waitFor(() => expect(input.value).toBe('Java & Vue'))
})
it('shows login only after authentication restoration finishes', async () => {
  const { host } = await mount(undefined, false)
  expect(host.querySelector('.blog-account-actions')).toBeNull()
  auth.ready = true;await nextTick()
  expect(host.querySelector('.blog-account-actions a')?.getAttribute('href')).toBe('/login')
  expect(host.querySelector('.blog-account-actions button')).toBeNull()
})
