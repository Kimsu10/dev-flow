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
  const listing = await (await fetch(base + '/api/posts?size=1')).json()
  assert.ok(listing.data.items.length, 'At least one published post is required; this check never seeds data.')
  const post = listing.data.items[0]
  const detail = await (await fetch(base + '/api/posts/' + post.id)).json()
  const expectedRelated = detail.data.relatedPosts || []
  const term = post.title.slice(0, 10)
  for (const [label, width, height] of [['desktop', 1440, 1100], ['mobile', 390, 900]]) {
    await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false })
    await navigate('/posts', "document.querySelector('.post-card') !== null")
    await evaluate(`(() => { const input = document.querySelector('#post-search'); input.value = ${JSON.stringify(term)}; input.dispatchEvent(new Event('input', {bubbles:true})); document.querySelector('form').requestSubmit(); })()`)
    await waitFor("new URLSearchParams(location.search).has('q') && !document.querySelector('.blog-loading')")
    assert.equal(await evaluate("new URLSearchParams(location.search).get('q')"), term)
    assert.ok(await evaluate("document.querySelectorAll('.post-card').length > 0"))
    assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false)
    await command('Page.captureScreenshot', { format: 'png' }).then(result => writeFile(path.join(output, label + '-search.png'), Buffer.from(result.data, 'base64')))
    await navigate('/posts/' + post.id, "document.querySelector('.post-body') !== null")
    assert.ok(await evaluate("document.querySelector('.reading-time').textContent.length > 0"))
    const links = await evaluate("[...document.querySelectorAll('.article-toc a')].map(a => a.getAttribute('href'))")
    if (links.length) {
      await evaluate("document.querySelector('.article-toc a:last-child').click()")
      await waitFor('location.hash.length > 0')
      const fragment = await evaluate('location.hash')
      await navigate('/posts/' + post.id + fragment, "document.querySelector('.post-body') !== null")
      await waitFor("(() => { const el = document.getElementById(decodeURIComponent(location.hash.slice(1))); return el && el.getBoundingClientRect().top >= 0 && el.getBoundingClientRect().top < innerHeight; })()")
    }
    assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false)
    await command('Page.captureScreenshot', { format: 'png' }).then(result => writeFile(path.join(output, label + '-reader.png'), Buffer.from(result.data, 'base64')))
    assert.equal(await evaluate("document.querySelectorAll('.related-posts .post-card').length"), expectedRelated.length)
    if (expectedRelated.length) {
      await evaluate("document.querySelector('.related-posts').scrollIntoView()")
      await command('Page.captureScreenshot', { format: 'png' }).then(result => writeFile(path.join(output, label + '-related.png'), Buffer.from(result.data, 'base64')))
      await evaluate("document.querySelector('.related-posts a').click()")
      await waitFor(`location.pathname === '/posts/${expectedRelated[0].id}' && document.querySelector('.post-body') !== null && scrollY === 0`)
      assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false)
    }
    await navigate('/missing-page', "document.querySelector('.not-found-title') !== null")
    assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false)
    assert.ok(await evaluate("document.querySelector('.skip-to-content').getAttribute('href') === '#main-content'"))
    results.push({ label, search: true, article: true, headingLinks: links.length, relatedPosts: expectedRelated.length, notFound: true, overflow: false })
  }
  assert.deepEqual(scriptErrors, [])
  await writeFile(path.join(output, 'results.json'), JSON.stringify({ results, apiRequests: apiCalls.length, scriptErrors }, null, 2))
  console.log(JSON.stringify({ results, output }))
} finally {
  socket?.close()
  child.kill()
}
