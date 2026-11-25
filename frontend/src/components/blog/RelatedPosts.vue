<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getRelatedPosts } from '../../api/blog'
import type { PostSummary } from '../../types/blog'
import PostCard from './PostCard.vue'
const props = defineProps<{ postId: number; initialPosts: PostSummary[]; preview: boolean }>()
const posts = ref(props.initialPosts.filter(post => post.id !== props.postId))
const page = ref(0), totalPages = ref(1), loading = ref(false), error = ref('')
const ready = ref(false)
let controller: AbortController | undefined
const mobile = window.matchMedia?.('(max-width: 600px)')
const pageSize = ref(mobile?.matches ? 1 : 3)
const previousDisabled = computed(() => loading.value || !ready.value || page.value === 0)
const nextDisabled = computed(() => loading.value || !ready.value || page.value + 1 >= totalPages.value)
async function load(target: number) {
  if (loading.value || target < 0) return
  const request = new AbortController(); controller = request
  loading.value = true; error.value = ''
  try {
    const result = await getRelatedPosts(props.postId, target, request.signal, props.preview, pageSize.value)
    if (request.signal.aborted) return
    if (result.totalPages && target >= result.totalPages) {
      loading.value = false
      return await load(result.totalPages - 1)
    }
    posts.value = result.items.filter(post => post.id !== props.postId)
    page.value = target; totalPages.value = result.totalPages; ready.value = true
  } catch {
    if (!request.signal.aborted) error.value = '관련 글을 불러오지 못했습니다. 다시 시도해 주세요.'
  } finally { if (!request.signal.aborted) loading.value = false }
}
function keyboard(event: KeyboardEvent) {
  if (event.target instanceof Element && event.target.closest('a')) return
  if (event.key === 'ArrowLeft' && !previousDisabled.value) { event.preventDefault(); void load(page.value - 1) }
  if (event.key === 'ArrowRight' && !nextDisabled.value) { event.preventDefault(); void load(page.value + 1) }
}
function resize(event: MediaQueryListEvent) {
  controller?.abort(); loading.value = false; ready.value = false
  pageSize.value = event.matches ? 1 : 3
  void load(0)
}
onMounted(() => { mobile?.addEventListener('change', resize); void load(0) })
onBeforeUnmount(() => { controller?.abort(); mobile?.removeEventListener('change', resize) })
</script>

<template>
  <section class="related-posts" aria-labelledby="related-title" @keydown="keyboard">
    <h2 id="related-title">같은 카테고리의 다른 글</h2>
    <div class="related-carousel">
      <button class="related-arrow" type="button" aria-label="이전 관련 글" aria-controls="related-post-list" :disabled="previousDisabled" @click="load(page - 1)"><span aria-hidden="true">‹</span></button>
      <div id="related-post-list" class="related-content" :aria-busy="loading">
        <div v-if="posts.length" class="post-grid"><PostCard v-for="post in posts" :key="post.id" :post="post" compact /></div>
        <p v-else-if="!loading && !error" class="related-empty">같은 카테고리의 다른 글이 없습니다.</p>
        <p v-if="loading" class="related-status" role="status">관련 글 불러오는 중…</p>
      </div>
      <button class="related-arrow" type="button" aria-label="다음 관련 글" aria-controls="related-post-list" :disabled="nextDisabled" @click="load(page + 1)"><span aria-hidden="true">›</span></button>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }} <button type="button" :disabled="loading" @click="load(page)">다시 시도</button></p>
    <p v-if="ready && totalPages > 0" class="related-pagination" role="status" aria-live="polite">{{ page + 1 }} / {{ totalPages }} 페이지</p>
  </section>
</template>

<style scoped>
.related-carousel { display: grid; grid-template-columns: 44px minmax(0, 1fr) 44px; align-items: stretch; gap: 12px; }
.related-arrow { display: flex; align-items: center; justify-content: center; align-self: stretch; width: 44px; min-height: 44px; padding: 0; font-size: 28px; line-height: 1; color: #40594b; background: #f8faf9; border: 1px solid #d4d4d4; border-radius: 6px; cursor: pointer; transition: background-color .15s, border-color .15s, color .15s, box-shadow .15s; }
.related-arrow:not(:disabled):hover { color: #fff; background: #1e4434; border-color: #1e4434; box-shadow: 0 2px 8px #1e443426; }
.related-arrow:focus-visible { outline: 2px solid #1e4434; outline-offset: 3px; }
.related-arrow:disabled { opacity: .35; cursor: default; }
.related-content { position: relative; min-width: 0; }
.related-pagination, .related-empty, .related-status { color: #737373; font-size: 13px; text-align: center; }
.related-status { position: absolute; inset: 0 0 auto; margin: 0; padding: 8px; background: #fffffff2; border-radius: 6px; pointer-events: none; }
.related-content :deep(.post-card-title) { min-height: 3em; }
@media (max-width: 600px) { .related-carousel { gap: 6px; } .post-grid { grid-template-columns: minmax(0, 1fr); } }
@media (prefers-reduced-motion: reduce) { .related-arrow { transition: none; } }
</style>
