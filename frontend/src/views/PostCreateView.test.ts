// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App as VueApp } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../App.vue'
import { routes } from '../router'
import { api, ApiError } from '../auth'

vi.mock('../auth', () => ({ api: vi.fn(), auth: { user: { id: 5, role: 'ADMIN' } }, restoreAuth: vi.fn(), ApiError: class extends Error {
  code?: string
  constructor(_status: number, code?: string) { super('Edit conflict'); this.code = code }
} }))
vi.mock('../components/editor/MarkdownEditorLayout.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return { default: defineComponent({ props: ['modelValue', 'title'], emits: ['update:title', 'update:modelValue', 'save', 'publish'], setup(props, { emit, slots }) {
    return () => h('div', [h('input', { class: 'test-title', value: props.title, onInput: (e: Event) => emit('update:title', (e.target as HTMLInputElement).value) }), h('textarea', { class: 'test-markdown', value: props.modelValue, onInput: (e: Event) => emit('update:modelValue', (e.target as HTMLTextAreaElement).value) }), h('div', { class: 'md-actions' }, slots.actions?.({ uploading: false })), h('button', { class: 'test-save', onClick: () => emit('save') }, 'Save')])
  } }) }
})
let app: VueApp | undefined
const post = (id: number) => ({ id, title: `Post ${id}`, contentMarkdown: 'Body', categoryId: null, summary: '', status: 'DRAFT', version: 0 })
beforeEach(() => {
  localStorage.clear()
  vi.mocked(api).mockImplementation(async (path, method) => path === '/categories' ? [] : method === 'PUT' ? post(1) : post(Number(path.split('/').pop())))
})
afterEach(() => { app?.unmount(); app = undefined; document.body.innerHTML = ''; vi.restoreAllMocks(); vi.clearAllMocks() })
async function mount(path = '/admin/posts/1/edit') {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path); await router.isReady()
  const host = document.createElement('div'); document.body.append(host)
  app = createApp(App).use(router); app.mount(host)
  await vi.waitFor(() => expect(host.querySelector('fieldset')?.hasAttribute('disabled')).toBe(false))
  return { host, router }
}
function typeTitle(host: HTMLElement, value: string) {
  const input = host.querySelector<HTMLInputElement>('.test-title')!
  input.value = value; input.dispatchEvent(new Event('input'))
}
it('flushes edits immediately on page hide and warns before closing with unsaved changes', async () => {
  const { host } = await mount()
  typeTitle(host, 'Unsaved')
  window.dispatchEvent(new Event('pagehide'))
  expect(JSON.parse(localStorage.getItem('devflow-editor-5-1')!).title).toBe('Unsaved')
  const unload = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(unload)
  expect(unload.defaultPrevented).toBe(true)
})
it('preserves the old draft key and loads the correct post when navigating between editors', async () => {
  const { host, router } = await mount()
  typeTitle(host, 'First post draft')
  await router.push('/admin/posts/2/edit')
  await vi.waitFor(() => expect(host.querySelector<HTMLInputElement>('.test-title')?.value).toBe('Post 2'))
  expect(JSON.parse(localStorage.getItem('devflow-editor-5-1')!).title).toBe('First post draft')
  expect(localStorage.getItem('devflow-editor-5-2')).toBeNull()
})
it('keeps server-save success visible when draft removal is denied', async () => {
  const { host } = await mount()
  typeTitle(host, 'Saved')
  vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('denied') })
  host.querySelector<HTMLButtonElement>('.test-save')!.click()
  await vi.waitFor(() => expect(host.textContent).toContain('미발행 상태로 저장했습니다. 브라우저 임시저장은 삭제하지 못했습니다.'))
  const unload = new Event('beforeunload', { cancelable: true }); window.dispatchEvent(unload)
  expect(unload.defaultPrevented).toBe(false)
})
it('rejects malformed drafts without changing the editor', async () => {
  localStorage.setItem('devflow-editor-5-1', JSON.stringify({ title: {}, markdown: 'bad' }))
  const { host } = await mount()
  Array.from(host.querySelectorAll('button')).find(button => button.textContent === '로컬 임시저장 복원')!.click()
  await nextTick()
  expect(host.querySelector<HTMLInputElement>('.test-title')?.value).toBe('Post 1')
  expect(host.textContent).toContain('임시저장을 복원하지 못했습니다.')
})

it('shows a recoverable draft before opening its controls and updates after deletion', async () => {
  const key = 'devflow-editor-5-1'
  const draft = JSON.stringify({ title: 'Recovered title', markdown: 'Recovered body' })
  localStorage.setItem(key, draft)
  const { host } = await mount()
  const details = host.querySelector<HTMLDetailsElement>('.local-draft')!
  const status = () => details.querySelector('.draft-status')?.textContent
  const [restore, remove] = details.querySelectorAll('button')
  expect(details.open).toBe(false)
  expect(status()).toBe('복원할 임시저장 있음')
  expect(restore!.disabled).toBe(false)
  remove!.click()
  await nextTick()
  expect(status()).toBe('저장된 임시저장 없음')
  expect(restore!.disabled).toBe(true)
  localStorage.setItem(key, draft)
  window.dispatchEvent(new StorageEvent('storage', { key, newValue: draft }))
  await nextTick()
  expect(status()).toBe('복원할 임시저장 있음')
  restore!.click()
  await nextTick()
  expect(host.querySelector<HTMLInputElement>('.test-title')?.value).toBe('Recovered title')
})

