import { api } from '../auth'
export interface Overview { start: string; end: string; visitors: number; pageViews: number; visits: number; postCount: number; viewsPerVisitor: number; todayVisitors: number; yesterdayVisitors: number; totalPageViews: number; totalUniqueVisitors: number; visitorChangePercent: number | null; pageViewChangePercent: number | null; todayChangePercent: number | null; retentionDays: number }
export interface Traffic { date: string; visitors: number; pageViews: number }
export interface Referrer { source: string; visits: number; percentage: number }
export interface CategoryStats { id: number | null; name: string; postCount: number; totalViews: number; periodViews: number; percentage: number }
export interface PostStats { id: number; title: string; createdAt: string; updatedAt: string; totalViews: number; periodViews: number }
export const analyticsGet = <T>(endpoint: string, query: string) => api<T>(`/admin/analytics/${endpoint}?${query}`)
