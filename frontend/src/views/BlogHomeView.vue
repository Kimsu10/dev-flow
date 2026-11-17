<script setup lang="ts">
import { computed } from 'vue'
import BlogShell from '../components/blog/BlogShell.vue'
import CategorySection from '../components/blog/CategorySection.vue'
import RequestState from '../components/blog/RequestState.vue'
import { getBlogHome } from '../api/blog'
import { useBlogResource } from '../composables/useBlogResource'

const { data, loading, error, reload } = useBlogResource(getBlogHome)
const sections = computed(() => (data.value?.categories || []).filter(section => section.posts.length > 0))
</script>

<template>
  <BlogShell>
    <header class="blog-intro"><p class="blog-eyebrow">A DEVELOPER'S NOTEBOOK</p><h1>Blog<span class="intro-dot">.</span></h1><p class="blog-description">개발 경험과 기술을 기록하는 공간</p></header>
    <nav v-if="!loading && !error && sections.length" class="category-nav" aria-label="카테고리 바로가기"><span class="category-nav-label">EXPLORE</span><a v-for="section in sections" :key="section.category.id" :href="`#category-${section.category.id}`">{{ section.category.name }}</a></nav>
    <div :aria-busy="loading" class="blog-content">
      <RequestState :loading="loading" :error="error" :empty="!sections.length" @retry="reload" />
      <template v-if="!loading && !error"><CategorySection v-for="(section, index) in sections" :key="section.category.id" :section="section" :eager="index === 0" /></template>
    </div>
  </BlogShell>
</template>
