<script setup lang="ts">
defineProps<{ loading: boolean; error: string; empty?: boolean }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="blog-loading" role="status">
    <p class="visually-hidden">게시글을 불러오는 중입니다.</p>
    <div class="post-grid" aria-hidden="true"><div v-for="n in 3" :key="n" class="skeleton-card"><div class="skeleton-image" /><div class="skeleton-line" /><div class="skeleton-line short" /></div></div>
  </div>
  <div v-else-if="error" class="blog-message" role="alert"><p>{{ error }}</p><button type="button" @click="$emit('retry')">다시 시도</button></div>
  <div v-else-if="empty" class="blog-message"><span class="empty-mark" aria-hidden="true">—</span><h2>아직 발행된 글이 없습니다.</h2><p>새로운 개발 이야기를 준비하고 있습니다.</p></div>
</template>
