<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, login, auth } from '../auth'
import BlogShell from '../components/blog/BlogShell.vue'
const route = useRoute(), router = useRouter()
const username = ref(''), password = ref(''), message = ref(''), busy = ref(false)
async function submit() {
  busy.value = true; message.value = ''
  try {
    await login(username.value, password.value)
    const target = route.query.redirect
    if (auth.user?.role === 'USER' && typeof target === 'string' && /^\/my-posts(?:$|\/\d+\/edit$)/.test(target)) await router.replace(target)
    else await router.replace(auth.user?.role === 'ADMIN' ? (typeof target === 'string' && /^\/(?:admin(?:\/|$)|posts\/create$|my-posts(?:$|\/\d+\/edit$))/.test(target) ? target : '/admin') : '/')
  } catch (error) {
    message.value = error instanceof ApiError && (error.code === 'SESSION_NOT_ESTABLISHED' || error.code === 'LOGIN_RATE_LIMITED')
      ? error.message
      : '로그인에 실패했습니다. 아이디 또는 이메일과 비밀번호를 확인하세요.'
  }
  finally { password.value = ''; busy.value = false }
}
</script>
<template>
  <BlogShell class="login-shell">
    <section class="login-panel" aria-labelledby="login-heading">
      <h1 id="login-heading">로그인</h1>
      <form class="auth-form" @submit.prevent="submit">
        <label>아이디 또는 이메일<input v-model="username" autocomplete="username" required maxlength="254"></label>
        <label>비밀번호<input v-model="password" type="password" autocomplete="current-password" required maxlength="72"></label>
        <button :disabled="busy">{{ busy ? '로그인 중…' : '로그인' }}</button>
        <p v-if="message" role="alert">{{ message }}</p>
      </form>
    </section>
  </BlogShell>
</template>

<style scoped>
.login-shell :deep(.blog-page) { display: grid; align-items: center; }
.login-panel { width: min(100%, 440px); margin-inline: auto; padding-block: 32px; text-align: center; }
.login-panel h1 { margin: 0 0 24px; }
.login-panel form, .login-panel p { margin: 0; }
</style>
