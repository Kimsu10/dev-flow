<script setup lang="ts">
import { RouterLink } from 'vue-router'
import BlogShell from '../components/blog/BlogShell.vue'
</script>

<template>
  <BlogShell>
    <section class="blog-message" aria-labelledby="not-found-title">
      <p>404</p>
      <h1 id="not-found-title" class="not-found-title">페이지를 찾을 수 없습니다.</h1>
      <p>주소를 확인하거나 게시글 검색에서 원하는 글을 찾아보세요.</p>
      <div class="not-found-links"><RouterLink to="/">블로그 홈</RouterLink><RouterLink to="/posts">게시글 검색</RouterLink></div>
    </section>
  </BlogShell>
</template>
