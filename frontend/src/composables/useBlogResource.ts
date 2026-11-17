import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { BlogApiError } from '../api/blog'

export function useBlogResource<T>(loader: (signal: AbortSignal) => Promise<T>) {
  const data = shallowRef<T | null>(null)
  const loading = ref(true)
  const error = ref('')
  let controller: AbortController | undefined
  async function reload() {
    controller?.abort()
    const request = new AbortController()
    controller = request
    loading.value = true
    error.value = ''
    try {
      const result = await loader(request.signal)
      if (!request.signal.aborted) data.value = result
    } catch (cause) {
      if (!request.signal.aborted) {
        error.value = cause instanceof BlogApiError && cause.status === 404
          ? '게시글을 찾을 수 없습니다.' : '게시글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
      }
    } finally {
      if (!request.signal.aborted) loading.value = false
    }
  }
  onMounted(reload)
  onBeforeUnmount(() => controller?.abort())
  return { data, loading, error, reload }
}
