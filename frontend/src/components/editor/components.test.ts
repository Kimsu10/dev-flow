// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, ref, type App } from 'vue'
import { EditorView } from '@codemirror/view'
import { undo } from '@codemirror/commands'
import MarkdownEditorLayout from './MarkdownEditorLayout.vue'
import type { UploadImage } from './types'

let app: App | undefined
afterEach(() => { app?.unmount(); document.body.innerHTML = ''; vi.restoreAllMocks() })
function mount(uploadImage?: UploadImage) {
  const value = ref('hello'), title = ref(''), save = vi.fn(), publish = vi.fn()
  const host = document.createElement('div'); document.body.append(host)
  app = createApp({ render: () => h(MarkdownEditorLayout, {
    modelValue: value.value, title: title.value, uploadImage,
    'onUpdate:modelValue': (next: string) => { value.value = next },
    'onUpdate:title': (next: string) => { title.value = next }, onSave: save, onPublish: publish,
  }) }); app.mount(host)
  const button = (label: string) => Array.from(host.querySelectorAll('button')).find(el => (el.getAttribute('aria-label') || el.textContent?.trim()) === label)!
  const editor = EditorView.findFromDOM(host.querySelector('.cm-editor') as HTMLElement)!
  return { host, value, title, save, publish, button, editor }
}
it('connects toolbar, v-model, sanitized preview, modes, title and action events', async () => {
  const { host, value, title, button, editor, save, publish } = mount()
  editor.dispatch({ selection: { anchor: 0, head: 5 } })
  button('Bold').click(); await nextTick()
  expect(value.value).toBe('**hello**')
  expect(host.querySelector('.md-preview strong')?.textContent).toBe('hello')
  undo(editor); await nextTick(); expect(value.value).toBe('hello')
  value.value = '# parent update'; await nextTick()
  expect(editor.state.doc.toString()).toBe('# parent update')
  const input = host.querySelector<HTMLInputElement>('.md-title input')!; input.value = '제목'; input.dispatchEvent(new Event('input'))
  expect(title.value).toBe('제목')
  button('미리보기').click(); await nextTick()
  expect(host.querySelector('.md-panes')?.getAttribute('data-mode')).toBe('preview')
  expect(button('Bold').disabled).toBe(true)
  button('편집').click(); await nextTick(); expect(button('Bold').disabled).toBe(false)
  button('전체 화면').click(); await nextTick(); expect(host.querySelector('.md-fullscreen')).not.toBeNull()
  host.querySelector('.md-workspace')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); await nextTick()
  expect(host.querySelector('.md-fullscreen')).toBeNull()
  button('임시 저장').click(); button('발행').click(); expect(save).toHaveBeenCalledOnce(); expect(publish).toHaveBeenCalledOnce()
})
it('maps an upload insertion point through concurrent edits and makes insertion undoable', async () => {
  let resolve!: (result: { url: string }) => void
  const uploader = vi.fn<UploadImage>((_file, progress) => { progress(50); return new Promise(done => { resolve = done }) })
  const { host, editor, value, button } = mount(uploader)
  editor.dispatch({ selection: { anchor: 5 } })
  const file = { name: 'a.png', type: 'image/png', size: 8, slice: () => ({ arrayBuffer: async () => new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]).buffer }) }
  const paste = new Event('paste', { bubbles: true, cancelable: true })
  Object.defineProperty(paste, 'clipboardData', { value: { files: [file] } })
  editor.contentDOM.dispatchEvent(paste)
  await vi.waitFor(() => expect(uploader).toHaveBeenCalledOnce())
  expect(host.textContent).toContain('50%')
  expect(button('임시 저장').disabled).toBe(true)
  editor.dispatch({ changes: { from: 0, insert: 'new ' } })
  resolve({ url: 'https://example.com/a.png' })
  await vi.waitFor(() => expect(value.value).toContain('![a.png]'))
  expect(value.value).toBe('new hello\n![a.png](<https://example.com/a.png>)\n')
  undo(editor); await nextTick(); expect(value.value).toBe('new hello')
  expect(button('임시 저장').disabled).toBe(false)
})
it('reports upload failure without changing the document', async () => {
  const { host, editor, value } = mount(async () => { throw new Error('업로드 실패') })
  const file = { name: 'a.png', type: 'image/png', size: 8, slice: () => ({ arrayBuffer: async () => new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]).buffer }) }
  const drop = new MouseEvent('drop', { bubbles: true, cancelable: true })
  Object.defineProperty(drop, 'dataTransfer', { value: { files: [file] } })
  editor.contentDOM.dispatchEvent(drop)
  await vi.waitFor(() => expect(host.textContent).toContain('업로드 실패'))
  expect(value.value).toBe('hello')
})
