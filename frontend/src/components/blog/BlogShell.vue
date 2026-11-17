<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { auth, logout } from '../../auth'
import { ref } from 'vue'
const message = ref('')
async function signOut() { try { await logout() } catch { message.value = '로그아웃에 실패했습니다. 다시 시도해 주세요.' } }
</script>

<template>
  <div class="blog-shell">
    <header class="blog-header">
      <RouterLink class="blog-brand" to="/" aria-label="DevFlow 홈"><span class="brand-mark" aria-hidden="true">/</span>DevFlow<span class="brand-dot">.</span></RouterLink>
      <nav aria-label="주요 메뉴"><RouterLink to="/" exact-active-class="is-current">Blog</RouterLink><template v-if="auth.user?.role === 'ADMIN'"><RouterLink to="/admin">관리자</RouterLink><RouterLink to="/posts/create" class="write-link">글 작성 ↗</RouterLink></template><button v-if="auth.user" @click="signOut">로그아웃</button><RouterLink v-else to="/login">로그인</RouterLink></nav>
    </header>
    <main class="blog-page"><p v-if="message" role="alert">{{ message }}</p><slot /></main>
    <footer class="blog-footer"><span>© {{ new Date().getFullYear() }} DevFlow</span><RouterLink to="/status">서비스 상태 <span aria-hidden="true">↗</span></RouterLink></footer>
  </div>
</template>
