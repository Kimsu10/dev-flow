<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
const state = ref<'loading' | 'up' | 'down' | 'error'>('loading')
const labels = {
  loading: '서버 상태 확인 중…',
  up: 'API와 데이터베이스가 정상 동작 중입니다.',
  down: '데이터베이스 연결을 확인해 주세요.',
  error: '서버에 연결할 수 없습니다.',
}
async function checkHealth() {
  state.value = 'loading'
  try {
    const response = await fetch('/api/health', { signal: AbortSignal.timeout(5000) })
    const body: unknown = await response.json()
    if (typeof body !== 'object' || body === null || !('status' in body) || !('database' in body)) throw new Error('Invalid response')
    if (response.ok && body.status === 'UP' && body.database === 'UP') state.value = 'up'
    else if (response.status === 503 && body.status === 'DOWN' && body.database === 'DOWN') state.value = 'down'
    else throw new Error('Unexpected response')
  } catch { state.value = 'error' }
}
onMounted(checkHealth)
</script>

<template>
  <main>
    <p class="eyebrow">개발의 흐름을 기록하다</p>
    <h1>DevFlow</h1>
    <p>개인 개발 블로그의 첫 시작.</p>
    <section aria-labelledby="health-title" :aria-busy="state === 'loading'">
      <h2 id="health-title">서비스 상태</h2>
      <p role="status" :class="state">{{ labels[state] }}</p>
      <button :disabled="state === 'loading'" @click="checkHealth">다시 확인</button>
    </section>
    <RouterLink class="write-post-link" to="/posts/create">글 작성하기</RouterLink>
  </main>
</template>
