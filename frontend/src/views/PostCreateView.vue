<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, auth, ApiError } from '../auth'
import { getManagedPost, type ManagedPost } from '../api/admin'
import type { Category } from '../types/blog'
import MarkdownEditorLayout from '../components/editor/MarkdownEditorLayout.vue'
import CategoryInput from '../components/editor/CategoryInput.vue'
import AiDraftPanel from '../components/editor/AiDraftPanel.vue'
import AiSources from '../components/blog/AiSources.vue'
import { validAttribution, type AiAttribution, type AiDraft } from '../types/ai'

const title = ref(''), markdown = ref(''), tagInput = ref('')
const tags = computed(() => [...new Set(tagInput.value.split(',').map(tag => tag.trim()).filter(Boolean))])
const notice = ref(''), busy = ref(false), loaded = ref(false)
const route = useRoute(), postId = ref<number | null>(null), categories = ref<Category[]>([])
const categoryId = ref<number | null>(null), summary = ref(''), status = ref('DRAFT')
const version = ref<number | null>(null), conflict = ref(false)
const aiAttribution = ref<AiAttribution | null>(null)
function addCategory(category: Category) {
  categories.value = [...categories.value.filter(c => c.id !== category.id), category].sort((a,b) => a.sortOrder - b.sortOrder || a.id - b.id)
}
function applyAiDraft(draft: AiDraft) {
  markdown.value = draft.contentMarkdown
  aiAttribution.value = { draftId: draft.id, question: draft.question, sources: draft.sources }
  notice.value = 'AI 초안을 편집기에 적용했습니다. 내용을 검토하고 수정한 뒤 저장하세요.'
}
// Capture the key for this editor instance: route params may change before unmount.
const storageKey = `devflow-editor-${auth.user?.id ?? 'admin'}-${route.params.id ?? 'new'}`
const snapshot = () => JSON.stringify({ title: title.value, markdown: markdown.value, categoryId: categoryId.value, summary: summary.value, tags: tagInput.value, version: version.value, aiAttribution: aiAttribution.value })
const savedSnapshot = ref('')
const dirty = computed(() => loaded.value && snapshot() !== savedSnapshot.value)
let timer: ReturnType<typeof setTimeout> | undefined
let pendingDraft = false
function flushDraft() {
  clearTimeout(timer)
  if (!pendingDraft || !loaded.value) return
  try { localStorage.setItem(storageKey, snapshot()); pendingDraft = false; notice.value = '브라우저에 임시저장했습니다.' }
  catch { notice.value = '브라우저 임시저장을 사용할 수 없습니다. 서버에 저장하세요.' }
}
function beforeUnload(event: BeforeUnloadEvent) {
  flushDraft()
  if (dirty.value) { event.preventDefault(); event.returnValue = '' }
}
function visibilityChanged() { if (document.visibilityState === 'hidden') flushDraft() }
onMounted(async () => {
  window.addEventListener('pagehide', flushDraft)
  window.addEventListener('beforeunload', beforeUnload)
  document.addEventListener('visibilitychange', visibilityChanged)
  try {
    categories.value = await api<Category[]>('/categories')
    if (route.params.id) {
      const p = await getManagedPost(String(route.params.id))
      postId.value = p.id; title.value = p.title; markdown.value = p.contentMarkdown
      categoryId.value = p.categoryId; summary.value = p.summary; status.value = p.status
      version.value = p.version
      if (p.aiDraftId) {
        const draft = await api<AiDraft>(`/admin/ai-drafts/${p.aiDraftId}`)
        aiAttribution.value = { draftId: draft.id, question: draft.question, sources: draft.sources }
      }
    }
    savedSnapshot.value = snapshot()
    loaded.value = true
  } catch { notice.value = '게시글을 불러오지 못했습니다.' }
})
async function save(publish = false) {
  if (busy.value || !loaded.value || conflict.value) return
  if (!title.value.trim()) { notice.value = '제목을 입력하세요.'; return }
  flushDraft()
  busy.value = true
  try {
    const p = await api<ManagedPost>(postId.value ? `/admin/posts/${postId.value}` : '/admin/posts', postId.value ? 'PUT' : 'POST', {
      title: title.value, contentMarkdown: markdown.value, summary: summary.value, categoryId: categoryId.value,
      version: version.value,
      aiDraftId: aiAttribution.value?.draftId ?? null,
    })
    postId.value = p.id
    version.value = p.version
    if (publish) { const published = await api<ManagedPost>(`/admin/posts/${p.id}/publish?version=${p.version}`, 'PATCH'); status.value = published.status; version.value = published.version }
    savedSnapshot.value = snapshot()
    pendingDraft = false
    clearTimeout(timer)
    const success = publish ? '발행했습니다.' : '서버에 저장했습니다.'
    try { localStorage.removeItem(storageKey); notice.value = success }
    catch { notice.value = `${success} 브라우저 임시저장은 삭제하지 못했습니다.` }
  } catch (error) {
    if (error instanceof ApiError && error.code === 'POST_CONFLICT') conflict.value = true
    flushDraft()
    notice.value = error instanceof Error ? error.message : '저장에 실패했습니다.'
  }
  finally { busy.value = false }
}
function clearDraft() {
  clearTimeout(timer)
  try { localStorage.removeItem(storageKey); pendingDraft = false; notice.value = '로컬 임시저장을 삭제했습니다.' }
  catch { notice.value = '로컬 임시저장을 삭제하지 못했습니다.' }
}
function restoreDraft() {
  if (!loaded.value || busy.value) return
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) { notice.value = '복원할 임시저장이 없습니다.'; return }
    const p = JSON.parse(raw)
    if (!p || typeof p !== 'object' || typeof p.title !== 'string' || typeof p.markdown !== 'string'
      || (p.categoryId != null && (!Number.isSafeInteger(p.categoryId) || p.categoryId < 1))
      || (p.summary != null && typeof p.summary !== 'string') || (p.tags != null && typeof p.tags !== 'string')) throw new Error('Invalid draft')
    if (p.version != null && (!Number.isSafeInteger(p.version) || p.version < 0)) throw new Error('Invalid draft version')
    if (p.aiAttribution != null && !validAttribution(p.aiAttribution)) throw new Error('Invalid AI attribution')
    title.value = p.title ?? ''; markdown.value = p.markdown ?? ''; categoryId.value = p.categoryId ?? null
    summary.value = p.summary ?? ''; tagInput.value = p.tags ?? ''
    if (p.version != null) version.value = p.version
    if (p.aiAttribution != null) aiAttribution.value = p.aiAttribution
    notice.value = '로컬 임시저장을 복원했습니다.'
  } catch { notice.value = '임시저장을 복원하지 못했습니다.' }
}
watch([title, markdown, categoryId, summary, tagInput, version, aiAttribution], () => {
  if (!loaded.value) return
  clearTimeout(timer)
  pendingDraft = true
  timer = setTimeout(flushDraft, 800)
}, { flush: 'sync' })
onBeforeUnmount(() => {
  flushDraft()
  window.removeEventListener('pagehide', flushDraft)
  window.removeEventListener('beforeunload', beforeUnload)
  document.removeEventListener('visibilitychange', visibilityChanged)
})
</script>

