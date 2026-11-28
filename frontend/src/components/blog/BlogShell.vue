<script setup lang="ts">
import AnalyticsConsent from './AnalyticsConsent.vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { auth, logout } from '../../auth'
import { computed, ref, watch } from 'vue'
import PostSearchField from './PostSearchField.vue'
import ServiceStatus from './ServiceStatus.vue'
const message = ref('')
const route = useRoute(), router = useRouter()
const showLogin = computed(() => auth.ready && !auth.user && !route.matched.some(record => record.path === '/login'))
const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const statusOpen = ref(false)
const statusDialog = ref<HTMLDialogElement | null>(null)
watch(statusDialog, element => element?.showModal?.(), { flush: 'post' })
watch(isAdmin, admin => { if (!admin) statusOpen.value = false })

function closeStatus() {
  statusDialog.value?.close?.()
  statusOpen.value = false
}
const searchInput = ref(typeof route.query.q === 'string' ? route.query.q : '')
watch(() => route.query.q, value => { searchInput.value = typeof value === 'string' ? value : '' })
function searchPosts() { router.push({ name: 'post-list', query: { q: searchInput.value.trim() || undefined } }) }
async function signOut() { try { await logout() } catch { message.value = '로그아웃에 실패했습니다. 다시 시도해 주세요.' } }
</script>

<template>
  <div class="blog-shell">
    <a class="skip-to-content" href="#main-content">본문으로 바로 가기</a>
    <header class="blog-header">
      <RouterLink class="blog-brand" to="/" aria-label="DevFlow 홈"><span class="brand-mark" aria-hidden="true">/</span>DevFlow<span class="brand-dot">.</span></RouterLink>
      <div class="blog-header-controls">
        <form class="blog-header-search" role="search" aria-label="블로그 전체 검색" @submit.prevent="searchPosts"><PostSearchField id="header-post-search" v-model="searchInput" hide-label /></form>
        <nav v-if="auth.user?.role === 'ADMIN'" class="blog-primary-nav" aria-label="주요 메뉴">
          <RouterLink to="/my-posts" exact-active-class="is-current">내 글</RouterLink>
          <RouterLink to="/admin" exact-active-class="is-current">관리자</RouterLink>
        </nav>
        <div v-if="auth.user || showLogin" class="blog-account-actions" role="group" aria-label="작성 및 계정">
          <RouterLink v-if="auth.user?.role === 'ADMIN'" to="/posts/create" class="write-link outlined-link">글 작성</RouterLink>
          <button v-if="auth.user" type="button" @click="signOut">로그아웃</button>
          <RouterLink v-else class="outlined-link" to="/login">로그인</RouterLink>
        </div>
      </div>
    </header>
    <main id="main-content" class="blog-page" tabindex="-1"><p v-if="message" role="alert">{{ message }}</p><slot /></main>
    <footer class="blog-footer">
      <span>© {{ new Date().getFullYear() }} DevFlow</span>
      <AnalyticsConsent show-settings />
      <button v-if="isAdmin" type="button" class="outlined-link" aria-haspopup="dialog"
        @click="statusOpen = true">서비스 상태</button>
    </footer>
    <dialog v-if="isAdmin && statusOpen" ref="statusDialog" class="status-dialog"
      aria-labelledby="health-title" @close="statusOpen = false">
      <ServiceStatus>
        <template #actions>
          <button type="button" autofocus @click="closeStatus">닫기</button>
        </template>
      </ServiceStatus>
    </dialog>
  </div>
</template>

<style scoped>
.status-dialog { width: min(440px, calc(100% - 32px)); max-height: calc(100% - 32px); padding: 24px; border: 1px solid #dedede; border-radius: 10px; background: #fff; color: #171717; }
.status-dialog::backdrop { background: rgb(0 0 0 / 45%); }
.status-dialog :deep(h2) { margin-top: 0; }
</style>
