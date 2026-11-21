<script setup lang="ts">
import AnalyticsConsent from './AnalyticsConsent.vue'
import { RouterLink } from 'vue-router'
import { auth, logout } from '../../auth'
import { ref } from 'vue'
const message = ref('')
async function signOut() { try { await logout() } catch { message.value = '로그아웃에 실패했습니다. 다시 시도해 주세요.' } }
</script>

<template>
  <div class="blog-shell">
    <a class="skip-to-content" href="#main-content">본문으로 바로 가기</a>
    <header class="blog-header">
      <RouterLink class="blog-brand" to="/" aria-label="DevFlow 홈"><span class="brand-mark" aria-hidden="true">/</span>DevFlow<span class="brand-dot">.</span></RouterLink>
      <nav aria-label="주요 메뉴"><RouterLink to="/" exact-active-class="is-current">Blog</RouterLink><RouterLink to="/posts">글 검색</RouterLink><template v-if="auth.user?.role === 'ADMIN'"><RouterLink to="/my-posts">내 글</RouterLink><RouterLink to="/admin">관리자</RouterLink><RouterLink to="/admin/analytics">Analytics</RouterLink><RouterLink to="/posts/create" class="write-link outlined-link">글 작성</RouterLink></template><button v-if="auth.user" @click="signOut">로그아웃</button><RouterLink v-else to="/login">로그인</RouterLink></nav>
    </header>
    <main id="main-content" class="blog-page" tabindex="-1"><p v-if="message" role="alert">{{ message }}</p><slot /></main>
    <AnalyticsConsent v-if="!auth.user || auth.user.role === 'USER'" />
    <footer class="blog-footer"><span>© {{ new Date().getFullYear() }} DevFlow</span><RouterLink class="outlined-link" to="/status">서비스 상태</RouterLink></footer>
  </div>
</template>
