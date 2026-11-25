// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import RelatedPosts from './RelatedPosts.vue'
import { getRelatedPosts } from '../../api/blog'
vi.mock('../../api/blog', () => ({ getRelatedPosts: vi.fn() }))
vi.mock('./PostCard.vue', () => ({ default: { props: ['post'], template: '<article class="post-card">{{ post.title }}</article>' } }))
let app: App | undefined
afterEach(() => { app?.unmount(); app = undefined; document.body.innerHTML = ''; vi.resetAllMocks() })
async function mount() {
  const host = document.createElement('div'); document.body.append(host)
  app = createApp(RelatedPosts, { postId: 1, initialPosts: [], preview: false }); app.mount(host)
  await vi.waitFor(() => expect(host.querySelector('[aria-busy]')?.getAttribute('aria-busy')).toBe('false'))
  return host
}
const post = (id: number) => ({ id, title: `Post ${id}` })
it('moves in both directions, excludes the current post and disables both page boundaries', async () => {
  vi.mocked(getRelatedPosts).mockImplementation(async (_id, page) => ({ items: (page ? [post(5)] : [post(1), post(2), post(3)]) as never[], page, size: 3, totalElements: 4, totalPages: 2 }))
  const host = await mount()
  const [previous, next] = host.querySelectorAll<HTMLButtonElement>('.related-arrow')
  expect(previous!.disabled).toBe(true); expect(next!.disabled).toBe(false)
  expect(host.querySelector('.post-grid')?.textContent).not.toContain('Post 1')
  next!.click()
  await vi.waitFor(() => expect(host.textContent).toContain('Post 5'))
  expect(next!.disabled).toBe(true); expect(previous!.disabled).toBe(false)
  previous!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }))
  await vi.waitFor(() => expect(host.textContent).toContain('Post 2'))
  expect(previous!.disabled).toBe(true)
  expect(getRelatedPosts).toHaveBeenCalledWith(1, 1, expect.any(AbortSignal), false, 3)
})
it('explains an empty list and disables both arrows', async () => {
  vi.mocked(getRelatedPosts).mockResolvedValue({ items: [], page: 0, size: 3, totalElements: 0, totalPages: 0 })
  const host = await mount()
  expect(host.textContent).toContain('같은 카테고리의 다른 글이 없습니다.')
  expect([...host.querySelectorAll<HTMLButtonElement>('.related-arrow')].every(b => b.disabled)).toBe(true)
})
it('keeps existing cards and page number visible while a delayed next page loads', async () => {
  vi.mocked(getRelatedPosts).mockResolvedValue({ items: [post(2)] as never[], page: 0, size: 3, totalElements: 4, totalPages: 2 })
  const host = await mount()
  let finish!: (value: Awaited<ReturnType<typeof getRelatedPosts>>) => void
  vi.mocked(getRelatedPosts).mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  host.querySelector<HTMLButtonElement>('[aria-label="다음 관련 글"]')!.click()
  await nextTick()
  expect(host.querySelector('[aria-busy]')?.getAttribute('aria-busy')).toBe('true')
  expect(host.querySelector('.post-card')?.textContent).toBe('Post 2')
  expect(host.querySelector('.related-pagination')?.textContent).toContain('1 / 2')
  expect([...host.querySelectorAll<HTMLButtonElement>('.related-arrow')].every(b => b.disabled)).toBe(true)
  finish({ items: [post(5)] as never[], page: 1, size: 3, totalElements: 4, totalPages: 2 })
  await vi.waitFor(() => expect(host.querySelector('.post-card')?.textContent).toBe('Post 5'))
})
it('retains the current page after a failed navigation and supports retry', async () => {
  vi.mocked(getRelatedPosts).mockResolvedValue({ items: [post(2)] as never[], page: 0, size: 3, totalElements: 4, totalPages: 2 })
  const host = await mount()
  vi.mocked(getRelatedPosts).mockRejectedValueOnce(new Error('Offline'))
  host.querySelector<HTMLButtonElement>('[aria-label="다음 관련 글"]')!.click()
  await vi.waitFor(() => expect(host.querySelector('[role="alert"]')).not.toBeNull())
  expect(host.textContent).toContain('Post 2')
  host.querySelector<HTMLButtonElement>('[role="alert"] button')!.click()
  await vi.waitFor(() => expect(host.querySelector('[role="alert"]')).toBeNull())
  await nextTick()
})