it('submits the loaded version and preserves content and draft after a conflict', async () => {
  const { host } = await mount()
  typeTitle(host, 'My changes')
  vi.mocked(api).mockRejectedValueOnce(new ApiError(409, 'POST_CONFLICT'))
  host.querySelector<HTMLButtonElement>('.test-save')!.click()
  await vi.waitFor(() => expect(host.querySelector('[role="alert"]')?.textContent).toContain('수정 충돌'))
  expect(api).toHaveBeenCalledWith('/admin/posts/1', 'PUT', expect.objectContaining({ title: 'My changes', version: 0 }))
  expect(host.querySelector<HTMLInputElement>('.test-title')?.value).toBe('My changes')
  expect(JSON.parse(localStorage.getItem('devflow-editor-5-1')!).version).toBe(0)
  const calls = vi.mocked(api).mock.calls.length
  host.querySelector<HTMLButtonElement>('.test-save')!.click(); await nextTick()
  expect(api).toHaveBeenCalledTimes(calls)
})

it('restores the draft base version so stale restored content cannot bypass conflict detection', async () => {
  vi.mocked(api).mockImplementation(async path => path === '/categories' ? [] : { ...post(1), version: 3 })
  localStorage.setItem('devflow-editor-5-1', JSON.stringify({ title: 'Old draft', markdown: 'Body', version: 1 }))
  const { host } = await mount()
  Array.from(host.querySelectorAll('button')).find(button => button.textContent === '로컬 임시저장 복원')!.click()
  await nextTick()
  host.querySelector<HTMLButtonElement>('.test-save')!.click()
  await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/admin/posts/1', 'PUT', expect.objectContaining({ version: 1 })))
})

it('retains applied AI question and sources through edits, local restore and server reload', async () => {
  const draft = { id: '11111111-1111-4111-8111-111111111111', question: '적용한 원래 질문', contentMarkdown: '# AI 초안', sources: [{ title: '자료', url: 'https://example.com/docs' }] }
  const { host } = await mount()
  vi.mocked(api).mockResolvedValueOnce(draft)
  const findButton = (label: string) => Array.from(host.querySelectorAll('button')).find(b => b.textContent === label)!
  findButton('AI 글 작성').click(); await nextTick()
  const question = host.querySelector<HTMLTextAreaElement>('#ai-question')!
  question.value = draft.question; question.dispatchEvent(new Event('input'))
  const consent = host.querySelector<HTMLInputElement>('input[type="checkbox"]')!
  consent.checked = true; consent.dispatchEvent(new Event('change')); await nextTick()
  host.querySelector('.ai-draft-panel form')!.dispatchEvent(new Event('submit'))
  await vi.waitFor(() => expect(host.querySelector('.ai-draft-result')).not.toBeNull())
  expect(host.querySelector<HTMLTextAreaElement>('.test-markdown')?.value).toBe('Body')
  findButton('편집기에 적용').click(); await nextTick()
  const body = host.querySelector<HTMLTextAreaElement>('.test-markdown')!
  body.value = '# 작성자가 편집한 본문'; body.dispatchEvent(new Event('input'))
  window.dispatchEvent(new Event('pagehide'))
  const local = localStorage.getItem('devflow-editor-5-1')!
  expect(JSON.parse(local).aiAttribution.question).toBe(draft.question)
  app!.unmount(); app = undefined; document.body.innerHTML = ''
  vi.mocked(api).mockImplementation(async path => path === '/categories' ? [] : post(1))
  const restored = await mount()
  Array.from(restored.host.querySelectorAll('button')).find(b => b.textContent === '로컬 임시저장 복원')!.click(); await nextTick()
  expect(restored.host.querySelector('.applied-ai')?.textContent).toContain(draft.question)
  expect(restored.host.querySelector<HTMLTextAreaElement>('.test-markdown')?.value).toBe('# 작성자가 편집한 본문')
  restored.host.querySelector<HTMLButtonElement>('.test-save')!.click()
  await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/admin/posts/1', 'PUT', expect.objectContaining({ aiDraftId: draft.id, contentMarkdown: '# 작성자가 편집한 본문' })))
  await vi.waitFor(() => expect(restored.host.textContent).toContain('미발행 상태로 저장했습니다.'))
  app!.unmount(); app = undefined; document.body.innerHTML = ''
  vi.mocked(api).mockImplementation(async path => path === '/categories' ? [] : path.startsWith('/admin/ai-drafts/') ? draft : { ...post(1), contentMarkdown: '# 작성자가 편집한 본문', aiDraftId: draft.id })
  const reloaded = await mount()
  expect(reloaded.host.querySelector('.applied-ai')?.textContent).toContain(draft.question)
  expect(reloaded.host.querySelector('.applied-ai a')?.getAttribute('href')).toBe(draft.sources[0]!.url)
})

