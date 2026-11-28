<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api, auth } from '../auth'
import { getMyPosts, type ManagedPost } from '../api/admin'
import BlogShell from '../components/blog/BlogShell.vue'
import { isFuturePublication, publicationLabels, publicationState } from '../utils/publication'
const posts = ref<ManagedPost[]>([]), loading = ref(true), busy = ref(false), error = ref('')
const notice = ref('')
const isTrial = auth.user?.role === 'USER'
const postPath = (id: number) => `${isTrial ? '/user' : '/admin'}/posts/${id}`
const editPath = (id: number) => `${isTrial ? '/my-posts' : '/admin/posts'}/${id}/edit`
async function load() {
  loading.value = true; error.value = ''
  try { posts.value = isTrial ? await api<ManagedPost[]>('/user/posts') : await getMyPosts() }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '내 글을 불러오지 못했습니다.' }
  finally { loading.value = false }
}
async function changePost(path: string, method: 'PATCH' | 'DELETE', success: string) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    await api(path, method)
    notice.value = success
    await load()
  }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '게시글 변경을 완료하지 못했습니다.' }
  finally { busy.value = false }
}
function makePublic(post: ManagedPost) {
  const success = isFuturePublication(post.publishedAt) ? '예약 공개를 설정했습니다.' : '글을 공개했습니다.'
  return changePost(`/admin/posts/${post.id}/publish?version=${post.version}`, 'PATCH', success)
}
function unpublish(post: ManagedPost) {
  return changePost(`/admin/posts/${post.id}/unpublish?version=${post.version}`, 'PATCH', '발행을 취소했습니다.')
}
function deletePost(post: ManagedPost) {
  if (!window.confirm(`“${post.title}” 글을 삭제하시겠습니까?`)) return
  return changePost(postPath(post.id), 'DELETE', '글을 삭제했습니다.')
}
function date(post: ManagedPost) { return post.publishedAt ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Seoul' }).format(new Date(post.publishedAt)) : '게시 일시 미설정' }
onMounted(load)
</script>

<template>
  <BlogShell>
    <h1>내 글</h1><RouterLink v-if="!isTrial" class="outlined-link" to="/posts/create">새 글 작성</RouterLink><RouterLink v-else class="outlined-link" to="/">AI 글 작성</RouterLink>
    <p>내가 작성한 글만 게시 일시 순으로 표시합니다. 비공개 글은 작성자만 조회·수정할 수 있습니다.</p>
    <p v-if="loading" role="status">내 글을 불러오는 중입니다.</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <div v-if="error" role="alert">{{ error }} <button :disabled="busy" @click="load">다시 불러오기</button></div>
    <p v-if="!loading && !error && !posts.length">아직 작성한 글이 없습니다.</p>
    <div class="my-posts"><article v-for="post in posts" :key="post.id">
      <h2><RouterLink :to="editPath(post.id)">{{ post.title }}</RouterLink></h2>
      <p><strong>{{ publicationLabels[publicationState(post)] }}</strong> · <time :datetime="post.publishedAt || undefined">{{ date(post) }}</time></p>
      <p v-if="post.aiDraftId && (post.visibility === 'PRIVATE' || post.status !== 'PUBLISHED')">공개로 전환하면 AI 원래 질문과 출처도 함께 공개됩니다.</p>
      <div class="my-post-actions">
        <RouterLink class="outlined-link" :to="editPath(post.id)">수정</RouterLink>
        <RouterLink v-if="publicationState(post) === 'public' || (post.status === 'PUBLISHED' && post.visibility === 'PRIVATE')" class="outlined-link" :to="`/posts/${post.id}`">글 보기</RouterLink>
        <button v-if="!isTrial && (post.visibility === 'PRIVATE' || post.status !== 'PUBLISHED')" :disabled="busy || loading" @click="makePublic(post)">{{ isFuturePublication(post.publishedAt) ? '예약 공개 설정' : '공개로 전환' }}</button>
        <button v-else-if="!isTrial" :disabled="busy || loading" @click="unpublish(post)">{{ publicationState(post) === 'scheduled' ? '예약 취소' : '발행 취소' }}</button>
        <button :disabled="busy || loading" @click="deletePost(post)">삭제</button>
      </div>
    </article></div>
  </BlogShell>
</template>

<style scoped>
.my-posts article { padding: 24px 0; border-bottom: 1px solid #dedede; overflow-wrap: anywhere; }
.my-posts h2 { font-size: 22px; }
.my-post-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
</style>
