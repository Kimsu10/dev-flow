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
  return { default: defineComponent({ props: ['modelValue', 'title'], emits: ['update:title', 'save', 'publish'], setup(props, { emit }) {
    return () => h('div', [h('input', { class: 'test-title', value: props.title, onInput: (e: Event) => emit('update:title', (e.target as HTMLInputElement).value) }), h('button', { class: 'test-save', onClick: () => emit('save') }, 'Save')])
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
  await vi.waitFor(() => expect(host.textContent).toContain('서버에 저장했습니다. 브라우저 임시저장은 삭제하지 못했습니다.'))
  const unload = new Event('beforeunload', { cancelable: true }); window.dispatchEvent(unload)
  expect(unload.defaultPrevented).toBe(false)
})
it('rejects malformed drafts without changing the editor', async () => {
  const { host } = await mount()
  localStorage.setItem('devflow-editor-5-1', JSON.stringify({ title: {}, markdown: 'bad' }))
  Array.from(host.querySelectorAll('button')).find(button => button.textContent === '로컬 임시저장 복원')!.click()
  await nextTick()
  expect(host.querySelector<HTMLInputElement>('.test-title')?.value).toBe('Post 1')
  expect(host.textContent).toContain('임시저장을 복원하지 못했습니다.')
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
  const { host } = await mount()
  localStorage.setItem('devflow-editor-5-1', JSON.stringify({ title: 'Old draft', markdown: 'Body', version: 1 }))
  Array.from(host.querySelectorAll('button')).find(button => button.textContent === '로컬 임시저장 복원')!.click()
  await nextTick()
  host.querySelector<HTMLButtonElement>('.test-save')!.click()
  await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/admin/posts/1', 'PUT', expect.objectContaining({ version: 1 })))
})
