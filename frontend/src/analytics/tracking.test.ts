// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, h, ref, type App } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { analyticsConsent, usePageView, pagePath } from './tracking'
import { auth } from '../auth'
let app: App | undefined
async function mount() {
 vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
 const ready = ref(true)
 const component = { setup() { usePageView(ready); return () => h('p', 'Public page') } }
 const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component }] })
 await router.push('/'); await router.isReady()
 const host = document.createElement('div'); document.body.append(host)
 app = createApp({ render: () => h(component) }).use(router); app.mount(host)
 return { router, ready }
}
afterEach(() => { app?.unmount(); document.body.innerHTML = ''; analyticsConsent.value = null; auth.user = null; auth.ready = false; vi.restoreAllMocks(); vi.unstubAllGlobals() })
it('does not collect before opt in or for administrators', async () => {
 const fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher); auth.ready = true
 await mount(); await new Promise(r => setTimeout(r, 10)); expect(fetcher).not.toHaveBeenCalled()
 auth.user = { id: 1, username: 'owner', role: 'ADMIN', email: 'owner@example.test', status: 'ACTIVE' }
 analyticsConsent.value = 'yes'; await new Promise(r => setTimeout(r, 10)); expect(fetcher).not.toHaveBeenCalled()
})
it('collects successful SPA navigations once and ignores hash changes', async () => {
 auth.ready = true; analyticsConsent.value = 'yes'
 const fetcher = vi.fn().mockImplementation(async (url: string) => new Response(JSON.stringify({ data: url.endsWith('/csrf') ? { headerName: 'X-CSRF-TOKEN', token: 'test' } : { counted: true } })))
 vi.stubGlobal('fetch', fetcher)
 const { router } = await mount()
 await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2))
 await router.push('/#section'); await new Promise(r => setTimeout(r, 10)); expect(fetcher).toHaveBeenCalledTimes(2)
 await router.push('/posts'); await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(4))
 const body = JSON.parse(fetcher.mock.calls[3]?.[1].body)
 expect(body.path).toBe('/posts'); expect(body.referrer).toContain('/'); expect(body.consent).toBe(true)
 expect(fetcher.mock.calls[3]?.[1].headers['X-CSRF-TOKEN']).toBe('test')
})
it('tracking failures do not affect the public page', async () => {
 auth.ready = true; analyticsConsent.value = 'yes'; vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
 await mount(); await new Promise(r => setTimeout(r, 20)); expect(document.body.textContent).toContain('Public page')
})
it('canonicalizes public list paths and removes UTM and arbitrary parameters', () => {
 expect(pagePath('/posts', { categoryId: '2', page: '1', utm_source: 'google', email: 'private' })).toBe('/posts?categoryId=2&page=1')
})
