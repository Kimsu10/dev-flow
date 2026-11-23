// Local browser smoke test. No npm dependencies or changes to the user's browser profile.
import { mkdir, readFile, writeFile, rm } from 'node:fs/promises'
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
const output = path.join(root, 'artifacts', 'auth-ui', String(Date.now()))
const profile = path.join(output, 'profile')
await mkdir(profile, { recursive: true })
const base = process.env.AUTH_TEST_BASE || 'http://127.0.0.1:5173'
const child = spawn(browser, ['--headless', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore', windowsHide: true })
let launchError
child.on('error', error => { launchError = error })
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
let socket
let sequence = 0, loads = 0
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
  const previous = loads;
  await command('Page.navigate', { url: base + url });
  for(let n=0; n<100 && loads === previous; n++) await pause(100);
  if(loads === previous) throw new Error('Page navigation timed out');
  await waitFor(ready);
}
async function reload(ready) {
  const previous = loads; await command('Page.reload');
  for(let n=0; n<100 && loads === previous; n++) await pause(100);
  if(loads === previous) throw new Error('Page reload timed out');
  await waitFor(ready);
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
    if (message.method === 'Page.loadEventFired') loads++;
    if (message.method === 'Network.requestWillBeSent' && message.params.request.url.startsWith(base + '/api/')) apiCalls.push(message.params.request.url)
    if (message.method === 'Runtime.exceptionThrown') scriptErrors.push(message.params.exceptionDetails.text)
  })
  await command('Page.enable'); await command('Runtime.enable'); await command('Network.enable')
  await command('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  await navigate('/admin', "location.pathname === '/login' && document.querySelector('input[autocomplete=username]')")
  assert.equal(await evaluate("document.querySelector('a[href=\"/posts/create\"]') === null"), true)
  async function submitLogin(password) {
    await evaluate(`(() => {
      const fields = [document.querySelector('input[autocomplete=username]'), document.querySelector('input[type=password]')];
      for (const [i, value] of ${JSON.stringify([process.env.AUTH_ADMIN_USERNAME, password])}.entries()) {
        fields[i].value = value; fields[i].dispatchEvent(new Event('input', { bubbles: true }));
      }
      document.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    })()`)
  }
  await submitLogin('wrong-password')
  await waitFor("document.querySelector('[role=alert]') !== null")
  await submitLogin(process.env.AUTH_ADMIN_PASSWORD)
  await waitFor("location.pathname === '/admin' && document.querySelector('.auth-form')")
  const cookies = (await command('Storage.getCookies')).cookies;
  const sessionCookie = cookies.find(cookie => cookie.name === 'JSESSIONID');
  assert.equal(sessionCookie?.httpOnly,true); assert.equal(sessionCookie?.sameSite,'Lax');
  await navigate('/posts/create', "document.querySelector('.cm-editor') !== null && !document.querySelector('fieldset').disabled")
  await evaluate(`(() => {
    const title = document.querySelector('.md-title input'); title.value = 'Browser QA post'; title.dispatchEvent(new Event('input', { bubbles: true }));
    const editor = document.querySelector('.cm-content'); editor.focus();
  })()`)
  await command('Input.insertText', { text: '# Browser review\n\n**Verified Markdown**' })
  await waitFor("document.querySelector('.md-preview strong')?.textContent === 'Verified Markdown'")
  await waitFor("Object.keys(localStorage).some(key => key.startsWith('devflow-editor-'))")
  await evaluate("[...document.querySelectorAll('.md-actions button')].find(button => button.textContent.trim() === '비공개 저장').click()")
  await waitFor("[...document.querySelectorAll('[role=status]')].some(el => el.textContent.includes('비공개로 저장했습니다'))")
  await evaluate("[...document.querySelectorAll('.md-actions button')].find(button => button.textContent.trim() === '공개하기').click()")
  await waitFor("[...document.querySelectorAll('[role=status]')].some(el => el.textContent.includes('독자가 볼 수 있도록 공개했습니다'))")
  await navigate('/my-posts', "document.querySelector('.my-posts article') !== null")
  await evaluate("[...document.querySelectorAll('.my-posts h2 a')].find(link => link.textContent.trim() === 'Browser QA post').click()")
  await waitFor("document.querySelector('.md-title input')?.value === 'Browser QA post'")
  await navigate('/my-posts', "document.querySelector('.my-posts article') !== null")
  await reload("document.querySelector('.my-posts article') !== null && document.querySelector('a[href=\"/posts/create\"]') !== null")
  assert.equal(await evaluate("document.querySelector('a[href=\"/posts/create\"]') !== null"), true)
  // Create a service account through the real API; secrets stay in browser memory.
  await evaluate(`(async () => {
    window.qaApi = async (path, method='GET', body) => {
      const headers = {};
      if(method !== 'GET') { const c=(await (await fetch('/api/auth/csrf')).json()).data; headers[c.headerName]=c.token; if(body) headers['Content-Type']='application/json'; }
      const r=await fetch('/api'+path,{method,headers,body:body?JSON.stringify(body):undefined});
      if(!r.ok) throw new Error('QA API status '+r.status); return (await r.json()).data;
    };
    const a=await window.qaApi('/admin/accounts','POST',{username:'browser-agent',email:'browser-agent@example.test',role:'SYSTEM'});
    const t=await window.qaApi('/admin/accounts/'+a.id+'/token','POST');
    const r=await fetch('/api/agent/posts/drafts',{method:'POST',headers:{Authorization:'Bearer '+t.token,'Content-Type':'application/json'},body:JSON.stringify({title:'Agent review QA',contentMarkdown:'# AI draft',status:'IN_REVIEW'})});
    if(!r.ok) throw new Error('Agent QA status '+r.status); window.qaDraft=(await r.json()).data.id;
  })()`)
  await navigate('/admin', "[...document.querySelectorAll('.admin-posts article')].some(el => el.textContent.includes('Agent review QA') && el.textContent.includes('검토 대기'))")
  for(const [label,width] of [['desktop',1440],['mobile',390]]) {
    await command('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false})
    assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'),false)
    const screenshot=await command('Page.captureScreenshot',{format:'png'});
    await writeFile(path.join(output,label+'.png'),Buffer.from(screenshot.data,'base64'));
  }
  await evaluate("[...document.querySelectorAll('nav button')].find(el => el.textContent.includes('로그아웃')).click()")
  await waitFor("location.pathname === '/' && document.querySelector('a[href=\"/login\"]')")
  await navigate('/posts/create', "location.pathname === '/login'")
  assert.deepEqual(scriptErrors,[])
  const report={checks:['anonymous guard','login failure','login success','Markdown preview','local draft','server save','publish','edit','session restoration','Agent IN_REVIEW','responsive admin','logout'],scriptErrors,output};
  await writeFile(path.join(output,'result.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify(report))
} finally {
  if(socket?.readyState === WebSocket.OPEN){socket.send(JSON.stringify({id:++sequence,method:'Browser.close'}));socket.close()}
  await pause(300);if(child.exitCode===null)child.kill();
  const expectedRoot=path.resolve(root,'artifacts','auth-ui')+path.sep;
  if(!path.resolve(profile).startsWith(expectedRoot) || path.basename(profile)!=='profile') throw new Error('Unsafe QA profile cleanup path');
  await rm(profile,{recursive:true,force:true,maxRetries:10,retryDelay:250});
}
