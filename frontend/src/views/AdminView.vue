<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, auth, type Profile } from '../auth'
import { getManagedPosts, type ManagedPost } from '../api/admin'
import BlogShell from '../components/blog/BlogShell.vue'
import { isFuturePublication, publicationLabels, publicationState } from '../utils/publication'
const posts = ref<ManagedPost[]>([]), accounts = ref<Profile[]>([])
const postRequest = reactive({ busy: true, error: '', notice: '' })
const accountRequest = reactive({ busy: true, error: '', notice: '' })
const roles = { ADMIN: '관리자', USER: '사용자', SYSTEM: 'Agent' }
const statuses = { ACTIVE: '활성', DISABLED: '비활성' }
const filtersKey = auth.user ? `devflow:admin:filters:${auth.user.id}` : undefined
function readFilters() {
  try {
    if (filtersKey) return JSON.parse(sessionStorage.getItem(filtersKey) ?? '{}') as {
      drafts?: boolean
      postStatus?: string
      accountRole?: string
      accountStatus?: string
    } | null
  } catch { /* Storage can be blocked or contain an invalid value. */ }
  return null
}
function validFilter(value: string | undefined, options: Record<string, string>) {
  return typeof value === 'string' && Object.hasOwn(options, value) ? value : ''
}
const savedFilters = readFilters()
const drafts = ref(savedFilters?.drafts !== false)
const postQuery = ref(''), postStatus = ref(validFilter(savedFilters?.postStatus, publicationLabels))
const accountQuery = ref(''), accountRole = ref(validFilter(savedFilters?.accountRole, roles))
const accountStatus = ref(validFilter(savedFilters?.accountStatus, statuses))
watch([drafts, postStatus, accountRole, accountStatus], ([drafts, postStatus, accountRole, accountStatus]) => {
  try {
    if (filtersKey) sessionStorage.setItem(filtersKey, JSON.stringify({ drafts, postStatus, accountRole, accountStatus }))
  } catch { /* Filters still work in the current view. */ }
}, { flush: 'sync' })
function matches(query: string, ...fields: (string | null)[]) {
  const term = query.trim().toLowerCase()
  return !term || fields.some(field => field?.toLowerCase().includes(term))
}
const filteredPosts = computed(() => posts.value.filter(post =>
  (!postStatus.value || publicationState(post) === postStatus.value) && matches(postQuery.value, post.title, post.authorName)))
const filteredAccounts = computed(() => accounts.value.filter(account =>
  (!accountRole.value || account.role === accountRole.value) && (!accountStatus.value || account.status === accountStatus.value)
  && matches(accountQuery.value, account.username, account.email)))
