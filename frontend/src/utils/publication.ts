export function toLocalDateTime(raw: string | null | undefined) {
  if (!raw) return ''
  const date = new Date(raw)
  if (!Number.isFinite(date.getTime())) return ''
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}
export function validPublicationInput(value: unknown): value is string {
  return typeof value === 'string' && (!value || (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?$/.test(value) && Number.isFinite(new Date(value).getTime())))
}
