<script setup lang="ts">
import { usePageView } from '../analytics/tracking'
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import BlogShell from '../components/blog/BlogShell.vue'
import RequestState from '../components/blog/RequestState.vue'
import ThumbnailImage from '../components/blog/ThumbnailImage.vue'
import PostCard from '../components/blog/PostCard.vue'
import AiSources from '../components/blog/AiSources.vue'
import { renderArticle } from '../components/editor/markdown'
import { getPost } from '../api/blog'
import { useBlogResource } from '../composables/useBlogResource'
const route = useRoute()
const { data, loading, error, reload } = useBlogResource(signal => getPost(String(route.params.id), signal))
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
watch(() => route.params.id, async () => {
  await reload()
  await nextTick()
  if (!route.hash && !error.value && String(data.value?.post.id) === String(route.params.id)) window.scrollTo({ top: 0, left: 0 })
})
usePageView(computed(() => !loading.value && !error.value && !!data.value && data.value.visibility !== 'PRIVATE' && String(data.value.post.id) === String(route.params.id)), () => data.value?.post.id)
</script>

<template>
  <BlogShell>
    <RouterLink class="text-back detail-back" to="/">← Blog</RouterLink>
    <RequestState :loading="loading" :error="error" @retry="reload" />
    <article v-if="!loading && !error && data" class="post-detail">
      <header class="post-detail-header">
        <p v-if="data.visibility === 'PRIVATE'" class="private-post-notice" role="status">비공개 글 · 작성자에게만 표시됩니다.</p>
        <aside v-if="data.aiAttribution" class="ai-disclosure" aria-label="AI 작성 안내"><strong>AI 조사 초안을 바탕으로 작성자가 검토·편집한 글입니다.</strong><p>원래 질문: {{ data.aiAttribution.question }}</p></aside>
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
    <section v-if="!loading && !error && data?.relatedPosts?.length" class="related-posts" aria-labelledby="related-title">
      <h2 id="related-title">같은 카테고리의 다른 글</h2>
      <div class="post-grid"><PostCard v-for="post in data.relatedPosts" :key="post.id" :post="post" /></div>
    </section>
  </BlogShell>
</template>
