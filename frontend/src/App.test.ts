// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, type App as VueApp } from 'vue'
import App from './App.vue'
let app: VueApp | undefined
function mountApp() {
  const host = document.createElement('div')
  document.body.append(host)
  app = createApp(App)
  app.mount(host)
  return host
}
afterEach(() => {
  app?.unmount()
  document.body.innerHTML = ''
  vi.unstubAllGlobals()
})
it.each([
  [200, { status: 'UP', database: 'UP' }, '정상 동작'],
  [503, { status: 'DOWN', database: 'DOWN' }, '데이터베이스 연결'],
  [200, {}, '연결할 수 없습니다'],
])('renders HTTP %s response', async (status, body, message) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })))
  const host = mountApp()
  expect(host.querySelector('button')?.disabled).toBe(true)
  await vi.waitFor(() => expect(host.querySelector('[role="status"]')?.textContent).toContain(message))
})
it('retries after network failure', async () => {
  const fetchMock = vi.fn().mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'UP', database: 'UP' })))
  vi.stubGlobal('fetch', fetchMock)
  const host = mountApp()
  await vi.waitFor(() => expect(host.textContent).toContain('연결할 수 없습니다'))
  host.querySelector('button')?.click()
  await vi.waitFor(() => expect(host.textContent).toContain('정상 동작'))
  expect(fetchMock).toHaveBeenCalledTimes(2)
})
