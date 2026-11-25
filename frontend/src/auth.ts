import { reactive } from 'vue'
import { router } from './router'
export interface Profile { id: number; username: string; email: string; role: 'ADMIN' | 'USER' | 'SYSTEM'; status: string }
export const auth = reactive<{ user: Profile | null; ready: boolean }>({ user: null, ready: false })
let restoring: Promise<void> | undefined
let sessionRevision = 0
const messages: Record<string, string> = {
  LOGIN_RATE_LIMITED: '로그인 요청이 많습니다. 잠시 후 다시 시도해 주세요.',
  POST_CONFLICT: '다른 곳에서 수정된 글입니다. 입력 내용은 유지됩니다. 최신 글을 확인한 뒤 다시 편집하세요.',
  AI_NOT_CONFIGURED: '백엔드에 OpenAI API 키가 설정되지 않았습니다. 서버 환경에 OPENAI_API_KEY를 설정하고 백엔드를 재시작하세요.',
  SESSION_NOT_ESTABLISHED: '로그인 세션 쿠키가 확인되지 않습니다. 접속 주소와 서버의 쿠키 설정을 확인하세요.',
  AI_UNAVAILABLE: 'AI 조사 요청을 완료하지 못했습니다. 잠시 후 다시 시도하세요.',
  AI_UNVERIFIED: '자료 조사와 출처가 확인된 초안을 받지 못했습니다. 다시 시도하세요.',
  AI_BUSY: '진행 중인 AI 조사가 있습니다. 잠시 후 다시 시도하세요.',
}
export class ApiError extends Error { status: number; code?: string; constructor(status: number, code?: string) { super((code && messages[code]) || (status === 401 ? '로그인이 필요합니다.' : status === 403 ? '접근 권한이 없거나 보안 토큰이 만료되었습니다.' : status === 409 ? '이미 사용 중인 계정이거나 데이터가 충돌합니다.' : '요청을 처리하지 못했습니다. 입력 내용을 확인하세요.')); this.status = status; this.code = code } }
export async function api<T>(path: string, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<T> {
  const revision = sessionRevision
  const headers: Record<string, string> = {}
  if (method !== 'GET') {
    const csrf = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store', signal })
    if (!csrf.ok) throw new ApiError(csrf.status)
    const { data } = await csrf.json()
    headers[data.headerName] = data.token
    if (body !== undefined) headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(`/api${path}`, { method, headers, credentials: 'same-origin', cache: 'no-store', signal, body: body === undefined ? undefined : JSON.stringify(body) })
  if (!response.ok) {
    if (response.status === 401 && path !== '/auth/login' && revision === sessionRevision) {
      auth.user = null
      if (router.currentRoute.value.meta.admin) void router.replace('/login')
    }
    if (response.status === 403 && revision === sessionRevision && router.currentRoute.value.meta.admin) void router.replace('/forbidden')
    let code: string | undefined
    try { const body = await response.json(); if (Object.hasOwn(messages, body?.error?.code)) code = body.error.code } catch { /* Keep the status-based error for non-JSON responses. */ }
    throw new ApiError(response.status, code)
  }
  return (await response.json()).data as T
}
export function restoreAuth(recheck = false): Promise<void> {
  if (auth.ready && !recheck) return Promise.resolve()
  return restoring ??= (async () => {
    const revision = sessionRevision
    try {
      const profile = await api<Profile>('/auth/me')
      if (revision === sessionRevision) { auth.user = profile; auth.ready = true }
    } catch (error) {
      if (revision !== sessionRevision) return
      if (!(error instanceof ApiError) || error.status !== 401) throw error
      auth.user = null; auth.ready = true
    }
    finally { restoring = undefined }
  })()
}
export async function login(username: string, password: string) {
  await api<Profile>('/auth/login', 'POST', { username, password })
  const revision = ++sessionRevision
  try {
    const profile = await api<Profile>('/auth/me')
    if (revision === sessionRevision) { auth.user = profile; auth.ready = true }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) throw new ApiError(401, 'SESSION_NOT_ESTABLISHED')
    throw error
  }
}
export async function logout() {
  await api('/auth/logout', 'POST')
  sessionRevision++
  auth.user = null; auth.ready = true
  await router.replace('/')
}
