<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../auth'
import { getMyPosts, type ManagedPost } from '../api/admin'
import BlogShell from '../components/blog/BlogShell.vue'
const posts = ref<ManagedPost[]>([]), loading = ref(true), busy = ref(false), error = ref('')
async function load() {
  loading.value = true; error.value = ''
  try { posts.value = await getMyPosts() }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '내 글을 불러오지 못했습니다.' }
  finally { loading.value = false }
}
async function makePublic(post: ManagedPost) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await api(`/admin/posts/${post.id}/publish?version=${post.version}`, 'PATCH'); await load() }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '공개 전환을 완료하지 못했습니다.' }
  finally { busy.value = false }
}
function date(post: ManagedPost) { return post.publishedAt ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Seoul' }).format(new Date(post.publishedAt)) : '게시 일시 미설정' }
function scheduled(post: ManagedPost) { return post.visibility === 'PUBLIC' && !!post.publishedAt && new Date(post.publishedAt).getTime() > Date.now() }
onMounted(load)
</script>

<template>
  <BlogShell>
    <h1>내 글</h1><RouterLink class="outlined-link" to="/posts/create">새 글 작성</RouterLink>
    <p>내가 작성한 글만 게시 일시 순으로 표시합니다. 비공개 글은 작성자만 조회·수정할 수 있습니다.</p>
    <p v-if="loading" role="status">내 글을 불러오는 중입니다.</p>
    <div v-if="error" role="alert">{{ error }} <button :disabled="busy" @click="load">다시 불러오기</button></div>
    <p v-if="!loading && !error && !posts.length">아직 작성한 글이 없습니다.</p>
    <div class="my-posts"><article v-for="post in posts" :key="post.id">
      <h2><RouterLink :to="`/admin/posts/${post.id}/edit`">{{ post.title }}</RouterLink></h2>
      <p><strong>{{ post.visibility === 'PRIVATE' ? '비공개 · 작성자만' : '공개' }}</strong> · <time :datetime="post.publishedAt || undefined">{{ date(post) }}</time></p>
      <p v-if="post.aiDraftId && post.visibility === 'PRIVATE'">공개로 전환하면 AI 원래 질문과 출처도 함께 공개됩니다.</p>
      <p v-if="scheduled(post)">예약 공개 · 지정한 게시 일시부터 공개 목록에 표시됩니다.</p>
      <div class="my-post-actions"><RouterLink class="outlined-link" :to="`/admin/posts/${post.id}/edit`">재편집</RouterLink><RouterLink v-if="post.status === 'PUBLISHED' && !scheduled(post)" class="outlined-link" :to="`/posts/${post.id}`">글 보기</RouterLink><button v-if="post.visibility === 'PRIVATE' || post.status !== 'PUBLISHED'" :disabled="busy || loading" @click="makePublic(post)">공개로 전환</button></div>
    </article></div>
  </BlogShell>
</template>

<style scoped>
.my-posts article { padding: 24px 0; border-bottom: 1px solid #dedede; overflow-wrap: anywhere; }
.my-posts h2 { font-size: 22px; }
.my-post-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
</style>
