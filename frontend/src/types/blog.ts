export interface Category { id: number; name: string; slug: string; sortOrder: number }
export interface PostSummary {
  id: number
  title: string
  summary: string
  thumbnailUrl: string | null
  createdAt: string
  publishedAt: string | null
  category: Category | null
}
export interface CategorySectionData { category: Category; posts: PostSummary[] }
export interface BlogHome { categories: CategorySectionData[] }
export interface PostPage { items: PostSummary[]; page: number; size: number; totalElements: number; totalPages: number }
export interface PostDetail { post: PostSummary; contentMarkdown: string }
