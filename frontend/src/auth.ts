import { reactive } from 'vue'
import { router } from './router'
export interface Profile { id: number; username: string; email: string; role: 'ADMIN' | 'USER' | 'SYSTEM'; status: string }
export const auth = reactive<{ user: Profile | null; ready: boolean }>({ user: null, ready: false })
let restoring: Promise<void> | undefined
export class ApiError extends Error { status: number; constructor(status: number) { super(status === 401 ? '로그인이 필요합니다.' : status === 403 ? '접근 권한이 없거나 보안 토큰이 만료되었습니다.' : status === 409 ? '이미 사용 중인 계정이거나 데이터가 충돌합니다.' : '요청을 처리하지 못했습니다. 입력 내용을 확인하세요.'); this.status = status } }
export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (method !== 'GET') {
    const csrf = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
    if (!csrf.ok) throw new ApiError(csrf.status)
    const { data } = await csrf.json()
    headers[data.headerName] = data.token
    if (body !== undefined) headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(`/api${path}`, { method, headers, credentials: 'same-origin', cache: 'no-store', body: body === undefined ? undefined : JSON.stringify(body) })
  if (!response.ok) {
    if (response.status === 401 && path !== '/auth/login') {
      auth.user = null
      if (router.currentRoute.value.meta.admin) void router.replace('/login')
    }
    if (response.status === 403 && router.currentRoute.value.meta.admin) void router.replace('/forbidden')
    throw new ApiError(response.status)
  }
  return (await response.json()).data as T
}
export function restoreAuth(): Promise<void> {
  if (auth.ready) return Promise.resolve()
  return restoring ??= (async () => {
    try { auth.user = await api<Profile>('/auth/me'); auth.ready = true }
    catch (error) { if (error instanceof ApiError && error.status === 401) { auth.user = null; auth.ready = true } else throw error }
    finally { restoring = undefined }
  })()
}
export async function login(username: string, password: string) { auth.user = await api<Profile>('/auth/login', 'POST', { username, password }); auth.ready = true }
export async function logout() { await api('/auth/logout', 'POST'); auth.user = null; auth.ready = true; await router.replace('/') }
