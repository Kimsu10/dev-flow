import type { BlogHome, Category, PostDetail, PostPage } from '../types/blog'

export class BlogApiError extends Error {
  status: number
  constructor(status: number) {
    super('게시글을 불러오지 못했습니다.')
    this.status = status
  }
}

async function get<T>(path: string, signal: AbortSignal): Promise<T> {
  const response = await fetch(`/api${path}`, { signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]) })
  if (!response.ok) throw new BlogApiError(response.status)
  const body: unknown = await response.json()
  if (typeof body !== 'object' || body === null || !('data' in body)) throw new Error('Invalid API response')
  return body.data as T
}

export const getBlogHome = (signal: AbortSignal) => get<BlogHome>('/blog/home', signal)
export const getCategories = (signal: AbortSignal) => get<Category[]>('/categories', signal)
export const getPost = (id: string, signal: AbortSignal, preview = false) => get<PostDetail>(`/posts/${encodeURIComponent(id)}${preview ? '?preview=true' : ''}`, signal)
export const getRelatedPosts = (id: number, page: number, signal: AbortSignal, preview = false, size = 3) => get<PostPage>(`/posts/${id}/related?page=${page}&size=${size}${preview ? '&preview=true' : ''}`, signal)
export function getPosts(categoryId: string | undefined, page: number, signal: AbortSignal, search = '') {
  const query = new URLSearchParams({ page: String(page), size: '9' })
  if (categoryId) query.set('categoryId', categoryId)
  if (search.trim()) query.set('q', search.trim())
  return get<PostPage>(`/posts?${query}`, signal)
}
