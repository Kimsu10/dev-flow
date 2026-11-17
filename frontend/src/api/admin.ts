import { api } from '../auth'
export interface ManagedPost { id: number; title: string; contentMarkdown: string; summary: string; categoryId: number | null; status: string; authorId: number | null; authorName: string | null; authorRole: string | null; updatedAt: string; version: number }
export const getManagedPost = (id: string) => api<ManagedPost>(`/admin/posts/${id}`)
export const getManagedPosts = (drafts = false) => api<ManagedPost[]>(`/admin/posts${drafts ? '/drafts' : ''}`)
