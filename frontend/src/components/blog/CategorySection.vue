<script setup lang="ts">
import { RouterLink } from 'vue-router'
import PostCard from './PostCard.vue'
import type { CategorySectionData } from '../../types/blog'
defineProps<{ section: CategorySectionData; eager?: boolean }>()
</script>

<template>
  <section v-if="section.posts.length" :id="`category-${section.category.id}`" class="category-section" :aria-labelledby="`category-title-${section.category.id}`">
    <div class="category-heading">
      <h2 :id="`category-title-${section.category.id}`">{{ section.category.name }}</h2>
      <RouterLink :to="{ name: 'post-list', query: { categoryId: section.category.id } }" :aria-label="`${section.category.name} 전체 보기`">전체 보기 <span aria-hidden="true">↗</span></RouterLink>
    </div>
    <div class="post-grid"><PostCard v-for="post in section.posts.slice(0, 3)" :key="post.id" :post="post" :eager="eager" /></div>
  </section>
</template>
