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
const output = path.join(root, 'artifacts', 'analytics-ui', String(Date.now()))
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
  await command('Network.setUserAgentOverride', { userAgent: 'Mozilla/5.0 Chrome/142.0.0.0 Safari/537.36' })
  await navigate('/admin/analytics', "location.pathname === '/login' && document.querySelector('form')")
  async function login() {
    await evaluate(`(() => {
      const inputs=[document.querySelector('input[autocomplete=username]'),document.querySelector('input[type=password]')];
      for(const [i,v] of ${JSON.stringify([process.env.AUTH_ADMIN_USERNAME,process.env.AUTH_ADMIN_PASSWORD])}.entries()) {inputs[i].value=v;inputs[i].dispatchEvent(new Event('input',{bubbles:true}));}
      document.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
    })()`);
  }
  await login()
  await waitFor("location.pathname === '/admin/analytics' && document.querySelectorAll('.analytics-cards article').length===4")
  assert.equal(await evaluate("document.querySelectorAll('canvas').length"),2)
  assert.equal(await evaluate("document.querySelector('.analytics-empty')!==null"),true)
  await evaluate(`(async () => {
    const api=async(path,method,body)=>{const c=(await(await fetch('/api/auth/csrf')).json()).data;const r=await fetch('/api'+path,{method,headers:{'Content-Type':'application/json',[c.headerName]:c.token},body:body?JSON.stringify(body):undefined});if(!r.ok)throw new Error('QA setup status '+r.status);return(await r.json()).data;};
    const p=await api('/admin/posts','POST',{title:'Analytics browser QA',contentMarkdown:'# Analytics QA',summary:'Public test post'});
    await api('/admin/posts/'+p.id+'/publish','PATCH');window.qaPostId=p.id;
    await api('/auth/logout','POST');
  })()`)
  const postId=await evaluate('window.qaPostId')
  await command('Page.navigate',{url:'about:blank'}); await pause(300)
  await navigate('/posts/'+postId+'?utm_source=google',"document.querySelector('.post-body')!==null && document.querySelector('.analytics-consent')!==null")
  assert.equal(apiCalls.filter(x=>x.includes('/analytics/page-views')).length,0)
  await evaluate("document.querySelector('.analytics-consent button').click()")
  await waitFor("document.querySelector('.analytics-settings')!==null")
  for(let n=0;n<60 && !apiCalls.some(x=>x.includes('/analytics/page-views'));n++)await pause(100)
  await pause(500)
  const cookies=(await command('Storage.getCookies')).cookies
  assert.equal(cookies.some(c=>c.name==='dv_visitor'&&c.httpOnly),true)
  await reload("document.querySelector('.post-body')!==null")
  await pause(500)
  await evaluate("document.querySelector('.detail-back').click()")
  await waitFor("location.pathname==='/' && document.querySelector('.blog-intro')!==null")
  await pause(500)
  await navigate('/login',"document.querySelector('input[autocomplete=username]')!==null")
  await login();await waitFor("location.pathname==='/admin' && document.querySelector('.auth-form')!==null")
  await navigate('/admin/analytics',"document.querySelectorAll('.analytics-cards article').length===4")
  assert.equal(await evaluate("document.querySelector('.analytics-cards article strong').textContent"),'1')
  assert.equal(await evaluate("document.querySelectorAll('.analytics-cards article strong')[1].textContent"),'2')
  assert.equal(await evaluate("document.querySelectorAll('canvas').length"),3)
  assert.equal(await evaluate("[...document.querySelectorAll('.analytics-panel')].some(p=>p.textContent.includes('Analytics browser QA'))"),true)
  const titles=await evaluate("[...document.querySelectorAll('.analytics-panel h2')].map(x=>x.textContent)")
  assert.ok(titles.includes('최근 작성한 게시글'));assert.ok(titles.includes('인기 게시글 TOP 10'))
  await evaluate("document.querySelector('.analytics-table-wrap td button').click()")
  await waitFor("document.querySelectorAll('canvas').length===4")
  await evaluate("const select=document.querySelector('.analytics-filters select');select.value='30';select.dispatchEvent(new Event('change',{bubbles:true}));document.querySelector('.analytics-filters').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));")
  await waitFor("document.querySelector('.analytics-period')!==null && !document.querySelector('[role=status]')")
  for(const [label,width] of [['desktop',1440],['mobile',390]]){
    await command('Emulation.setDeviceMetricsOverride',{width,height:1100,deviceScaleFactor:1,mobile:false});await pause(100)
    assert.equal(await evaluate('document.documentElement.scrollWidth>innerWidth'),false)
    const screenshot=await command('Page.captureScreenshot',{format:'png'});await writeFile(path.join(output,label+'.png'),Buffer.from(screenshot.data,'base64'))
  }
  assert.deepEqual(scriptErrors,[])
  const report={checks:['admin guard','empty state','consent default off','anonymous page view','cookie flags','reload deduplication','SPA page view','unique visitor counts','real chart rendering','post links and trends','30-day filter','desktop and mobile'],scriptErrors,output};
  await writeFile(path.join(output,'result.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report))
} finally {
  if(socket?.readyState===WebSocket.OPEN){socket.send(JSON.stringify({id:++sequence,method:'Browser.close'}));socket.close()}
  await pause(300);if(child.exitCode===null)child.kill();
  const expectedRoot=path.resolve(root,'artifacts','analytics-ui')+path.sep;
  if(!path.resolve(profile).startsWith(expectedRoot)||path.basename(profile)!=='profile')throw new Error('Unsafe QA profile cleanup');
  await rm(profile,{recursive:true,force:true,maxRetries:10,retryDelay:250});
}
