<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, auth } from '../auth'
import BlogShell from '../components/blog/BlogShell.vue'
const route = useRoute(), router = useRouter()
const username = ref(''), password = ref(''), message = ref(''), busy = ref(false)
async function submit() {
  busy.value = true; message.value = ''
  try {
    await login(username.value, password.value)
    const target = route.query.redirect
    await router.replace(auth.user?.role === 'ADMIN' ? (typeof target === 'string' && /^\/(?:admin(?:\/|$)|posts\/create$|my-posts$)/.test(target) ? target : '/admin') : '/')
  } catch { message.value = '로그인에 실패했습니다. 아이디 또는 이메일과 비밀번호를 확인하세요.' }
  finally { password.value = ''; busy.value = false }
}
</script>
<template><BlogShell><h1>로그인</h1><form class="auth-form" @submit.prevent="submit">
<label>아이디 또는 이메일<input v-model="username" autocomplete="username" required maxlength="254"></label>
<label>비밀번호<input v-model="password" type="password" autocomplete="current-password" required maxlength="72"></label>
<button :disabled="busy">{{ busy ? '로그인 중…' : '로그인' }}</button><p v-if="message" role="alert">{{ message }}</p>
</form></BlogShell></template>
