import { auth, restoreAuth } from './auth'
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  { path: '/admin/analytics', meta: { admin: true }, component: () => import('./views/AnalyticsView.vue') },
  { path: '/login', component: () => import('./views/LoginView.vue') },
  { path: '/forbidden', component: () => import('./views/ForbiddenView.vue') },
  { path: '/my-posts', meta: { admin: true }, component: () => import('./views/MyPostsView.vue') },
  { path: '/admin', meta: { admin: true }, component: () => import('./views/AdminView.vue') },
  { path: '/admin/posts/:id(\\d+)/edit', meta: { admin: true, editor: true }, component: () => import('./views/PostCreateView.vue') },
  { path: '/', name: 'home', component: () => import('./views/BlogHomeView.vue') },
  { path: '/status', name: 'status', component: () => import('./views/HomeView.vue') },
  { path: '/posts/create', name: 'post-create', meta: { admin: true, editor: true }, component: () => import('./views/PostCreateView.vue') },
  { path: '/posts', name: 'post-list', component: () => import('./views/PostListView.vue') },
  { path: '/posts/:id(\\d+)', name: 'post-detail', component: () => import('./views/PostDetailView.vue') },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('./views/NotFoundView.vue') },
]

export const router = createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes })

export async function authGuard(to: { path: string; fullPath: string; meta: Record<string, unknown> }) {
  try { await restoreAuth(!!to.meta.admin && !auth.user) } catch { if (to.meta.admin) return '/login'; return true }
  if (to.meta.admin && !auth.user) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.admin && auth.user?.role !== 'ADMIN') return '/forbidden'
  return true
}
router.beforeEach(authGuard)
