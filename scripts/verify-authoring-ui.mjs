// Local browser smoke test. No npm dependencies or changes to the user's browser profile.
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const browser = process.env.BLOG_TEST_BROWSER || [
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
].find(existsSync)
if (!browser) throw new Error('Set BLOG_TEST_BROWSER to a Chromium executable.')
const output = path.join(root, 'artifacts', 'authoring-ui', String(Date.now()))
const profile = path.join(output, 'profile')
await mkdir(profile, { recursive: true })
const base = process.env.BLOG_TEST_BASE || 'http://127.0.0.1:15173'
const child = spawn(browser, ['--headless', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore', windowsHide: true })
let launchError
child.on('error', error => { launchError = error })
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
let socket
let sequence = 0
const pending = new Map()
const apiCalls = []
const scriptErrors = []
async function command(method, params = {}) {
  const id = ++sequence
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)) }, 20000)
    pending.set(id, { resolve: result => { clearTimeout(timer); resolve(result) }, reject: error => { clearTimeout(timer); reject(error) } })
    socket.send(JSON.stringify({ id, method, params }))
  })
}
async function evaluate(expression) {
  const result = await command('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
  return result.result.value
}
async function waitFor(expression) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await evaluate(expression)) return
    await pause(100)
  }
  console.error(JSON.stringify({ location: await evaluate('location.pathname'), visible: await evaluate('document.body.innerText.slice(0,1000)'), scriptErrors }))
  throw new Error(`Page condition timed out: ${expression}`)
}
async function navigate(url, ready) {
  await command('Page.navigate', { url: base + url })
  await waitFor(ready)
}
// UI-only contract fixtures. No credentials, live AI calls, or database writes.
const results = []
const draft = { id: '11111111-1111-4111-8111-111111111111', question: 'PostgreSQL의 트랜잭션 격리를 설명해 주세요.', contentMarkdown: '# 조사한 초안\n\n공식 자료를 확인하며 검토할 내용입니다.\n\n## 주의점\n\n작성자가 검토합니다.', sources: [{ title: 'PostgreSQL 공식 문서 (테스트 출처)', url: 'https://www.postgresql.org/docs/current/transaction-iso.html' }] }
const category = { id: 91, name: 'Vue', slug: 'vue', sortOrder: 0 }
let savedPost, categories = [category], generationCalls = 0
async function fixture(params) {
  const pathname = new URL(params.request.url).pathname.replace('/api', '')
  const method = params.request.method
  const body = params.request.postData ? JSON.parse(params.request.postData) : {}
  let data
  if (pathname === '/auth/me') data = { id: 5, username: 'ui-fixture-admin', role: 'ADMIN', status: 'ACTIVE' }
  else if (pathname === '/auth/csrf') data = { headerName: 'X-CSRF-TOKEN', token: 'non-secret-ui-fixture' }
  else if (pathname === '/categories') data = categories
  else if (pathname === '/admin/categories') {
    data = { id: 92, name: body.name, slug: 'added', sortOrder: 0 }
    categories = [...categories.filter(c => c.id !== 92), data]
  } else if (pathname === '/admin/ai-drafts' && method === 'POST') { generationCalls++; data = { ...draft, question: body.question } }
  else if (pathname.startsWith('/admin/ai-drafts/')) data = draft
  else if (pathname === '/admin/posts' && method === 'POST') {
    savedPost = { ...body, id: 123, version: 0, status: 'PUBLISHED', createdAt: '2026-11-20T00:00:00Z', publishedAt: body.publishedAt || '2026-11-20T00:00:00Z' }; data = savedPost
  } else if (pathname === '/admin/posts/123' && method === 'PUT') { savedPost = { ...savedPost, ...body, version: savedPost.version + 1 }; data = savedPost }
  else if (pathname === '/admin/posts/123') data = savedPost
  else if (pathname === '/admin/posts/mine') data = savedPost ? [savedPost] : []
  else if (pathname === '/admin/posts/123/publish') { savedPost = { ...savedPost, status: 'PUBLISHED', visibility: 'PUBLIC', version: savedPost.version + 1 }; data = savedPost }
  else if (pathname === '/posts/123') data = {
    post: { id: 123, title: savedPost.title, summary: '', thumbnailUrl: null, createdAt: savedPost.createdAt, publishedAt: savedPost.publishedAt, category },
    contentMarkdown: savedPost.contentMarkdown, visibility: savedPost.visibility, relatedPosts: [], aiAttribution: { draftId: draft.id, question: draft.question, sources: draft.sources },
  }
  else throw new Error('Unimplemented UI fixture: ' + method + ' ' + pathname)
  await command('Fetch.fulfillRequest', { requestId: params.requestId, responseCode: 200, responseHeaders: [{ name: 'Content-Type', value: 'application/json' }], body: Buffer.from(JSON.stringify({ data })).toString('base64') })
}
async function clickLabel(label, scope = 'document') {
  await evaluate(`(() => { const b = [...${scope}.querySelectorAll('button')].find(b => b.textContent.trim() === ${JSON.stringify(label)}); if (!b) throw new Error('Button missing'); b.click(); })()`)
}
try {
  let port
  for (let attempt = 0; attempt < 100; attempt++) {
    if (launchError) throw launchError
    try { port = Number((await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]); break } catch {}
    await pause(100)
  }
  if (!port) throw new Error('Headless browser did not start.')
  const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
  socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }) })
  socket.addEventListener('message', event => {
    const message = JSON.parse(String(event.data))
    if (message.id && pending.has(message.id)) {
      const callback = pending.get(message.id); pending.delete(message.id)
      message.error ? callback.reject(new Error(message.error.message)) : callback.resolve(message.result)
    }
    if (message.method === 'Fetch.requestPaused') void fixture(message.params).catch(error => scriptErrors.push(error.message))
    if (message.method === 'Runtime.exceptionThrown') scriptErrors.push(message.params.exceptionDetails.text)
    if (message.method === 'Page.javascriptDialogOpening') void command('Page.handleJavaScriptDialog', { accept: true })
  })
  await command('Page.enable'); await command('Runtime.enable')
  await command('Fetch.enable', { patterns: [{ urlPattern: base + '/api/*' }] })
  for (const [label, width, height] of [['desktop', 1440, 1100], ['mobile', 390, 900]]) {
    await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false })
    await navigate('/posts/create', "document.querySelector('.cm-content') !== null && !document.querySelector('fieldset').disabled")
    await evaluate('localStorage.clear()')
    assert.equal(await evaluate("document.querySelector('#ai-question').offsetParent === null"), true)
    const calls = generationCalls
    await evaluate("(() => { const input = document.querySelector('.md-title input'); input.value = '브라우저 검증 글'; input.dispatchEvent(new Event('input', {bubbles:true})); document.querySelector('.cm-content').focus(); })()")
    await command('Input.insertText', { text: '직접 작성한 원래 본문' })
    assert.equal(await evaluate("document.querySelector('#post-visibility').value"),'PRIVATE')
    await evaluate("(() => { const date = document.querySelector('#post-publication'); date.value = '2020-03-04T12:30'; date.dispatchEvent(new Event('input', {bubbles:true})); })()")
    await clickLabel('+ 카테고리 추가')
    await evaluate("(() => { const input = document.querySelector('#new-category-name'); input.value = '추가 카테고리'; input.dispatchEvent(new Event('input', {bubbles:true})); })()")
    await clickLabel('저장 후 선택')
    await waitFor("document.querySelector('#post-category').value === '92'")
    await clickLabel('AI 글 작성')
    await evaluate(`(() => { const input = document.querySelector('#ai-question'); input.value = ${JSON.stringify(draft.question)}; input.dispatchEvent(new Event('input', {bubbles:true})); })()`)
    assert.equal(generationCalls, calls)
    assert.equal(await evaluate("[...document.querySelectorAll('button')].find(b => b.textContent === '자료 조사하고 초안 만들기').disabled"), true)
    await evaluate("document.querySelector('.ai-consent input').click()")
    await clickLabel('자료 조사하고 초안 만들기')
    await waitFor("document.querySelector('.ai-draft-result') !== null")
    assert.equal(await evaluate("document.querySelector('.cm-content').textContent"), '직접 작성한 원래 본문')
    await evaluate("document.querySelector('.ai-draft-result').scrollIntoView()")
    await command('Page.captureScreenshot', { format: 'png' }).then(r => writeFile(path.join(output,label+'-preview.png'),Buffer.from(r.data,'base64')))
    await clickLabel('편집기에 적용')
    await waitFor("document.querySelector('.cm-content').textContent.includes('조사한 초안')")
    await evaluate("document.querySelector('.cm-content').focus()")
    await command('Input.insertText', { text: '작성자가 추가한 수정\n' })
    await evaluate("window.dispatchEvent(new Event('pagehide'))")
    await navigate('/posts/create', "document.querySelector('.cm-content') !== null && !document.querySelector('fieldset').disabled")
    await clickLabel('로컬 임시저장 복원')
    await waitFor("document.querySelector('.applied-ai') !== null")
    assert.ok(await evaluate("document.querySelector('.cm-content').textContent.includes('작성자가 추가한 수정')"))
    await evaluate("document.querySelector('.applied-ai').scrollIntoView()")
    assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'),false)
    await command('Page.captureScreenshot',{format:'png'}).then(r=>writeFile(path.join(output,label+'-applied.png'),Buffer.from(r.data,'base64')))
    await clickLabel('저장', "document.querySelector('.md-actions')")
    await waitFor("[...document.querySelectorAll('[role=status]')].some(el => el.textContent.includes('서버에 저장했습니다'))")
    assert.equal(savedPost.aiDraftId,draft.id)
    assert.equal(savedPost.visibility,'PRIVATE')
    const originalDate = savedPost.publishedAt
    assert.ok(originalDate)
    await navigate('/my-posts', "document.querySelector('.my-posts article') !== null")
    assert.ok(await evaluate("document.querySelector('.my-posts').textContent.includes('비공개 · 작성자만')"))
    await command('Page.captureScreenshot',{format:'png'}).then(r=>writeFile(path.join(output,label+'-my-posts.png'),Buffer.from(r.data,'base64')))
    await clickLabel('공개로 전환')
    await waitFor("document.querySelector('.my-posts strong').textContent === '공개'")
    assert.equal(savedPost.visibility,'PUBLIC');assert.equal(savedPost.publishedAt,originalDate)
    await navigate('/admin/posts/123/edit', "document.querySelector('.applied-ai') !== null")
    assert.ok(await evaluate("document.querySelector('.applied-ai').textContent.includes('PostgreSQL의 트랜잭션')"))
    assert.equal(await evaluate("document.querySelector('#post-visibility').value"),'PUBLIC')
    assert.ok(await evaluate("document.querySelector('#post-publication').value.startsWith('2020-03-04')"))
    await navigate('/posts/123', "document.querySelector('.ai-disclosure') !== null")
    assert.equal(await evaluate("document.querySelector('.post-detail-header time').getAttribute('datetime')"),originalDate)
    assert.equal(await evaluate("document.querySelector('.ai-sources a').href"),draft.sources[0].url)
    assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'),false)
    await command('Page.captureScreenshot',{format:'png'}).then(r=>writeFile(path.join(output,label+'-published.png'),Buffer.from(r.data,'base64')))
    results.push({ label, categorySelected:true, previewBeforeApply:true, localRestore:true, privateSave:true, myPostsConversion:true, publicationDatePreserved:true, serverReload:true, publishedAttribution:true, overflow:false })
  }
  assert.deepEqual(scriptErrors,[])
  await writeFile(path.join(output,'results.json'),JSON.stringify({ transport:'Mocked authoring API fixtures; no live AI or database writes',results,scriptErrors },null,2))
  console.log(JSON.stringify({ results, output, transport:'mocked' }))
} finally { socket?.close(); child.kill() }
