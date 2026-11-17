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
