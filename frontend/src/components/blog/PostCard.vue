<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import ThumbnailImage from './ThumbnailImage.vue'
import type { PostSummary } from '../../types/blog'
const props = defineProps<{ post: PostSummary; eager?: boolean }>()
const date = computed(() => props.post.publishedAt || props.post.createdAt)
const dateLabel = computed(() => {
  const value = new Date(date.value)
  return Number.isNaN(value.getTime()) ? '날짜 정보 없음' : new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'Asia/Seoul',
  }).format(value)
})
</script>

<template>
  <article class="post-card">
    <RouterLink :to="{ name: 'post-detail', params: { id: post.id } }" :aria-label="post.title">
      <div class="post-thumbnail-frame"><ThumbnailImage :src="post.thumbnailUrl" :eager="eager" /></div>
      <div class="post-card-copy">
        <time class="post-date" :datetime="date">{{ dateLabel }}</time>
        <h3 class="post-card-title">{{ post.title }}</h3>
        <p class="post-card-summary">{{ post.summary }}</p>
      </div>
    </RouterLink>
  </article>
</template>