<template>
  <main>
    <RouterLink class="back-link" to="/">← DevFlow</RouterLink>
    <h1 class="create-post-heading">{{ postId ? '게시글 검토 및 수정' : '새 글 작성' }}</h1>
    <RouterLink to="/admin">관리자 목록</RouterLink><p>{{ status }} · {{ dirty ? '서버에 저장하지 않은 변경 사항이 있습니다.' : '서버 저장 후 변경 사항이 없습니다.' }} 로컬 임시저장은 이 브라우저에 남습니다. 공용 기기에서는 사용 후 삭제하세요.</p><button :disabled="!loaded || busy" @click="restoreDraft">로컬 임시저장 복원</button><button :disabled="!loaded || busy" @click="clearDraft">로컬 임시저장 삭제</button>
    <p v-if="conflict" role="alert">수정 충돌을 감지했습니다. 현재 입력을 복사해 보관하고 <RouterLink :to="`/admin/posts/${postId}/edit`" target="_blank" rel="noopener">새 탭에서 최신 글 확인</RouterLink> 후 변경 사항을 반영하세요. 이 화면에서는 덮어쓰지 않습니다.</p>
    <fieldset :inert="busy || !loaded" :disabled="busy || !loaded" style="border:0;padding:0;margin:16px 0">
    <AiDraftPanel @apply="applyAiDraft" />
    <section v-if="aiAttribution" class="applied-ai" aria-label="적용한 AI 초안 정보"><h2>AI 초안 적용됨</h2><p>발행 시 공개되는 원래 질문: {{ aiAttribution.question }}</p><p>본문을 수정해도 원래 질문과 출처는 유지됩니다.</p><AiSources :sources="aiAttribution.sources" /></section>
    <MarkdownEditorLayout
      v-model="markdown"
      v-model:title="title"
      placeholder="글을 작성하세요"
      @save="save(false)"
      @publish="save(true)"
    >
      <template #actions="{ uploading }"><button type="button" :disabled="uploading || conflict" @click="save(false)">임시 저장</button><button type="button" :disabled="uploading || conflict" @click="save(true)">발행</button></template>
      <template #metadata>
        <div class="post-metadata">
          <CategoryInput v-model="categoryId" :categories="categories" @added="addCategory" />
          <label>요약<input v-model="summary" maxlength="500"></label>
          <div>
            <label for="post-tags">태그<input id="post-tags" v-model="tagInput" name="tags" placeholder="Vue, TypeScript" aria-describedby="post-tags-help"></label>
            <p id="post-tags-help">쉼표로 구분합니다. 태그는 로컬 임시저장에만 보관됩니다.</p>
            <ul v-if="tags.length" class="post-tags" aria-label="입력한 태그"><li v-for="tag in tags" :key="tag">#{{ tag }}</li></ul>
          </div>
        </div>
      </template>
    </MarkdownEditorLayout>
    </fieldset>
    <p v-if="notice" role="status">{{ notice }}</p>
  </main>
</template>

<style scoped>
.back-link { color: #a5b4fc; text-decoration: none; }
.create-post-heading { font-size: clamp(1.6rem, 4vw, 2.3rem); margin-top: 24px; }
.applied-ai { margin-bottom: 20px; padding: 18px; border: 1px solid #d4d4d4; border-radius: 8px; overflow-wrap: anywhere; }
.post-metadata { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 2fr); gap: 20px; padding: 0 22px 20px; }
.post-metadata label { display: block; font-size: 13px; color: var(--md-muted); }
.post-metadata input { display: block; box-sizing: border-box; width: 100%; margin-top: 8px; padding: 10px 12px; border: 1px solid var(--md-border); border-radius: 5px; background: var(--md-panel); color: var(--md-text); font: inherit; }
.post-metadata p { margin: 6px 0 0; font-size: 12px; color: var(--md-muted); }
.post-tags { display: flex; flex-wrap: wrap; gap: 6px; list-style: none; margin: 8px 0 0; padding: 0; }
.post-tags li { padding: 3px 8px; border-radius: 5px; background: #292d51; color: #c7d2fe; font-size: 12px; }
@media (max-width: 760px) { .post-metadata { grid-template-columns: minmax(0, 1fr); padding-inline: 16px; } }
</style>