const username = ref(''), email = ref(''), password = ref(''), role = ref('USER')
const issuedToken = ref<{ accountId: number; username: string; value: string } | null>(null)
async function loadPosts() {
  const list = await getManagedPosts(drafts.value)
  posts.value = list.filter(post => post.authorId !== auth.user?.id && (!drafts.value || post.authorRole === 'SYSTEM'))
}
async function loadAccounts() {
  accounts.value = await api<Profile[]>('/admin/accounts')
}
async function run(request: typeof postRequest, load: () => Promise<void>, action?: () => Promise<unknown>, success = '') {
  if (request.busy) return
  request.busy = true
  request.error = ''
  request.notice = ''
  try {
    await action?.()
    request.notice = success
    await load()
  } catch (e) { request.error = e instanceof Error ? e.message : '요청을 완료하지 못했습니다.' }
  finally { request.busy = false }
}
const runPosts = (action?: () => Promise<unknown>, success?: string) => run(postRequest, loadPosts, action, success)
const runAccounts = (action?: () => Promise<unknown>, success?: string) => run(accountRequest, loadAccounts, action, success)
function changePostList() {
  postStatus.value = ''
  posts.value = []
  return runPosts()
}
function resetPostFilters() {
  postQuery.value = ''
  postStatus.value = ''
  if (!drafts.value) {
    drafts.value = true
    return changePostList()
  }
}
function resetAccountFilters() {
  accountQuery.value = ''
  accountRole.value = ''
  accountStatus.value = ''
}
function removePost(post: ManagedPost) {
  if (!window.confirm(`“${post.title}” 글을 삭제하시겠습니까?`)) return
  return runPosts(() => api(`/admin/posts/${post.id}`, 'DELETE'), '글을 삭제했습니다.')
}
function publish(post: ManagedPost) {
  const scheduled = isFuturePublication(post.publishedAt)
  return runPosts(() => api(`/admin/posts/${post.id}/publish?version=${post.version}`, 'PATCH'), scheduled ? '예약 공개를 설정했습니다.' : '글을 공개했습니다.')
}
async function createAccount() { await api('/admin/accounts', 'POST', { username: username.value, email: email.value, password: role.value === 'USER' ? password.value : undefined, role: role.value }); username.value = ''; email.value = ''; password.value = '' }
function changeAccountStatus(account: Profile) {
  const status = account.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  if (status === 'DISABLED' && account.role === 'SYSTEM' &&
    !window.confirm(`“${account.username}”을 비활성화하면 서비스 토큰도 폐기됩니다. 계속하시겠습니까?`)) return
  return runAccounts(async () => {
    await api(`/admin/accounts/${account.id}/status`, 'PATCH', { status })
    if (status === 'DISABLED' && issuedToken.value?.accountId === account.id) issuedToken.value = null
  }, '계정 상태를 변경했습니다.')
}
function issue(account: Profile) {
  if (!window.confirm(`“${account.username}”의 새 토큰을 발급하면 기존 토큰이 폐기됩니다. 계속하시겠습니까?`)) return
  return runAccounts(async () => {
    const result = await api<{ token: string }>(`/admin/accounts/${account.id}/token`, 'POST')
    issuedToken.value = { accountId: account.id, username: account.username, value: result.token }
  })
}
function revoke(account: Profile) {
  if (!window.confirm(`“${account.username}”의 서비스 토큰을 폐기하시겠습니까?`)) return
  return runAccounts(async () => {
    await api(`/admin/accounts/${account.id}/token`, 'DELETE')
    if (issuedToken.value?.accountId === account.id) issuedToken.value = null
  }, '토큰을 폐기했습니다.')
}
onMounted(() => {
  postRequest.busy = false
  accountRequest.busy = false
  void runPosts()
  void runAccounts()
})
</script>
<template>
  <BlogShell>
    <header class="admin-heading">
      <h1>관리자</h1>
      <nav class="admin-links" aria-label="관리자 메뉴">
        <a class="outlined-link" href="#admin-posts-heading">게시글 운영</a>
        <a class="outlined-link" href="#admin-accounts-heading">계정 관리</a>
        <RouterLink class="outlined-link" to="/my-posts">내 글</RouterLink>
      </nav>
    </header>
    <section class="admin-section" aria-labelledby="admin-posts-heading">
      <h2 id="admin-posts-heading">게시글 운영</h2>
      <div class="admin-filters">
        <label>목록<select v-model="drafts" :disabled="postRequest.busy" @change="changePostList"><option :value="true">Agent 검토 대기</option><option :value="false">전체 운영글</option></select></label>
        <label>글 검색<input v-model="postQuery" type="search" placeholder="제목 또는 작성자"></label>
        <label>게시 상태<select v-model="postStatus"><option value="">전체 상태</option><option v-for="(label, state) in publicationLabels" :key="state" :value="state">{{ label }}</option></select></label>
        <button v-if="!drafts || postQuery || postStatus" type="button" :disabled="postRequest.busy" @click="resetPostFilters">글 검색 조건 초기화</button>
      </div>
      <p v-if="postRequest.notice" role="status">{{ postRequest.notice }}</p>
      <p v-if="postRequest.error" class="error" role="alert">{{ postRequest.error }} <button @click="runPosts()">글 목록 다시 불러오기</button></p>
      <p v-else-if="postRequest.busy" role="status">글 목록을 처리 중입니다.</p>
      <p v-else class="list-count" role="status">표시 {{ filteredPosts.length }}개 / 불러온 글 {{ posts.length }}개 <span v-if="!drafts">· 최근 최대 500개 기준</span></p>
      <p v-if="!postRequest.busy && !postRequest.error && !filteredPosts.length">{{ posts.length ? '검색 조건에 맞는 글이 없습니다.' : drafts ? '검토할 Agent 초안이 없습니다.' : '관리할 운영글이 없습니다.' }}</p>
      <div v-if="!postRequest.error" class="admin-posts">
        <article v-for="p in filteredPosts" :key="p.id">
          <h3><RouterLink :to="`/admin/posts/${p.id}/edit`">{{ p.title }}</RouterLink></h3>
          <p><strong class="admin-badge">{{ publicationLabels[publicationState(p)] }}</strong> {{ p.authorName ?? '작성자 미지정' }}{{ p.authorRole === 'SYSTEM' ? ' · Agent' : '' }}</p>
          <p v-if="p.aiDraftId && p.status !== 'PUBLISHED'">공개하면 AI 원래 질문과 출처도 함께 공개됩니다.</p>
          <button v-if="p.status !== 'PUBLISHED'" :disabled="postRequest.busy" @click="publish(p)">{{ isFuturePublication(p.publishedAt) ? '승인 및 예약 공개' : '승인 및 공개' }}</button>
          <button v-else :disabled="postRequest.busy" @click="runPosts(() => api(`/admin/posts/${p.id}/unpublish?version=${p.version}`, 'PATCH'), '발행을 취소했습니다.')">{{ publicationState(p) === 'scheduled' ? '예약 취소' : '발행 취소' }}</button>
          <button :disabled="postRequest.busy" @click="removePost(p)">삭제</button>
        </article>
      </div>
    </section>
    <section class="admin-section" aria-labelledby="admin-accounts-heading">
      <h2 id="admin-accounts-heading">사용자 및 Agent 계정</h2>
      <div class="admin-filters">
        <label>계정 검색<input v-model="accountQuery" type="search" placeholder="아이디 또는 이메일"></label>
        <label>계정 역할<select v-model="accountRole"><option value="">전체 역할</option><option v-for="(label, key) in roles" :key="key" :value="key">{{ label }}</option></select></label>
        <label>계정 상태<select v-model="accountStatus"><option value="">전체 상태</option><option v-for="(label, key) in statuses" :key="key" :value="key">{{ label }}</option></select></label>
        <button v-if="accountQuery || accountRole || accountStatus" type="button" @click="resetAccountFilters">계정 검색 조건 초기화</button>
      </div>
      <p v-if="accountRequest.notice" role="status">{{ accountRequest.notice }}</p>
      <p v-if="accountRequest.error" class="error" role="alert">{{ accountRequest.error }} <button @click="runAccounts()">계정 목록 다시 불러오기</button></p>
      <p v-else-if="accountRequest.busy" role="status">계정 목록을 처리 중입니다.</p>
      <p v-else class="list-count" role="status">표시 {{ filteredAccounts.length }}개 / 불러온 계정 {{ accounts.length }}개</p>
      <p v-if="!accountRequest.busy && !accountRequest.error && !filteredAccounts.length">검색 조건에 맞는 계정이 없습니다.</p>
      <ul class="account-list">
        <li v-for="a in filteredAccounts" :key="a.id">
          <div><strong>{{ a.username }}</strong> <span class="admin-badge">{{ roles[a.role] }}</span> <span>{{ a.status === 'ACTIVE' ? statuses.ACTIVE : a.status === 'DISABLED' ? statuses.DISABLED : a.status }}</span><p>{{ a.email }}</p></div>
          <div v-if="a.role !== 'ADMIN'">
            <button :disabled="accountRequest.busy" @click="changeAccountStatus(a)">{{ a.status === 'ACTIVE' ? '비활성화' : '활성화' }}</button>
            <template v-if="a.role === 'SYSTEM'">
              <button :disabled="accountRequest.busy || a.status !== 'ACTIVE'" @click="issue(a)">토큰 발급 / 교체</button>
              <button :disabled="accountRequest.busy" @click="revoke(a)">토큰 폐기</button>
            </template>
          </div>
        </li>
      </ul>
      <div v-if="issuedToken" class="issued-token">
        <p role="status"><strong>{{ issuedToken.username }}</strong>의 새 토큰입니다. 안전하게 복사한 뒤 닫으세요. 유효기간은 90일입니다.</p>
        <input :value="issuedToken.value" readonly aria-label="새 서비스 토큰" autocomplete="off"><button @click="issuedToken = null">닫기</button>
      </div>
      <details class="create-account">
        <summary>새 계정 등록</summary>
        <form class="auth-form" @submit.prevent="runAccounts(createAccount, '새 계정을 등록했습니다.')">
          <label>아이디<input v-model="username" required pattern="[A-Za-z0-9_.-]{3,100}" autocomplete="off"></label>
          <label>이메일<input v-model="email" type="email" required maxlength="254"></label>
          <label>역할<select v-model="role"><option value="USER">사용자</option><option value="SYSTEM">Agent</option></select></label>
          <label v-if="role === 'USER'">초기 비밀번호<input v-model="password" type="password" minlength="12" maxlength="72" required autocomplete="new-password"></label>
          <button :disabled="accountRequest.busy">계정 생성</button>
        </form>
      </details>
    </section>
    <section class="admin-section admin-analytics" aria-labelledby="admin-analytics-heading">
      <h2 id="admin-analytics-heading">Analytics</h2>
      <p>블로그 방문 추이와 게시글별 통계를 확인합니다.</p>
      <RouterLink class="outlined-link" to="/admin/analytics">Analytics 열기</RouterLink>
    </section>
  </BlogShell>
