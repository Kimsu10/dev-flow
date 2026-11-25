// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, h, ref, nextTick, type App } from 'vue'
import CategoryInput from './CategoryInput.vue'
import AiDraftPanel from './AiDraftPanel.vue'
import { api } from '../../auth'
import type { Category } from '../../types/blog'
vi.mock('../../auth', () => ({ api: vi.fn() }))
let app: App | undefined
afterEach(() => { app?.unmount(); app = undefined; document.body.innerHTML = ''; vi.resetAllMocks() })
function button(host: HTMLElement, text: string) { return Array.from(host.querySelectorAll('button')).find(b => b.textContent === text)! }
function hostElement() { const host = document.createElement('div'); document.body.append(host); return host }
const draft = { id: '11111111-1111-4111-8111-111111111111', question: '원래 질문', contentMarkdown: '# 조사 결과\n\n검증할 초안', sources: [{ title: '공식 자료', url: 'https://example.com/docs' }] }
it('adds or reuses a category, emits immediate selection, and prevents duplicate submissions', async () => {
  let resolve!: (v: Category) => void
  vi.mocked(api).mockImplementation(() => new Promise(done => { resolve = done as typeof resolve }))
  const host = hostElement(), selected = ref<number | null>(null), categories = ref<Category[]>([{ id: 1, name: 'Vue', slug: 'vue', sortOrder: 0 }])
  app = createApp({ render: () => h(CategoryInput, { categories: categories.value, modelValue: selected.value,
    'onUpdate:modelValue': (id: number | null) => { selected.value = id }, onAdded: (c: Category) => { categories.value = [...categories.value.filter(p => p.id !== c.id), c] },
  }) }); app.mount(host)
  host.querySelector<HTMLButtonElement>('[aria-label="카테고리 추가"]')!.click(); await nextTick()
  const input = host.querySelector<HTMLInputElement>('#new-category-name')!
  input.value = 'Vue'; input.dispatchEvent(new Event('input')); await nextTick()
  host.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
  host.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
  expect(api).toHaveBeenCalledTimes(1)
  resolve({ id: 1, name: 'Vue', slug: 'vue', sortOrder: 0 })
  await vi.waitFor(() => expect(selected.value).toBe(1))
  expect(host.querySelectorAll('option')).toHaveLength(2)
  expect(host.querySelector<HTMLSelectElement>('select')?.value).toBe('1')
})
it('keeps AI optional, requires disclosure consent and applies only after preview approval', async () => {
  vi.mocked(api).mockResolvedValue(draft)
  const host = hostElement(), apply = vi.fn()
  app = createApp(AiDraftPanel, { onApply: apply }); app.mount(host)
  expect(api).not.toHaveBeenCalled()
  button(host, 'AI 글 작성').click(); await nextTick()
  expect(host.textContent).toContain('원래 질문과 AI 작성 안내, 출처 링크가 공개됩니다')
  const question = host.querySelector<HTMLTextAreaElement>('#ai-question')!
  question.value = draft.question; question.dispatchEvent(new Event('input')); await nextTick()
  expect(button(host, '자료 조사하고 초안 만들기').disabled).toBe(true)
  const consent = host.querySelector<HTMLInputElement>('input[type="checkbox"]')!
  consent.checked = true; consent.dispatchEvent(new Event('change')); await nextTick()
  host.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
  await vi.waitFor(() => expect(host.querySelector('.ai-draft-result')).not.toBeNull())
  expect(host.querySelector('.md-preview h1')?.textContent).toBe('조사 결과')
  expect(host.querySelector('.ai-sources a')?.getAttribute('href')).toBe(draft.sources[0]!.url)
  expect(apply).not.toHaveBeenCalled()
  question.value = '나중에 바꾼 질문'; question.dispatchEvent(new Event('input')); await nextTick()
  button(host, '편집기에 적용').click()
  expect(apply).toHaveBeenCalledWith(draft)
  expect(api).toHaveBeenCalledWith('/admin/ai-drafts', 'POST', { question: draft.question, publicQuestionAccepted: true }, expect.any(AbortSignal))
})

it('leaves the editor untouched on AI failures and cancels pending transport on unmount', async () => {
  vi.mocked(api).mockRejectedValueOnce(new Error('AI 연결이 설정되지 않았습니다.'))
  const host = hostElement(), apply = vi.fn()
  app = createApp(AiDraftPanel, { onApply: apply }); app.mount(host)
  button(host, 'AI 글 작성').click(); await nextTick()
  const input = host.querySelector<HTMLTextAreaElement>('#ai-question')!
  input.value = '질문'; input.dispatchEvent(new Event('input'))
  const consent = host.querySelector<HTMLInputElement>('input[type="checkbox"]')!
  consent.checked = true; consent.dispatchEvent(new Event('change')); await nextTick()
  host.querySelector('form')!.dispatchEvent(new Event('submit'))
  await vi.waitFor(() => expect(host.querySelector('[role="alert"]')?.textContent).toContain('설정되지 않았습니다'))
  expect(apply).not.toHaveBeenCalled()
  vi.mocked(api).mockImplementation(() => new Promise(() => {}))
  host.querySelector('form')!.dispatchEvent(new Event('submit'))
  const signal = vi.mocked(api).mock.calls.at(-1)?.[3]
  app.unmount(); app = undefined
  expect(signal?.aborted).toBe(true)
})
