import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: () => import('./views/BlogHomeView.vue') },
  { path: '/status', name: 'status', component: () => import('./views/HomeView.vue') },
  { path: '/posts/create', name: 'post-create', component: () => import('./views/PostCreateView.vue') },
  { path: '/posts', name: 'post-list', component: () => import('./views/PostListView.vue') },
  { path: '/posts/:id(\\d+)', name: 'post-detail', component: () => import('./views/PostDetailView.vue') },
]

export const router = createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes })
