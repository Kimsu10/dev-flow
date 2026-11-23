<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, auth, type Profile } from '../auth'
import { getManagedPosts, type ManagedPost } from '../api/admin'
import BlogShell from '../components/blog/BlogShell.vue'
import { isFuturePublication, publicationLabels, publicationState } from '../utils/publication'
const posts = ref<ManagedPost[]>([]), accounts = ref<Profile[]>([]), message = ref(''), drafts = ref(true), busy = ref(false)
const notice = ref('')
const username = ref(''), email = ref(''), password = ref(''), role = ref('USER'), token = ref('')
async function load() {
  const [list, profiles] = await Promise.all([getManagedPosts(drafts.value), api<Profile[]>('/admin/accounts')])
  posts.value = list.filter(post => post.authorId !== auth.user?.id && (!drafts.value || post.authorRole === 'SYSTEM'))
  accounts.value = profiles
}
async function run(action?: () => Promise<unknown>, success = '') {
  if (busy.value) return
  busy.value = true; message.value = ''; notice.value = ''
  try {
    await action?.()
    notice.value = success
    await load()
  } catch (e) { message.value = e instanceof Error ? e.message : '요청 실패' }
  finally { busy.value = false }
}
function removePost(post: ManagedPost) {
  if (!window.confirm(`“${post.title}” 글을 삭제하시겠습니까?`)) return
  return run(() => api(`/admin/posts/${post.id}`, 'DELETE'), '글을 삭제했습니다.')
}
function publish(post: ManagedPost) {
  const scheduled = isFuturePublication(post.publishedAt)
  return run(() => api(`/admin/posts/${post.id}/publish?version=${post.version}`, 'PATCH'), scheduled ? '예약 공개를 설정했습니다.' : '글을 공개했습니다.')
}
async function createAccount() { await api('/admin/accounts', 'POST', { username: username.value, email: email.value, password: role.value === 'USER' ? password.value : undefined, role: role.value }); username.value = ''; email.value = ''; password.value = '' }
async function issue(id: number) { token.value = ''; const result = await api<{ token: string }>(`/admin/accounts/${id}/token`, 'POST'); token.value = result.token }
onMounted(() => run())
</script>
<template><BlogShell><h1>관리자</h1><p v-if="message" role="alert">{{ message }} <button :disabled="busy" @click="run()">다시 불러오기</button></p><p v-if="notice" role="status">{{ notice }}</p>
<p>내가 작성한 글은 <RouterLink to="/my-posts">내 글</RouterLink>에서 관리합니다.</p>
<section aria-labelledby="admin-posts-heading">
<h2 id="admin-posts-heading">게시글 운영</h2>
<label>보기 <select v-model="drafts" :disabled="busy" @change="run()"><option :value="true">Agent 검토 대기</option><option :value="false">전체 운영글</option></select></label>
<p v-if="drafts">Agent 초안의 본문을 검토한 뒤 승인하세요.</p>
<p v-if="busy" role="status">처리 중입니다.</p>
<p v-else-if="!message && !posts.length">{{ drafts ? '검토할 Agent 초안이 없습니다.' : '관리할 운영글이 없습니다.' }}</p>
<div v-if="!message" class="admin-posts"><article v-for="p in posts" :key="p.id"><h3><RouterLink :to="`/admin/posts/${p.id}/edit`">{{ p.title }}</RouterLink></h3><p>{{ publicationLabels[publicationState(p)] }} · {{ p.authorName ?? '작성자 미지정' }}{{ p.authorRole === 'SYSTEM' ? ' · Agent' : '' }}</p>
<p v-if="p.aiDraftId && p.status !== 'PUBLISHED'">공개하면 AI 원래 질문과 출처도 함께 공개됩니다.</p>
<button v-if="p.status !== 'PUBLISHED'" :disabled="busy" @click="publish(p)">{{ isFuturePublication(p.publishedAt) ? '승인 및 예약 공개' : '승인 및 공개' }}</button>
<button v-else :disabled="busy" @click="run(() => api(`/admin/posts/${p.id}/unpublish?version=${p.version}`, 'PATCH'), '발행을 취소했습니다.')">{{ publicationState(p) === 'scheduled' ? '예약 취소' : '발행 취소' }}</button>
<button :disabled="busy" @click="removePost(p)">삭제</button></article></div>
</section>
<section aria-labelledby="admin-accounts-heading" class="admin-accounts">
<h2 id="admin-accounts-heading">사용자 및 Agent 계정</h2><form class="auth-form" @submit.prevent="run(createAccount)"><label>아이디<input v-model="username" required pattern="[A-Za-z0-9_.-]{3,100}" autocomplete="off"></label><label>이메일<input v-model="email" type="email" required maxlength="254"></label><label>역할<select v-model="role"><option>USER</option><option>SYSTEM</option></select></label><label v-if="role === 'USER'">초기 비밀번호<input v-model="password" type="password" minlength="12" maxlength="72" required autocomplete="new-password"></label><button :disabled="busy">계정 생성</button></form>
<ul><li v-for="a in accounts" :key="a.id">{{ a.username }} · {{ a.role }} · {{ a.status }} <template v-if="a.role !== 'ADMIN'"><button :disabled="busy" @click="run(() => api(`/admin/accounts/${a.id}/status`, 'PATCH', { status: a.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' }))">{{ a.status === 'ACTIVE' ? '비활성화' : '활성화' }}</button><template v-if="a.role === 'SYSTEM'"><button :disabled="busy || a.status !== 'ACTIVE'" @click="run(() => issue(a.id))">토큰 발급 / 교체</button><button :disabled="busy" @click="run(() => api(`/admin/accounts/${a.id}/token`, 'DELETE'))">토큰 폐기</button></template></template></li></ul>
<div v-if="token" role="status"><p>새 토큰은 이 화면에서만 표시됩니다. 안전하게 복사한 뒤 닫으세요. 기존 토큰은 폐기되었습니다. 유효기간: 90일.</p><input :value="token" readonly aria-label="새 서비스 토큰"><button @click="token = ''">닫기</button></div>
</section>
</BlogShell></template>

<style scoped>
.admin-accounts { margin-top: 48px; }
.admin-posts article { border-bottom-color: #dedede; overflow-wrap: anywhere; }
</style>
