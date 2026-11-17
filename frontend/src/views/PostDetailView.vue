<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import BlogShell from '../components/blog/BlogShell.vue'
import RequestState from '../components/blog/RequestState.vue'
import ThumbnailImage from '../components/blog/ThumbnailImage.vue'
import { renderMarkdown } from '../components/editor/markdown'
import { getPost } from '../api/blog'
import { useBlogResource } from '../composables/useBlogResource'
const route = useRoute()
const { data, loading, error, reload } = useBlogResource(signal => getPost(String(route.params.id), signal))
const html = computed(() => renderMarkdown(data.value?.contentMarkdown || ''))
const date = computed(() => {
  const raw = data.value?.post.publishedAt || data.value?.post.createdAt
  return raw ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'long', timeZone: 'Asia/Seoul' }).format(new Date(raw)) : ''
})
watch(() => route.params.id, reload)
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
      </header>
      <ThumbnailImage v-if="data.post.thumbnailUrl" :src="data.post.thumbnailUrl" eager />
      <div class="post-body" v-html="html" />
    </article>
  </BlogShell>
</template>
