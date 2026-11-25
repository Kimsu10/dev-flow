// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import HomeAiPublisher from './HomeAiPublisher.vue'
import { api } from '../../auth'
vi.mock('../../auth', () => ({ api: vi.fn() }))
let app: App | undefined
const draft = { id: '11111111-1111-4111-8111-111111111111', question: 'Question\nMore context', contentMarkdown: '# Generated title\n\nBody', sources: [{ title: 'Docs', url: 'https://example.com/docs' }] }
beforeEach(() => vi.mocked(api).mockImplementation(async path => path === '/categories' ? [{ id: 25, name: 'Spring Boot' }] : path === '/admin/ai-drafts' ? draft : { id: 123 }))
afterEach(() => { app?.unmount(); app = undefined; document.body.innerHTML = ''; vi.resetAllMocks() })
async function mount() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: HomeAiPublisher }, { path: '/posts/:id', name: 'post-detail', component: { template: '<p>Detail</p>' } }] })
  await router.push('/')
  const host = document.createElement('div'); document.body.append(host)
  app = createApp({ template: '<router-view />' }).use(router); app.mount(host)
  await vi.waitFor(() => expect(host.querySelectorAll('option')).toHaveLength(2))
  const question = host.querySelector('textarea')!; question.value = draft.question; question.dispatchEvent(new Event('input'))
  const category = host.querySelector('select')!; category.value = '25'; category.dispatchEvent(new Event('change')); await nextTick()
  return { host, router }
}
it('researches and publishes from multiline input, retaining provenance and category', async () => {
  const { host, router } = await mount()
  expect(host.textContent).toContain('즉시 공개됩니다')
  host.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
  await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/posts/123'))
  expect(api).toHaveBeenCalledWith('/admin/ai-drafts', 'POST', { question: draft.question, publicQuestionAccepted: true }, expect.any(AbortSignal))
  expect(api).toHaveBeenCalledWith('/admin/posts', 'POST', expect.objectContaining({ title: 'Generated title', contentMarkdown: draft.contentMarkdown, aiDraftId: draft.id, categoryId: 25, visibility: 'PUBLIC' }), expect.any(AbortSignal))
})
it('does not publish unverified AI output and retains the input after failure', async () => {
  const { host, router } = await mount()
  vi.mocked(api).mockResolvedValueOnce({ ...draft, sources: [] })
  host.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
  await vi.waitFor(() => expect(host.querySelector('[role="alert"]')).not.toBeNull())
  expect(vi.mocked(api).mock.calls.filter(([path]) => path === '/admin/posts')).toHaveLength(0)
  expect(host.querySelector('textarea')?.value).toBe(draft.question)
  expect(router.currentRoute.value.path).toBe('/')
})
it('reuses completed research when publication fails instead of charging for research again', async () => {
  const { host } = await mount()
  vi.mocked(api).mockImplementation(async path => { if (path === '/admin/ai-drafts') return draft; throw new Error('Publish failed') })
  host.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
  await vi.waitFor(() => expect(host.querySelector('[role="alert"]')?.textContent).toBe('Publish failed'))
  vi.mocked(api).mockResolvedValue({ id: 123 })
  host.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
  await vi.waitFor(() => expect(vi.mocked(api).mock.calls.filter(([path]) => path === '/admin/posts')).toHaveLength(2))
  expect(vi.mocked(api).mock.calls.filter(([path]) => path === '/admin/ai-drafts')).toHaveLength(1)
})
