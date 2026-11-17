import { EditorSelection, type EditorState } from '@codemirror/state'
import type { Format } from './types'

export function formatSelection(state: EditorState, format: Format, heading = 2) {
  return state.changeByRange(range => {
    const selected = state.sliceDoc(range.from, range.to)
    let before = '', after = '', text = selected || '텍스트'
    switch (format) {
      case 'bold': before = after = '**'; break
      case 'italic': before = after = '*'; break
      case 'strike': before = after = '~~'; break
      case 'code': before = after = '`'; break
      case 'codeblock': before = '\n```text\n'; after = '\n```\n'; break
      case 'link': before = '['; after = '](https://example.com)'; break
      case 'image': before = '!['; after = '](https://example.com/image.png)'; text = selected || '이미지 설명'; break
      case 'table': text = '| 제목 | 설명 |\n| --- | --- |\n| 항목 | 내용 |'; before = '\n'; after = '\n'; break
      default: {
        const from = state.doc.lineAt(range.from).from
        const to = state.doc.lineAt(range.to > range.from && state.doc.lineAt(range.to).from === range.to ? range.to - 1 : range.to).to
        const prefix = format === 'heading' ? '#'.repeat(heading) + ' ' : format === 'quote' ? '> ' : format === 'ordered' ? '1. ' : '- '
        const insert = state.sliceDoc(from, to).split('\n').map((line, i) => (format === 'ordered' ? `${i + 1}. ` : prefix) + line).join('\n')
        return { changes: { from, to, insert }, range: EditorSelection.range(from, from + insert.length) }
      }
    }
    return { changes: { from: range.from, to: range.to, insert: before + text + after }, range: EditorSelection.range(range.from + before.length, range.from + before.length + text.length) }
  })
}
