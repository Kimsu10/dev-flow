import MarkdownIt from 'markdown-it'
import taskLists from 'markdown-it-task-lists'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import sql from 'highlight.js/lib/languages/sql'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'

Object.entries({ java, javascript, typescript, sql, json, bash }).forEach(([name, language]) => hljs.registerLanguage(name, language))
const md = new MarkdownIt({ html: false, linkify: true, highlight(code, lang) {
  return lang && hljs.getLanguage(lang) ? hljs.highlight(code, { language: lang, ignoreIllegals: true }).value : ''
} }).use(taskLists)
export function renderMarkdown(source: string) {
  return DOMPurify.sanitize(md.render(source), { USE_PROFILES: { html: true } })
}

export interface ArticleHeading { id: string; text: string; level: number }

/** Parse once so the table of contents and rendered headings always agree. */
export function renderArticle(source: string) {
  const env = {}
  const tokens = md.parse(source, env)
  const headings: ArticleHeading[] = []
  const used = new Set<string>()
  let text = ''
  let codeLines = 0
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i]!
    if (token.type === 'inline') {
      text += ' ' + (token.children || []).filter(child => ['text', 'code_inline'].includes(child.type)).map(child => child.content).join(' ')
      for (const child of token.children || []) {
        if (child.type === 'image') { child.attrSet('loading', 'lazy'); child.attrSet('decoding', 'async') }
      }
    }
    if (token.type === 'fence' || token.type === 'code_block') codeLines += token.content.trim().split('\n').length
    if (token.type !== 'heading_open') continue
    const inline = tokens[i + 1]
    const label = (inline?.children || []).map(child => child.type === 'image' ? child.content : ['text', 'code_inline'].includes(child.type) ? child.content : '').join('').trim()
    const slug = label.toLowerCase().normalize('NFC').replace(/[^\p{L}\p{N}\s-]/gu, '').trim().replace(/\s+/g, '-').slice(0, 80) || 'heading'
    const base = `section-${slug}`
    let id = base
    for (let suffix = 2; used.has(id); suffix++) id = `${base}-${suffix}`
    used.add(id)
    token.attrSet('id', id)
    headings.push({ id, text: label || '제목 없는 문단', level: Number(token.tag.slice(1)) })
  }
  const cjk = (text.match(/[\p{Script=Hangul}\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/gu) || []).length
  const words = text.replace(/[\p{Script=Hangul}\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/gu, ' ').trim().split(/\s+/).filter(Boolean).length
  return {
    html: DOMPurify.sanitize(md.renderer.render(tokens, md.options, env), { USE_PROFILES: { html: true } }),
    headings,
    readingMinutes: Math.max(1, Math.ceil(cjk / 500 + words / 200 + codeLines / 20)),
  }
}
