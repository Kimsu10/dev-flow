// @vitest-environment jsdom
import { expect, it } from 'vitest'
import { renderArticle } from './markdown'

it('creates matching, unique Unicode heading links from Markdown tokens', () => {
  const article = renderArticle('# 소개\n\n## **설치** 및 `실행`\n\n## 소개\n\n## 소개-2\n\n## 소개\n\n```md\n# not a heading\n```')
  const host = document.createElement('div')
  host.innerHTML = article.html
  expect(article.headings.map(h => h.text)).toEqual(['소개', '설치 및 실행', '소개', '소개-2', '소개'])
  expect(new Set(article.headings.map(h => h.id)).size).toBe(5)
  for (const heading of article.headings) expect(host.querySelector(`[id="${heading.id}"]`)?.textContent).toBe(heading.text)
  expect(article.headings[1]?.level).toBe(2)
})

it('preserves safe rendering, lazy images, and ignores raw HTML headings', () => {
  const article = renderArticle('# <script>bad()</script>\n\n<img src=x onerror=alert(1)>\n\n![alt](/image.png)\n\n[x](javascript:alert(1))')
  const host = document.createElement('div')
  host.innerHTML = article.html
  expect(host.querySelector('script, [onerror], a[href^="javascript:"]')).toBeNull()
  expect(host.querySelector('img')?.getAttribute('loading')).toBe('lazy')
  expect(host.querySelector('img')?.getAttribute('decoding')).toBe('async')
})

it('estimates mixed language and code reading time with a one-minute minimum', () => {
  expect(renderArticle('').readingMinutes).toBe(1)
  expect(renderArticle('가'.repeat(1000)).readingMinutes).toBe(2)
  expect(renderArticle('word '.repeat(400)).readingMinutes).toBe(2)
  expect(renderArticle('```java\n' + 'int x = 1;\n'.repeat(40) + '```').readingMinutes).toBe(2)
})
