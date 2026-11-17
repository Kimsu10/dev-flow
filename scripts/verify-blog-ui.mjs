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
const output = path.join(root, 'artifacts', 'blog-ui', String(Date.now()))
const profile = path.join(output, 'profile')
await mkdir(profile, { recursive: true })
const base = 'http://127.0.0.1:5173'
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
  throw new Error(`Page condition timed out: ${expression}`)
}
async function navigate(url, ready) {
  await command('Page.navigate', { url: base + url })
  await waitFor(ready)
}
const results = []
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
    if (message.method === 'Network.requestWillBeSent' && message.params.request.url.startsWith(base + '/api/')) apiCalls.push(message.params.request.url)
    if (message.method === 'Runtime.exceptionThrown') scriptErrors.push(message.params.exceptionDetails.text)
  })
  await command('Page.enable'); await command('Runtime.enable'); await command('Network.enable')
  for (const [label, width, height, columns] of [['desktop', 1440, 1100, 3], ['tablet', 820, 1100, 2], ['mobile', 390, 900, 1]]) {
    await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false })
    const start = apiCalls.length
    await navigate('/', "document.querySelectorAll('.category-section').length === 3")
    await evaluate("document.querySelectorAll('.post-thumbnail').forEach(img => img.loading = 'eager')")
    await waitFor("[...document.querySelectorAll('.post-thumbnail')].every(img => img.complete && img.naturalWidth > 0)")
    const layout = await evaluate(`(() => {
      const image = document.querySelector('.post-thumbnail-frame').getBoundingClientRect();
      return { columns: getComputedStyle(document.querySelector('.post-grid')).gridTemplateColumns.split(' ').length,
        cards: document.querySelectorAll('.post-card').length,
        background: getComputedStyle(document.documentElement).backgroundColor,
        overflow: document.documentElement.scrollWidth > innerWidth, ratio: image.width / image.height };
    })()`)
    assert.equal(layout.columns, columns); assert.equal(layout.cards, 9)
    assert.equal(layout.background, 'rgb(255, 255, 255)'); assert.equal(layout.overflow, false)
    assert.ok(Math.abs(layout.ratio - 1.5) < 0.01)
    const calls = apiCalls.slice(start)
    assert.deepEqual(calls.filter(url => !url.endsWith('/api/auth/me')), [base + '/api/blog/home'])
    const screenshot = await command('Page.captureScreenshot', { format: 'png' })
    await writeFile(path.join(output, `${label}.png`), Buffer.from(screenshot.data, 'base64'))
    results.push({ viewport: label, width, ...layout, homeApiRequests: calls.length })
  }
  await evaluate("document.querySelector('.post-card a').click()")
  await waitFor("document.querySelector('.post-body pre code') !== null")
  assert.ok(await evaluate("document.querySelector('.post-detail h1').textContent.includes('Spring Boot')"))
  await navigate('/', "document.querySelector('.category-heading a') !== null")
  await evaluate("document.querySelector('.category-heading a').click()")
  await waitFor("document.querySelector('.list-intro') && document.querySelectorAll('.post-card').length === 3")
  assert.ok(await evaluate("location.search.includes('categoryId=')"))
  await navigate('/posts/create', "location.pathname === '/login' && document.querySelector('input[autocomplete=username]') !== null")
  assert.equal(await evaluate("document.querySelector('.cm-editor') === null"), true)
  await navigate('/', "document.querySelector('.post-card img') !== null")
  await evaluate("document.querySelector('.post-card img').src = '/missing-image-for-qa.png'")
  await waitFor("document.querySelector('.post-card img').src.endsWith('/images/post-placeholder.svg') && document.querySelector('.post-card img').naturalWidth > 0")
  assert.deepEqual(scriptErrors, [])
  const report = { results, navigation: 'detail/category/anonymous editor guard passed', brokenThumbnail: 'fallback passed', scriptErrors, output }
  await writeFile(path.join(output, 'result.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report))
} finally {
  if (socket?.readyState === WebSocket.OPEN) { socket.send(JSON.stringify({ id: ++sequence, method: 'Browser.close' })); socket.close() }
  await pause(300)
  if (child.exitCode === null) child.kill()
}
