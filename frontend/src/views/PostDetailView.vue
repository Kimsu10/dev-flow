<script setup lang="ts">
import { usePageView } from '../analytics/tracking'
import { computed, nextTick, ref, watch, onBeforeUnmount } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import BlogShell from '../components/blog/BlogShell.vue'
import RequestState from '../components/blog/RequestState.vue'
import ThumbnailImage from '../components/blog/ThumbnailImage.vue'
import RelatedPosts from '../components/blog/RelatedPosts.vue'
import AiSources from '../components/blog/AiSources.vue'
import { renderArticle } from '../components/editor/markdown'
import { getPost } from '../api/blog'
import { useBlogResource } from '../composables/useBlogResource'
import { api, auth } from '../auth'
import { isFuturePublication } from '../utils/publication'
const route = useRoute()
const router = useRouter()
const { data, loading, error, reload } = useBlogResource(signal => getPost(String(route.params.id), signal, route.query.preview === 'true'))
const deleting = ref(false), actionError = ref('')
const legacyPermission = ref(false)
watch(() => [data.value, auth.user?.id, auth.user?.role] as const, async ([post, actorId, role], _previous, cleanup) => {
  legacyPermission.value = false
  if (!post || role !== 'ADMIN' || (post.canEdit !== undefined && post.canDelete !== undefined)) return
  const controller = new AbortController()
  cleanup(() => controller.abort())
  try {
    // Older running backends omit capability flags. Verify their existing management endpoint.
    await api(`/admin/posts/${post.post.id}`, 'GET', undefined, controller.signal)
    if (!controller.signal.aborted && auth.user?.id === actorId) legacyPermission.value = true
  } catch { /* Missing or denied management permission must not expose actions. */ }
})
let actionVersion = 0
const canEdit = computed(() => auth.user?.role === 'ADMIN' && (data.value?.canEdit ?? legacyPermission.value) && String(data.value?.post.id) === String(route.params.id))
const canDelete = computed(() => auth.user?.role === 'ADMIN' && (data.value?.canDelete ?? legacyPermission.value) && String(data.value?.post.id) === String(route.params.id))
const unpublished = computed(() => !!data.value?.status && data.value.status !== 'PUBLISHED')
const scheduled = computed(() => data.value?.visibility === 'PUBLIC' && isFuturePublication(data.value?.post.publishedAt))
async function removePost() {
  if (!canDelete.value || deleting.value || !data.value) return
  const post = data.value.post
  if (!window.confirm(`“${post.title}” 글을 삭제하시겠습니까?`)) return
  const request = ++actionVersion
  deleting.value = true; actionError.value = ''
  try {
    await api(`/admin/posts/${post.id}`, 'DELETE')
    if (request === actionVersion) await router.replace('/my-posts')
  } catch (cause) {
    if (request === actionVersion) actionError.value = cause instanceof Error ? cause.message : '글을 삭제하지 못했습니다. 다시 시도하세요.'
  } finally { if (request === actionVersion) deleting.value = false }
}
onBeforeUnmount(() => { actionVersion++ })
const article = computed(() => renderArticle(data.value?.contentMarkdown || ''))
const body = ref<HTMLElement>()
watch(() => [article.value.html, route.hash, loading.value], async () => {
  await nextTick()
  if (loading.value || !route.hash || !body.value) return
  let id: string
  try { id = decodeURIComponent(route.hash.slice(1)) } catch { return }
  const target = document.getElementById(id)
  if (target && body.value.contains(target)) target.scrollIntoView({ block: 'start' })
})
const date = computed(() => {
  const raw = data.value?.post.publishedAt || data.value?.post.createdAt
  return raw ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'long', timeZone: 'Asia/Seoul' }).format(new Date(raw)) : ''
})
watch(() => [route.params.id, route.query.preview], async () => {
  actionVersion++;deleting.value = false;actionError.value = ''
  await reload()
  await nextTick()
  if (!route.hash && !error.value && String(data.value?.post.id) === String(route.params.id)) window.scrollTo({ top: 0, left: 0 })
})
usePageView(computed(() => !loading.value && !error.value && !!data.value && !unpublished.value && !scheduled.value && data.value.visibility !== 'PRIVATE' && String(data.value.post.id) === String(route.params.id)), () => data.value?.post.id)
</script>

<template>
  <BlogShell class="post-detail-shell">
    <RouterLink class="text-back detail-back" to="/">← Blog</RouterLink>
    <RequestState :loading="loading" :error="error" @retry="reload" />
    <article v-if="!loading && !error && data" class="post-detail">
      <header class="post-detail-header">
        <div v-if="canEdit || canDelete" class="post-management" role="group" aria-label="글 관리">
          <RouterLink v-if="canEdit" class="outlined-link" :class="{ 'is-disabled': deleting }" :to="`/admin/posts/${data.post.id}/edit`" :aria-disabled="deleting || undefined" :tabindex="deleting ? -1 : undefined" @click="deleting && $event.preventDefault()">글 수정</RouterLink>
          <button v-if="canDelete" type="button" :disabled="deleting" @click="removePost">{{ deleting ? '삭제 중…' : '글 삭제' }}</button>
        </div>
        <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
        <p v-if="data.visibility === 'PRIVATE'" class="private-post-notice" role="status">비공개 글 · 작성자에게만 표시됩니다.</p>
        <p v-if="unpublished" class="private-post-notice" role="status">미발행 글 미리보기 · 독자에게 공개되지 않습니다.</p>
        <p v-else-if="scheduled" class="private-post-notice" role="status">예약 공개 미리보기 · 지정한 게시 일시부터 공개됩니다.</p>
        <aside v-if="data.aiAttribution" class="ai-disclosure" aria-label="AI 작성 안내"><strong>AI가 생성한 초안을 바탕으로 작성된 글입니다.</strong><p>원래 질문: {{ data.aiAttribution.question }}</p></aside>
        <RouterLink v-if="data.post.category" class="detail-category" :to="{ name: 'post-list', query: { categoryId: data.post.category.id } }">{{ data.post.category.name }}</RouterLink>
        <h1>{{ data.post.title }}</h1><p class="post-detail-summary">{{ data.post.summary }}</p>
        <time class="post-date" :datetime="data.post.publishedAt || data.post.createdAt">{{ date }}</time>
        <span class="reading-time"> · 약 {{ article.readingMinutes }}분 읽기</span>
      </header>
      <ThumbnailImage v-if="data.post.thumbnailUrl" :src="data.post.thumbnailUrl" eager />
      <details v-if="article.headings.length > 1" class="article-toc" open>
        <summary>목차</summary>
        <nav aria-label="본문 목차"><ol><li v-for="heading in article.headings" :key="heading.id" :class="{ 'toc-nested': heading.level > 2 }"><a :href="`#${heading.id}`">{{ heading.text }}</a></li></ol></nav>
      </details>
      <div ref="body" class="post-body" v-html="article.html" />
      <AiSources v-if="data.aiAttribution" :sources="data.aiAttribution.sources" />
    </article>
    <RelatedPosts v-if="!loading && !error && data?.post.category" :key="data.post.id" :post-id="data.post.id" :initial-posts="data.relatedPosts || []" :preview="route.query.preview === 'true'" />
  </BlogShell>
</template>
