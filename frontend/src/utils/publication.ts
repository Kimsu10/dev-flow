import type { ManagedPost } from '../api/admin'

export function toLocalDateTime(raw: string | null | undefined) {
  if (!raw) return ''
  const date = new Date(raw)
  if (!Number.isFinite(date.getTime())) return ''
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}
export function validPublicationInput(value: unknown): value is string {
  return typeof value === 'string' && (!value || (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?$/.test(value) && Number.isFinite(new Date(value).getTime())))
}
export const publicationLabels = {
  draft: '미발행',
  review: '검토 대기',
  private: '비공개',
  scheduled: '예약 공개',
  public: '공개 중',
} as const

export function isFuturePublication(publishedAt: string | null | undefined) {
  return !!publishedAt && Date.parse(publishedAt) > Date.now()
}

export function publicationState(post: Pick<ManagedPost, 'status' | 'visibility' | 'publishedAt'>): keyof typeof publicationLabels {
  if (post.status === 'IN_REVIEW') return 'review'
  if (post.status !== 'PUBLISHED') return 'draft'
  if (post.visibility === 'PRIVATE') return 'private'
  return isFuturePublication(post.publishedAt) ? 'scheduled' : 'public'
}
