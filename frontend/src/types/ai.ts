export interface AiSource { title: string; url: string }
export interface AiAttribution { draftId: string; question: string; sources: AiSource[] }
export interface AiDraft { id: string; question: string; contentMarkdown: string; sources: AiSource[] }
export function safeSourceUrl(value: string) {
  try { const url = new URL(value); return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password && value.length <= 2048 }
  catch { return false }
}
export function validAttribution(value: unknown): value is AiAttribution {
  if (!value || typeof value !== 'object') return false
  const p = value as Partial<AiAttribution>
  return typeof p.draftId === 'string' && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(p.draftId)
    && typeof p.question === 'string' && !!p.question.trim() && p.question.length <= 2000
    && Array.isArray(p.sources) && p.sources.length > 0 && p.sources.length <= 30
    && p.sources.every(s => s && typeof s.title === 'string' && s.title.length <= 500 && typeof s.url === 'string' && safeSourceUrl(s.url))
}
