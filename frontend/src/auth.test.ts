// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { api, auth, restoreAuth, login } from './auth'
import { authGuard } from './router'
afterEach(() => { auth.user = null; auth.ready = false; vi.unstubAllGlobals() })
const profile = { id: 1, username: 'owner', email: 'owner@example.test', role: 'ADMIN' as const, status: 'ACTIVE' }
it('restores authentication once and guards privileged routes', async () => {
 const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: profile })))
 vi.stubGlobal('fetch', fetcher)
 await Promise.all([restoreAuth(), restoreAuth()])
 expect(fetcher).toHaveBeenCalledTimes(1)
 expect(await authGuard({ path: '/admin', fullPath: '/admin', meta: { admin: true } })).toBe(true)
 auth.user = { ...profile, role: 'USER' }
 expect(await authGuard({ path: '/admin', fullPath: '/admin', meta: { admin: true } })).toBe('/forbidden')
 auth.user = null
 expect(await authGuard({ path: '/posts/create', fullPath: '/posts/create', meta: { admin: true } })).toEqual({ path: '/login', query: { redirect: '/posts/create' } })
})
it('fetches CSRF before login and never stores passwords in state', async () => {
 const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ data: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } })))
  .mockResolvedValueOnce(new Response(JSON.stringify({ data: profile })))
 vi.stubGlobal('fetch', fetcher)
 await login('owner', 'test-password')
 expect(fetcher.mock.calls[1]?.[1]).toMatchObject({ method: 'POST', credentials: 'same-origin', headers: { 'X-CSRF-TOKEN': 'test-csrf' } })
 expect(auth.user).toEqual(profile)
 expect(JSON.stringify(auth)).not.toContain('test-password')
})
it('clears authentication on expired sessions and retries transient restoration errors', async () => {
 vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(new Response('{}', { status: 503 })).mockResolvedValueOnce(new Response('{}', { status: 401 })))
 await expect(restoreAuth()).rejects.toThrow()
 expect(auth.ready).toBe(false)
 await restoreAuth()
 expect(auth.ready).toBe(true); expect(auth.user).toBeNull()
})
it('stops a mutation when fetching CSRF fails', async () => {
 const fetcher = vi.fn().mockResolvedValue(new Response('{}', { status: 503 })); vi.stubGlobal('fetch', fetcher)
 await expect(api('/admin/posts', 'POST', {})).rejects.toThrow()
 expect(fetcher).toHaveBeenCalledTimes(1)
})

it('recognizes post conflicts without displaying arbitrary server error text', async () => {
 vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: { code: 'POST_CONFLICT', message: 'internal detail' } }), { status: 409 })))
 await expect(api('/admin/posts/1')).rejects.toMatchObject({ status: 409, code: 'POST_CONFLICT', message: expect.stringContaining('다른 곳에서 수정된 글') })
})
