<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Profile } from '../auth'
import { getManagedPosts, type ManagedPost } from '../api/admin'
import BlogShell from '../components/blog/BlogShell.vue'
const posts = ref<ManagedPost[]>([]), accounts = ref<Profile[]>([]), message = ref(''), drafts = ref(false), busy = ref(false)
const username = ref(''), email = ref(''), password = ref(''), role = ref('USER'), token = ref('')
async function load() { posts.value = await getManagedPosts(drafts.value); accounts.value = await api<Profile[]>('/admin/accounts') }
async function run(action: () => Promise<unknown>) { if (busy.value) return; busy.value = true; message.value = ''; try { await action(); await load() } catch (e) { message.value = e instanceof Error ? e.message : '요청 실패' } finally { busy.value = false } }
async function createAccount() { await api('/admin/accounts', 'POST', { username: username.value, email: email.value, password: role.value === 'USER' ? password.value : undefined, role: role.value }); username.value = ''; email.value = ''; password.value = '' }
async function issue(id: number) { token.value = ''; const result = await api<{ token: string }>(`/admin/accounts/${id}/token`, 'POST'); token.value = result.token }
onMounted(() => run(async () => {}))
</script>
<template><BlogShell><h1>관리자</h1><p v-if="message" role="alert">{{ message }}</p>
<RouterLink to="/posts/create">새 글 작성</RouterLink> <label><input v-model="drafts" type="checkbox" @change="run(async () => {})">초안 및 검토 대상만 보기</label>
<p>게시 일시 순 최대 500개 · Agent 초안은 본문 검토 후 발행하세요. 다른 작성자의 비공개 글은 표시하지 않습니다.</p>
<div class="admin-posts"><article v-for="p in posts" :key="p.id"><h2><RouterLink :to="`/admin/posts/${p.id}/edit`">{{ p.title }}</RouterLink></h2><p>{{ p.visibility === 'PRIVATE' ? '비공개' : '공개' }} · {{ p.status }} · {{ p.authorName ?? '기존 게시글' }} · {{ p.authorRole ?? 'LEGACY' }}</p>
<button v-if="p.status !== 'PUBLISHED' || p.visibility === 'PRIVATE'" :disabled="busy" @click="run(() => api(`/admin/posts/${p.id}/publish?version=${p.version}`, 'PATCH'))">승인 및 공개</button>
<button v-else :disabled="busy" @click="run(() => api(`/admin/posts/${p.id}/unpublish`, 'PATCH'))">발행 취소</button>
<button :disabled="busy" @click="run(() => api(`/admin/posts/${p.id}`, 'DELETE'))">삭제</button></article></div>
<h2>사용자 및 Agent 계정</h2><form class="auth-form" @submit.prevent="run(createAccount)"><label>아이디<input v-model="username" required pattern="[A-Za-z0-9_.-]{3,100}" autocomplete="off"></label><label>이메일<input v-model="email" type="email" required maxlength="254"></label><label>역할<select v-model="role"><option>USER</option><option>SYSTEM</option></select></label><label v-if="role === 'USER'">초기 비밀번호<input v-model="password" type="password" minlength="12" maxlength="72" required autocomplete="new-password"></label><button :disabled="busy">계정 생성</button></form>
<ul><li v-for="a in accounts" :key="a.id">{{ a.username }} · {{ a.role }} · {{ a.status }} <template v-if="a.role !== 'ADMIN'"><button :disabled="busy" @click="run(() => api(`/admin/accounts/${a.id}/status`, 'PATCH', { status: a.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' }))">{{ a.status === 'ACTIVE' ? '비활성화' : '활성화' }}</button><template v-if="a.role === 'SYSTEM'"><button :disabled="busy || a.status !== 'ACTIVE'" @click="run(() => issue(a.id))">토큰 발급 / 교체</button><button :disabled="busy" @click="run(() => api(`/admin/accounts/${a.id}/token`, 'DELETE'))">토큰 폐기</button></template></template></li></ul>
<div v-if="token" role="status"><p>새 토큰은 이 화면에서만 표시됩니다. 안전하게 복사한 뒤 닫으세요. 기존 토큰은 폐기되었습니다. 유효기간: 90일.</p><input :value="token" readonly aria-label="새 서비스 토큰"><button @click="token = ''">닫기</button></div>
</BlogShell></template>
