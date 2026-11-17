// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, type App } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import AnalyticsView from './AnalyticsView.vue'
import { analyticsGet } from '../api/analytics'
import { auth } from '../auth'
vi.mock('../api/analytics', () => ({ analyticsGet: vi.fn() }))
vi.mock('../components/AnalyticsChart.vue', () => ({ default: { template: '<div data-chart>chart</div>' } }))
let app: App | undefined
async function mount() {
 const host = document.createElement('div'); document.body.append(host)
 const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: AnalyticsView }] }); await router.push('/'); await router.isReady()
 app = createApp(AnalyticsView).use(router); app.mount(host); return host
}
afterEach(() => { app?.unmount(); document.body.innerHTML = ''; vi.clearAllMocks(); auth.user = null })
it('renders empty metrics, charts and separate post sections', async () => {
 vi.mocked(analyticsGet).mockImplementation(async endpoint => endpoint === 'overview' ? { start: '2026-01-01', end: '2026-01-07', visitors: 0, pageViews: 0, visits: 0, postCount: 0, viewsPerVisitor: 0, todayVisitors: 0, yesterdayVisitors: 0, totalPageViews: 0, totalUniqueVisitors: 0, visitorChangePercent: null, pageViewChangePercent: null, todayChangePercent: null, retentionDays: 365 } as never : [] as never)
 const host = await mount(); await vi.waitFor(() => expect(host.textContent).toContain('수집된 방문 데이터가 없습니다'))
 expect(host.textContent).toContain('최근 작성한 게시글'); expect(host.textContent).toContain('인기 게시글 TOP 10')
 expect(analyticsGet).toHaveBeenCalledWith('overview', 'days=7')
 expect(host.querySelectorAll('[data-chart]')).toHaveLength(2)
})
it('handles API errors with a retry action', async () => {
 vi.mocked(analyticsGet).mockRejectedValue(new Error('unavailable'))
 const host = await mount(); await vi.waitFor(() => expect(host.querySelector('[role=alert]')?.textContent).toContain('통계를 불러오지 못했습니다'))
 expect(host.querySelector('[role=alert] button')?.textContent).toBe('다시 시도')
})
