<script setup lang="ts">
import { usePageView } from '../analytics/tracking'
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import BlogShell from '../components/blog/BlogShell.vue'
import RequestState from '../components/blog/RequestState.vue'
import ThumbnailImage from '../components/blog/ThumbnailImage.vue'
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
watch(() => route.params.id, reload)
usePageView(computed(() => !loading.value && !error.value && !!data.value && String(data.value.post.id) === String(route.params.id)), () => data.value?.post.id)
</script>

<template>
  <BlogShell>
    <RouterLink class="text-back detail-back" to="/">← Blog</RouterLink>
    <RequestState :loading="loading" :error="error" @retry="reload" />
    <article v-if="!loading && !error && data" class="post-detail">
      <header class="post-detail-header">
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
    </article>
  </BlogShell>
</template>
