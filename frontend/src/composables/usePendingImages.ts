import { computed, ref, watch, type Ref } from 'vue'
const prefix = 'https://devflow.invalid/pending-images/'
const pattern = /https:\/\/devflow\.invalid\/pending-images\/([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})/g
export const pendingImageIds = (markdown: string) => [...new Set([...markdown.matchAll(pattern)].map(match => match[1]!))]
let database: Promise<IDBDatabase> | undefined
function db() {
  return database ??= new Promise((resolve, reject) => {
    const request = indexedDB.open('devflow-pending-images', 1)
    request.onupgradeneeded = () => request.result.createObjectStore('images')
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => { database = undefined; reject(new Error('이미지 임시저장소를 열 수 없습니다.')) }
  })
}
async function stored<T>(key: string, mode: IDBTransactionMode, value?: Blob): Promise<T> {
  const database = await db()
  return new Promise((resolve, reject) => {
    const tx = database.transaction('images', mode), store = tx.objectStore('images')
    const request = mode === 'readonly' ? store.get(key) : value ? store.put(value, key) : store.delete(key)
    tx.oncomplete = () => resolve(request.result as T)
    tx.onerror = tx.onabort = () => reject(new Error('이미지 임시저장에 실패했습니다.'))
  })
}
function dataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.onerror = () => reject(new Error('이미지 미리보기를 만들 수 없습니다.')); reader.readAsDataURL(blob) })
}
export function usePendingImages(markdown: Ref<string>, owner: number | string) {
  const previews = ref<Record<string, string>>({}), warning = ref('')
  const key = (id: string) => `${owner}:${id}`
  const previewSource = computed(() => markdown.value.replace(pattern, (url, id) => previews.value[id] || url))
  async function stage(file: File) {
    if (pendingImageIds(markdown.value).length >= 10) throw new Error('한 번에 이미지 10개까지 첨부할 수 있습니다.')
    const id = crypto.randomUUID()
    await stored(key(id), 'readwrite', file)
    previews.value = { ...previews.value, [id]: await dataUrl(file) }
    return { url: prefix + id, alt: file.name }
  }
  watch(markdown, async source => {
    for (const id of pendingImageIds(source)) {
      if (previews.value[id]) continue
      try {
        const blob = await stored<Blob | undefined>(key(id), 'readonly')
        if (!blob) { warning.value = '임시 이미지 파일이 없습니다. 해당 이미지를 다시 첨부해 주세요.'; continue }
        previews.value = { ...previews.value, [id]: await dataUrl(blob) }
      } catch { warning.value = '임시 이미지를 복원하지 못했습니다. 파일을 다시 첨부해 주세요.' }
    }
  }, { immediate: true })
  async function form(post: unknown) {
    const ids = pendingImageIds(markdown.value), body = new FormData()
    body.append('post', new Blob([JSON.stringify(post)], { type: 'application/json' }))
    body.append('imageIds', new Blob([JSON.stringify(ids)], { type: 'application/json' }))
    let total = 0
    for (const id of ids) {
      const blob = await stored<Blob | undefined>(key(id), 'readonly')
      if (!blob) throw new Error('임시 이미지 파일이 없습니다. 다시 첨부한 뒤 저장하세요.')
      total += blob.size
      if (total > 25 * 1024 * 1024) throw new Error('첨부 이미지 전체 크기는 25MB 이하여야 합니다.')
      body.append('images', blob, id)
    }
    return body
  }
  async function clear(ids: string[]) {
    await Promise.all(ids.map(id => stored(key(id), 'readwrite')))
  }
  return { stage, previewSource, warning, form, clear }
}
