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
      <div class="blog-header-controls">
        <nav class="blog-primary-nav" aria-label="주요 메뉴">
          <RouterLink to="/" exact-active-class="is-current">Blog</RouterLink>
          <RouterLink to="/posts" exact-active-class="is-current">글 검색</RouterLink>
          <template v-if="auth.user?.role === 'ADMIN'">
            <RouterLink to="/my-posts" exact-active-class="is-current">내 글</RouterLink>
            <RouterLink to="/admin" exact-active-class="is-current">관리자</RouterLink>
            <RouterLink to="/admin/analytics" exact-active-class="is-current">Analytics</RouterLink>
          </template>
        </nav>
        <div v-if="auth.user || auth.ready" class="blog-account-actions" role="group" aria-label="작성 및 계정">
          <RouterLink v-if="auth.user?.role === 'ADMIN'" to="/posts/create" class="write-link outlined-link">글 작성</RouterLink>
          <button v-if="auth.user" type="button" @click="signOut">로그아웃</button>
          <RouterLink v-else-if="auth.ready" class="outlined-link" to="/login">로그인</RouterLink>
        </div>
      </div>
    </header>
    <main id="main-content" class="blog-page" tabindex="-1"><p v-if="message" role="alert">{{ message }}</p><slot /></main>
    <AnalyticsConsent v-if="!auth.user || auth.user.role === 'USER'" />
    <footer class="blog-footer"><span>© {{ new Date().getFullYear() }} DevFlow</span><RouterLink class="outlined-link" to="/status">서비스 상태</RouterLink></footer>
  </div>
</template>
