<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="blog-shell">
    <header class="blog-header">
      <RouterLink class="blog-brand" to="/" aria-label="DevFlow 홈"><span class="brand-mark" aria-hidden="true">/</span>DevFlow<span class="brand-dot">.</span></RouterLink>
      <nav aria-label="주요 메뉴"><RouterLink to="/" exact-active-class="is-current">Blog</RouterLink><RouterLink to="/posts/create" class="write-link">글 작성 <span aria-hidden="true">↗</span></RouterLink></nav>
    </header>
    <main class="blog-page"><slot /></main>
    <footer class="blog-footer"><span>© {{ new Date().getFullYear() }} DevFlow</span><RouterLink to="/status">서비스 상태 <span aria-hidden="true">↗</span></RouterLink></footer>
  </div>
</template>