</template>

<style scoped>
.admin-heading { padding-block: 24px; }
.admin-heading h1 { font-size: clamp(2rem, 5vw, 3rem); }
.admin-links, .admin-filters { display: flex; flex-wrap: wrap; gap: 12px; }
.admin-section { margin-top: 20px; padding: clamp(16px, 3vw, 28px); border: 1px solid #dedede; border-radius: 10px; }
.admin-section h2 { margin-top: 0; scroll-margin-top: 16px; }
.admin-filters { padding: 12px; border-radius: 8px; background: #f6f7f6; }
.admin-filters label { display: grid; flex: 1 1 180px; min-width: 0; gap: 6px; font-size: .875rem; }
.admin-filters button { align-self: end; }
.admin-filters input, .admin-filters select, .issued-token input { min-width: 0; width: 100%; padding: 8px; border: 1px solid #bcbcbc; border-radius: 6px; font: inherit; background: #fff; }
.list-count { color: #525252; font-size: .875rem; }
.admin-posts article, .account-list li { border-bottom: 1px solid #dedede; overflow-wrap: anywhere; }
.admin-posts h3 { margin: 0; }
.admin-posts h3 a { text-decoration: underline; text-underline-offset: .2em; }
.admin-badge { display: inline-block; padding: 2px 8px; border-radius: 20px; background: #e9f2ec; color: #1e4434; font-size: .8rem; }
.account-list { margin: 0; padding: 0; list-style: none; }
.account-list li { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 6px 16px; padding-block: 12px; }
.account-list p { margin: 4px 0 0; color: #525252; }
.issued-token, .create-account { margin-top: 16px; padding: 12px; background: #f6f7f6; border-radius: 8px; }
.issued-token button { margin-top: 8px; }
.create-account summary { cursor: pointer; font-weight: 600; }
</style>
