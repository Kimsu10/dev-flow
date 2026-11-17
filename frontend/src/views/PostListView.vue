<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import BlogShell from '../components/blog/BlogShell.vue'
import PostCard from '../components/blog/PostCard.vue'
import RequestState from '../components/blog/RequestState.vue'
import { getCategories, getPosts } from '../api/blog'
import { useBlogResource } from '../composables/useBlogResource'
const route = useRoute()
const categoryId = computed(() => typeof route.query.categoryId === 'string' ? route.query.categoryId : undefined)
const page = computed(() => /^\d+$/.test(String(route.query.page)) ? Number(route.query.page) : 0)
const { data, loading, error, reload } = useBlogResource(async signal => {
  const [categories, posts] = await Promise.all([getCategories(signal), getPosts(categoryId.value, page.value, signal)])
  return { categories, posts }
})
const title = computed(() => data.value?.categories.find(c => String(c.id) === categoryId.value)?.name || '모든 게시글')
watch(() => [route.query.categoryId, route.query.page], reload)
</script>

<template>
  <BlogShell>
    <header class="list-intro"><RouterLink class="text-back" to="/">← Blog</RouterLink><h1>{{ title }}</h1><p class="blog-description">차근차근 쌓아가는 개발의 기록</p></header>
    <nav v-if="data" class="category-nav" aria-label="카테고리 필터"><RouterLink :to="{ name: 'post-list' }" :aria-current="!categoryId ? 'page' : undefined">전체</RouterLink><RouterLink v-for="category in data.categories" :key="category.id" :to="{ name: 'post-list', query: { categoryId: category.id } }" :aria-current="String(category.id) === categoryId ? 'page' : undefined">{{ category.name }}</RouterLink></nav>
    <RequestState :loading="loading" :error="error" :empty="!data?.posts.items.length" @retry="reload" />
    <template v-if="!loading && !error && data">
      <div class="post-grid list-grid"><PostCard v-for="post in data.posts.items" :key="post.id" :post="post" /></div>
      <nav v-if="data.posts.totalPages > 1" class="pagination" aria-label="게시글 페이지">
        <RouterLink v-if="page > 0" :to="{ query: { ...route.query, page: page - 1 } }">← 이전</RouterLink>
        <span>{{ page + 1 }} / {{ data.posts.totalPages }}</span>
        <RouterLink v-if="page + 1 < data.posts.totalPages" :to="{ query: { ...route.query, page: page + 1 } }">다음 →</RouterLink>
      </nav>
    </template>
  </BlogShell>
</template>
