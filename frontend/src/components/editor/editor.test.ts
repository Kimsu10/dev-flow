// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { EditorSelection, EditorState, type Transaction } from '@codemirror/state'
import { history, undo, redo } from '@codemirror/commands'
import { formatSelection } from './format'
import { renderMarkdown } from './markdown'
import { imageMarkdown, validateImage } from './image'

describe('Markdown rendering', () => {
  it('renders headings, lists, tasks, table, links, inline and highlighted fenced code', () => {
    const html = renderMarkdown('# 제목\n\n**굵게** *기울임* ~~취소~~ `inline`\n\n> 인용\n\n1. 번호\n\n- [x] 완료\n\n[링크](https://example.com)\n\n![이미지](https://example.com/a.png)\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\n---\n\n```typescript\nconst value: string = "ok"\n```')
    for (const tag of ['h1', 'strong', 'em', 's', 'blockquote', 'ol', 'table', 'hr', 'code', 'img']) expect(html).toContain(`<${tag}`)
    expect(html).toContain('type="checkbox"')
    expect(html).toContain('hljs-keyword')
  })
  it('neutralizes scripts, handlers and unsafe Markdown URLs', () => {
    const el = document.createElement('div')
    el.innerHTML = renderMarkdown('<script>alert(1)</script>\n<img src=x onerror=alert(1)>\n\n[x](javascript:alert(1))\n\n![x](data:text/html;base64,PHNjcmlwdD4=)')
    expect(el.querySelector('script, [onerror], [onclick], a[href^="javascript:"], img[src^="data:"]')).toBeNull()
  })
  it.each(['java', 'javascript', 'typescript', 'sql', 'json', 'bash'])('highlights %s', language => {
    const samples: Record<string, string> = { java: 'public class Test {}', javascript: 'const x = 1', typescript: 'const x: string = "x"', sql: 'SELECT * FROM users', json: '{"x": true}', bash: 'echo "$HOME"' }
    expect(renderMarkdown('```' + language + '\n' + samples[language] + '\n```')).toContain('hljs-')
  })
})
describe('format transactions', () => {
  it('keeps selection and supports undo and redo', () => {
    let state = EditorState.create({ doc: 'hello world', selection: { anchor: 6, head: 11 }, extensions: history() })
    state = state.update(formatSelection(state, 'bold')).state
    expect(state.doc.toString()).toBe('hello **world**')
    expect(state.sliceDoc(state.selection.main.from, state.selection.main.to)).toBe('world')
    const target = { get state() { return state }, dispatch: (transaction: Transaction) => { state = transaction.state } }
    undo(target)
    expect(state.doc.toString()).toBe('hello world')
    redo(target)
    expect(state.doc.toString()).toBe('hello **world**')
  })
  it('prefixes selected whole lines, preserving subsequent lines', () => {
    const state = EditorState.create({ doc: 'one\ntwo\nthree', selection: { anchor: 0, head: 8 } })
    expect(state.update(formatSelection(state, 'ordered')).state.doc.toString()).toBe('1. one\n2. two\nthree')
  })
  it('supports multiple selections', () => {
    const state = EditorState.create({ doc: 'a b', selection: EditorSelection.create([EditorSelection.range(0, 1), EditorSelection.range(2, 3)]), extensions: EditorState.allowMultipleSelections.of(true) })
    expect(state.update(formatSelection(state, 'italic')).state.doc.toString()).toBe('*a* *b*')
  })
})
describe('image contract', () => {
  const file = (name: string, type: string, bytes: number[]) => ({ name, type, size: bytes.length, slice: () => ({ arrayBuffer: async () => new Uint8Array(bytes).buffer }) }) as unknown as File
  it('accepts a PNG signature and rejects spoofed extensions, MIME and size', async () => {
    const bytes = [137, 80, 78, 71, 13, 10, 26, 10]
    await expect(validateImage(file('a.png', 'image/png', bytes), 1024)).resolves.toBeUndefined()
    await expect(validateImage(file('a.jpg', 'image/jpeg', bytes), 1024)).rejects.toThrow('형식')
    await expect(validateImage(file('a.png', 'image/png', bytes), 1)).rejects.toThrow('MB')
    await expect(validateImage(file('a.svg', 'image/svg+xml', bytes), 1024)).rejects.toThrow('형식')
  })
  it('escapes alt text, encodes URL delimiters and rejects unsafe protocols', () => {
    expect(imageMarkdown('/image (1).png', 'a] [b')).toContain('![a   b]')
    expect(() => imageMarkdown('javascript:alert(1)', 'x')).toThrow('HTTP')
    expect(imageMarkdown('https://example.com/a>test', 'x')).toContain('%3E')
  })
})