it('saves privately by default and explicitly schedules public visibility for the chosen date', async () => {
  const scheduled = new Date(Date.now() + 86400000)
  const localDate = new Date(scheduled.getTime() - scheduled.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
  const publishedAt = new Date(localDate).toISOString()
  vi.mocked(api).mockImplementation(async (path, method) => {
    if (path === '/categories') return []
    return { ...post(1), status: 'PUBLISHED', visibility: method === 'POST' ? 'PRIVATE' : 'PUBLIC', publishedAt }
  })
  const { host } = await mount('/posts/create')
  expect(host.querySelector<HTMLInputElement>('#post-publication')?.value).toBe('')
  typeTitle(host, 'New dated post')
  const date = host.querySelector<HTMLInputElement>('#post-publication')!
  date.value = localDate; date.dispatchEvent(new Event('input'))
  window.dispatchEvent(new Event('pagehide'))
  expect(JSON.parse(localStorage.getItem('devflow-editor-5-new')!).publishedInput).toBe(localDate)
  const button = (label: string) => Array.from(host.querySelectorAll('button')).find(b => b.textContent?.trim() === label)!
  button('비공개 저장').click()
  await vi.waitFor(() => expect(host.textContent).toContain('비공개로 저장했습니다.'))
  expect(api).toHaveBeenCalledWith('/admin/posts', 'POST', expect.objectContaining({ visibility: 'PRIVATE', publishedAt }))
  button('예약 공개').click()
  await vi.waitFor(() => expect(host.textContent).toContain('예약 공개로 저장했습니다.'))
  expect(api).toHaveBeenCalledWith('/admin/posts/1', 'PUT', expect.objectContaining({ visibility: 'PUBLIC', publishedAt: undefined }))
})

it('loads existing visibility and omits untouched publication time to preserve database precision', async () => {
  vi.mocked(api).mockImplementation(async path => path === '/categories' ? [] : { ...post(1), status: 'PUBLISHED', visibility: 'PUBLIC', publishedAt: '2016-01-02T03:04:05.123456Z' })
  const { host } = await mount()
  expect(host.querySelector<HTMLInputElement>('#post-publication')?.value).not.toBe('')
  typeTitle(host, 'Date untouched')
  host.querySelector<HTMLButtonElement>('.test-save')!.click()
  await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/admin/posts/1','PUT',expect.objectContaining({ visibility: 'PUBLIC', publishedAt: undefined })))
})

it('keeps an Agent draft in review unless the editor explicitly approves publication', async () => {
  vi.mocked(api).mockImplementation(async path => path === '/categories' ? [] : { ...post(1), status: 'IN_REVIEW', authorId: 7, visibility: 'PUBLIC' })
  const { host } = await mount()
  typeTitle(host, 'Edited draft')
  Array.from(host.querySelectorAll('button')).find(button => button.textContent === '검토 상태로 저장')!.click()
  await vi.waitFor(() => expect(host.textContent).toContain('검토 대기 상태로 저장했습니다.'))
  expect(api).toHaveBeenCalledWith('/admin/posts/1', 'PUT', expect.objectContaining({ visibility: undefined }))
  expect(host.querySelector('.publication-primary')?.textContent).toBe('승인 후 공개')
})

it('requires confirmation before publishing the AI question and sources', async () => {
  const draft = {
    id: '11111111-1111-4111-8111-111111111111',
    question: '공개 확인 질문',
    contentMarkdown: '# AI 초안',
    sources: [{ title: '자료', url: 'https://example.com/docs' }],
  }
  vi.mocked(api).mockImplementation(async (path, method) => {
    if (path === '/categories') return []
    if (path.startsWith('/admin/ai-drafts/')) return draft
    return { ...post(1), status: 'PUBLISHED', visibility: method === 'PUT' ? 'PUBLIC' : 'PRIVATE', aiDraftId: draft.id }
  })
  const { host } = await mount()
  const publish = host.querySelector<HTMLButtonElement>('.publication-primary')!
  publish.click()
  await nextTick()
  expect(host.textContent).toContain('AI 원래 질문과 출처의 공개 여부를 확인하세요.')
  expect(vi.mocked(api).mock.calls.some(([, method]) => method === 'PUT')).toBe(false)
  const consent = host.querySelector<HTMLInputElement>('.publication-consent input')!
  consent.checked = true
  consent.dispatchEvent(new Event('change'))
  publish.click()
  await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/admin/posts/1', 'PUT', expect.objectContaining({ visibility: 'PUBLIC', aiDraftId: draft.id })))
})
