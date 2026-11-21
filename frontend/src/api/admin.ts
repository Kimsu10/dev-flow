import { api } from '../auth'
export interface ManagedPost { id: number; title: string; contentMarkdown: string; summary: string; categoryId: number | null; status: string; authorId: number | null; authorName: string | null; authorRole: string | null; updatedAt: string; createdAt: string; publishedAt: string | null; version: number; aiDraftId: string | null; visibility: 'PUBLIC' | 'PRIVATE' }
export const getManagedPost = (id: string) => api<ManagedPost>(`/admin/posts/${id}`)
export const getManagedPosts = (drafts = false) => api<ManagedPost[]>(`/admin/posts${drafts ? '/drafts' : ''}`)
export const getMyPosts = () => api<ManagedPost[]>('/admin/posts/mine')
