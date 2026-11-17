import { ref, watch, onMounted, onBeforeUnmount, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { auth } from '../auth'
export const analyticsConsent = ref<'yes' | 'no' | null>(null)
try { const saved = localStorage.getItem('devflow-analytics-consent'); if (saved === 'yes' || saved === 'no') analyticsConsent.value = saved } catch { /* Storage restrictions mean no tracking. */ }
export function consentToAnalytics(value: 'yes' | 'no') {
  try { localStorage.setItem('devflow-analytics-consent', value); analyticsConsent.value = value } catch { analyticsConsent.value = 'no' }
}
export function trackingAllowed() {
  return analyticsConsent.value === 'yes' && auth.ready && auth.user?.role !== 'ADMIN' && auth.user?.role !== 'SYSTEM'
    && navigator.doNotTrack !== '1' && !(navigator as Navigator & { globalPrivacyControl?: boolean }).globalPrivacyControl
}
let previousUrl = '', queue: Promise<unknown> = Promise.resolve()
export function pagePath(path: string, query: Record<string, unknown>) {
  if (path !== '/posts') return path
  const q = new URLSearchParams()
  for (const key of ['categoryId', 'page']) if (/^\d{1,10}$/.test(String(query[key] ?? ''))) q.set(key, String(query[key]))
  return path + (q.size ? '?' + q.toString() : '')
}
export function usePageView(ready: Ref<boolean>, postId?: () => number | undefined) {
  const route = useRoute(); let last = '', disposed = false
  const visible = ref(document.visibilityState !== 'hidden')
  const visibility = () => { visible.value = document.visibilityState !== 'hidden' }
  onMounted(() => document.addEventListener('visibilitychange', visibility))
  onBeforeUnmount(() => { disposed = true; document.removeEventListener('visibilitychange', visibility) })
  watch([ready, () => pagePath(route.path, route.query), analyticsConsent, () => auth.ready, visible], () => {
    if (!ready.value || !trackingAllowed() || document.visibilityState === 'hidden') return
    const path = pagePath(route.path, route.query)
    if (last === path) return
    last = path
    const referrer = previousUrl || document.referrer; previousUrl = location.origin + path
    const clean = (value: unknown) => typeof value === 'string' && /^[A-Za-z0-9_.~-]{0,100}$/.test(value) ? value : undefined
    const event = { eventId: crypto.randomUUID(), path, postId: postId?.(), consent: true, referrer,
      utmSource: clean(route.query.utm_source), utmMedium: clean(route.query.utm_medium), utmCampaign: clean(route.query.utm_campaign) }
    queue = queue.catch(() => {}).then(async () => {
      if (disposed || !trackingAllowed()) return
      try {
        const csrf = await fetch('/api/auth/csrf', { credentials: 'same-origin', signal: AbortSignal.timeout(5000) })
        if (!csrf.ok || !trackingAllowed()) return
        const { data } = await csrf.json()
        await fetch('/api/analytics/page-views', { method: 'POST', credentials: 'same-origin', signal: AbortSignal.timeout(5000),
          headers: { 'Content-Type': 'application/json', [data.headerName]: data.token }, body: JSON.stringify(event) })
      } catch { /* Analytics failure must never affect reading. */ }
    })
  }, { immediate: true, flush: 'post' })
}
