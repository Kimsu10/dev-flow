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
import PublicationFields from '../components/editor/PublicationFields.vue'
import { isFuturePublication, publicationLabels, publicationState, toLocalDateTime, validPublicationInput } from '../utils/publication'

const title = ref(''), markdown = ref('')
const notice = ref(''), busy = ref(false), loaded = ref(false)
const route = useRoute(), postId = ref<number | null>(null), categories = ref<Category[]>([])
const categoryId = ref<number | null>(null), summary = ref('')
const version = ref<number | null>(null), conflict = ref(false)
const savedPost = ref<ManagedPost | null>(null), publishedInput = ref('')
const originalPublishedInput = computed(() => toLocalDateTime(savedPost.value?.publishedAt))
const savedPublication = computed(() => savedPost.value ? publicationState(savedPost.value) : 'draft')
const savedPublicationState = computed(() => publicationLabels[savedPublication.value])
const hasPublicSettings = computed(() => savedPublication.value === 'public' || savedPublication.value === 'scheduled')
const canMakePrivate = computed(() => hasPublicSettings.value
  && (savedPost.value?.authorId == null || savedPost.value.authorId === auth.user?.id))
const targetPublicationDate = computed(() => publishedInput.value || savedPost.value?.publishedAt)
const publishLabel = computed(() => {
  if (isFuturePublication(targetPublicationDate.value)) return savedPublication.value === 'scheduled' ? '예약 변경 저장' : '예약 공개'
  if (savedPublication.value === 'public') return '공개 글 수정 저장'
  if (savedPublication.value === 'review' || (savedPublication.value === 'draft' && savedPost.value?.authorRole === 'SYSTEM')) return '승인 후 공개'
  return '공개하기'
})
const keepLabel = computed(() => !savedPost.value || savedPublication.value === 'private'
  ? '비공개 저장'
  : savedPublication.value === 'review' ? '검토 상태로 저장' : '미발행 저장')
const aiAttribution = ref<AiAttribution | null>(null)
const aiPublicationConfirmed = ref(false)
const needsAiConfirmation = computed(() => !!aiAttribution.value
  && (!hasPublicSettings.value || aiAttribution.value.draftId !== savedPost.value?.aiDraftId))
function addCategory(category: Category) {
  categories.value = [...categories.value.filter(c => c.id !== category.id), category].sort((a,b) => a.sortOrder - b.sortOrder || a.id - b.id)
}
function applyAiDraft(draft: AiDraft) {
  aiPublicationConfirmed.value = false
  markdown.value = draft.contentMarkdown
  aiAttribution.value = { draftId: draft.id, question: draft.question, sources: draft.sources }
  notice.value = 'AI 초안을 편집기에 적용했습니다. 내용을 검토하고 수정한 뒤 저장하세요.'
}
// Capture the key for this editor instance: route params may change before unmount.
const storageKey = `devflow-editor-${auth.user?.id ?? 'admin'}-${route.params.id ?? 'new'}`
const draftState = ref<'checking' | 'available' | 'empty' | 'unavailable'>('checking')
const draftStatus = computed(() => ({
  checking: '임시저장 확인 중',
  available: '복원할 임시저장 있음',
  empty: '저장된 임시저장 없음',
  unavailable: '브라우저 저장소 사용 불가',
})[draftState.value])
const snapshot = () => JSON.stringify({ title: title.value, markdown: markdown.value, categoryId: categoryId.value, summary: summary.value, version: version.value, aiAttribution: aiAttribution.value, publishedInput: publishedInput.value })
const savedSnapshot = ref('')
const dirty = computed(() => loaded.value && snapshot() !== savedSnapshot.value)
let timer: ReturnType<typeof setTimeout> | undefined
let pendingDraft = false
function readDraft() {
  const raw = localStorage.getItem(storageKey)
  draftState.value = raw === null ? 'empty' : 'available'
  return raw
}
function refreshDraftState(event?: StorageEvent) {
  if (event?.key != null && event.key !== storageKey) return
  try { readDraft() }
  catch { draftState.value = 'unavailable' }
}
function flushDraft() {
  clearTimeout(timer)
  if (!pendingDraft || !loaded.value) return
  try {
    localStorage.setItem(storageKey, snapshot())
    pendingDraft = false
    draftState.value = 'available'
    notice.value = '브라우저에 임시저장했습니다.'
  } catch { notice.value = '브라우저 임시저장을 사용할 수 없습니다. 서버에 저장하세요.' }
}
function beforeUnload(event: BeforeUnloadEvent) {
  flushDraft()
  if (dirty.value) { event.preventDefault(); event.returnValue = '' }
}
function visibilityChanged() { if (document.visibilityState === 'hidden') flushDraft() }
onMounted(async () => {
  refreshDraftState()
  window.addEventListener('storage', refreshDraftState)
  window.addEventListener('pagehide', flushDraft)
  window.addEventListener('beforeunload', beforeUnload)
  document.addEventListener('visibilitychange', visibilityChanged)
  try {
    categories.value = await api<Category[]>('/categories')
    if (route.params.id) {
      const p = await getManagedPost(String(route.params.id))
      postId.value = p.id; title.value = p.title; markdown.value = p.contentMarkdown
      categoryId.value = p.categoryId; summary.value = p.summary
      version.value = p.version
      savedPost.value = p
      publishedInput.value = toLocalDateTime(p.publishedAt)
      if (p.aiDraftId) {
        const draft = await api<AiDraft>(`/admin/ai-drafts/${p.aiDraftId}`)
        aiAttribution.value = { draftId: draft.id, question: draft.question, sources: draft.sources }
      }
    }
    savedSnapshot.value = snapshot()
    loaded.value = true
  } catch { notice.value = '게시글을 불러오지 못했습니다.' }
})
async function save(action: 'keep' | 'private' | 'publish' = 'keep') {
  if (busy.value || !loaded.value || conflict.value) return
  if (!title.value.trim()) { notice.value = '제목을 입력하세요.'; return }
  if (!validPublicationInput(publishedInput.value)) { notice.value = '게시 일시를 확인하세요.'; return }
  const publish = action === 'publish' || (action === 'keep' && hasPublicSettings.value)
  if (publish && needsAiConfirmation.value && !aiPublicationConfirmed.value) {
    notice.value = 'AI 원래 질문과 출처의 공개 여부를 확인하세요.'
    return
  }
  const visibility = publish ? 'PUBLIC' : action === 'private' || !postId.value ? 'PRIVATE' : undefined
  flushDraft()
  busy.value = true
  try {
    const p = await api<ManagedPost>(postId.value ? `/admin/posts/${postId.value}` : '/admin/posts', postId.value ? 'PUT' : 'POST', {
      title: title.value, contentMarkdown: markdown.value, summary: summary.value, categoryId: categoryId.value,
      version: version.value,
      aiDraftId: aiAttribution.value?.draftId ?? null,
      visibility,
      // Omit an unchanged input to preserve the original database timestamp exactly.
      publishedAt: publishedInput.value && publishedInput.value !== originalPublishedInput.value ? new Date(publishedInput.value).toISOString() : undefined,
    })
    postId.value = p.id
    version.value = p.version
    savedPost.value = p
    publishedInput.value = toLocalDateTime(p.publishedAt)
    aiPublicationConfirmed.value = false
    savedSnapshot.value = snapshot()
    pendingDraft = false
    clearTimeout(timer)
    const success = {
      draft: '미발행 상태로 저장했습니다.',
      review: '검토 대기 상태로 저장했습니다.',
      private: '비공개로 저장했습니다.',
      scheduled: '예약 공개로 저장했습니다.',
      public: '독자가 볼 수 있도록 공개했습니다.',
    }[savedPublication.value]
    try { localStorage.removeItem(storageKey); draftState.value = 'empty'; notice.value = success }
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
  try { localStorage.removeItem(storageKey); pendingDraft = false; draftState.value = 'empty'; notice.value = '로컬 임시저장을 삭제했습니다.' }
  catch { notice.value = '로컬 임시저장을 삭제하지 못했습니다.' }
}
function restoreDraft() {
  if (!loaded.value || busy.value) return
  try {
    const raw = readDraft()
    if (raw === null) { notice.value = '복원할 임시저장이 없습니다.'; return }
    const p = JSON.parse(raw)
    if (!p || typeof p !== 'object' || typeof p.title !== 'string' || typeof p.markdown !== 'string'
      || (p.categoryId != null && (!Number.isSafeInteger(p.categoryId) || p.categoryId < 1))
      || (p.summary != null && typeof p.summary !== 'string')) throw new Error('Invalid draft')
    if (p.version != null && (!Number.isSafeInteger(p.version) || p.version < 0)) throw new Error('Invalid draft version')
    if (p.aiAttribution != null && !validAttribution(p.aiAttribution)) throw new Error('Invalid AI attribution')
    if (p.publishedInput != null && !validPublicationInput(p.publishedInput)) throw new Error('Invalid publication date')
    title.value = p.title ?? ''; markdown.value = p.markdown ?? ''; categoryId.value = p.categoryId ?? null
    summary.value = p.summary ?? ''
    if (p.version != null) version.value = p.version
    if (p.aiAttribution != null) aiAttribution.value = p.aiAttribution
    aiPublicationConfirmed.value = false
    if (p.publishedInput != null) publishedInput.value = p.publishedInput
    notice.value = '로컬 임시저장을 복원했습니다.'
  } catch { notice.value = '임시저장을 복원하지 못했습니다.' }
}
watch([title, markdown, categoryId, summary, version, aiAttribution, publishedInput], () => {
  if (!loaded.value) return
  clearTimeout(timer)
  pendingDraft = true
  timer = setTimeout(flushDraft, 800)
}, { flush: 'sync' })
onBeforeUnmount(() => {
  flushDraft()
  window.removeEventListener('storage', refreshDraftState)
  window.removeEventListener('pagehide', flushDraft)
  window.removeEventListener('beforeunload', beforeUnload)
  document.removeEventListener('visibilitychange', visibilityChanged)
})
</script>

<template>
  <main class="post-editor-page">
    <nav class="editor-nav" aria-label="글쓰기 메뉴">
      <RouterLink to="/">← DevFlow</RouterLink>
      <RouterLink to="/my-posts">내 글</RouterLink>
      <RouterLink to="/admin">관리자</RouterLink>
    </nav>
    <header class="editor-heading">
      <h1>{{ postId ? '게시글 검토 및 수정' : '새 글 작성' }}</h1>
      <p><strong v-if="postId && loaded">{{ savedPublicationState }}</strong>{{ dirty ? '저장하지 않은 변경 사항이 있습니다.' : postId ? '저장한 내용과 같습니다.' : '제목과 본문을 작성해 주세요.' }}</p>
    </header>
    <details class="local-draft" :class="{ 'has-draft': draftState === 'available' }">
      <summary>브라우저 임시저장 <span class="draft-status">{{ draftStatus }}</span></summary>
      <p>임시저장은 이 브라우저에 남습니다. 공용 기기에서는 사용 후 삭제하세요.</p>
      <button type="button" :disabled="!loaded || busy || draftState !== 'available'" @click="restoreDraft">로컬 임시저장 복원</button>
      <button type="button" :disabled="busy || draftState !== 'available'" @click="clearDraft">로컬 임시저장 삭제</button>
    </details>
    <p v-if="conflict" class="editor-alert" role="alert">수정 충돌을 감지했습니다. 현재 입력을 복사해 보관하고 <RouterLink :to="`/admin/posts/${postId}/edit`" target="_blank" rel="noopener">새 탭에서 최신 글 확인</RouterLink> 후 변경 사항을 반영하세요.</p>
    <p v-if="notice" class="editor-notice" role="status">{{ notice }}</p>
    <fieldset class="editor-fields" :inert="busy || !loaded" :disabled="busy || !loaded">
    <AiDraftPanel @apply="applyAiDraft" />
    <section v-if="aiAttribution" class="applied-ai" aria-label="적용한 AI 초안 정보"><h2>AI 초안 적용됨</h2><p>발행 시 공개되는 원래 질문: {{ aiAttribution.question }}</p><p>본문을 수정해도 원래 질문과 출처는 유지됩니다.</p><AiSources :sources="aiAttribution.sources" /></section>
    <MarkdownEditorLayout
      v-model="markdown"
      v-model:title="title"
      placeholder="글을 작성하세요"
      @save="save()"
      @publish="save('publish')"
    >
      <template #actions="{ uploading }">
        <div class="post-publication">
          <PublicationFields v-model:published-input="publishedInput" />
          <label v-if="needsAiConfirmation" class="publication-consent">
            <input v-model="aiPublicationConfirmed" type="checkbox">
            공개 시 AI에 입력한 원래 질문과 출처가 독자에게 표시됩니다.
          </label>
          <div class="publication-buttons">
            <button v-if="!hasPublicSettings" type="button" :disabled="uploading || conflict" @click="save()">{{ keepLabel }}</button>
            <button v-if="canMakePrivate" type="button" :disabled="uploading || conflict" @click="save('private')">비공개로 변경</button>
            <button class="publication-primary" type="button" :disabled="uploading || conflict" @click="save('publish')">{{ publishLabel }}</button>
          </div>
        </div>
      </template>
      <template #metadata>
        <div class="post-metadata">
          <CategoryInput v-model="categoryId" :categories="categories" @added="addCategory" />
          <label>요약<input v-model="summary" maxlength="500"></label>
        </div>
      </template>
    </MarkdownEditorLayout>
    </fieldset>
  </main>
</template>

<style scoped>
.post-editor-page { max-width: 1200px; margin: 0 auto; padding: 32px 40px 64px; }
.editor-nav { display: flex; flex-wrap: wrap; gap: 12px 24px; padding-bottom: 20px; border-bottom: 1px solid #ededed; }
.editor-nav a { font-size: .9rem; color: #1e4434; text-underline-offset: .2em; }
.editor-heading { margin: 28px 0 16px; }
.editor-heading h1 { font-size: clamp(1.75rem, 4vw, 2.5rem); letter-spacing: -.04em; }
.editor-heading p { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; margin: 0; color: #525252; }
.editor-heading strong { padding: 2px 10px; border-radius: 20px; color: #1e4434; background: #e9f2ec; font-size: .85rem; }
.local-draft { padding: 12px 16px; border: 1px solid #dedede; border-radius: 8px; background: #fafafa; }
.local-draft summary { cursor: pointer; font-weight: 600; }
.local-draft.has-draft { background: #eef5ef; border-color: #a9bfad; }
.draft-status { margin-inline-start: 8px; color: #525252; font-size: .85rem; font-weight: 500; }
.has-draft .draft-status { color: #1e4434; font-weight: 650; }
.local-draft button { margin: 0 8px 4px 0; }
.editor-alert, .editor-notice { padding: 10px 14px; border-radius: 6px; overflow-wrap: anywhere; }
.editor-alert { background: #fff1ef; color: #92281c; }
.editor-notice { background: #f4f6f5; }
.editor-fields { min-inline-size: 0; border: 0; padding: 0; margin: 0; }
.applied-ai { margin-bottom: 16px; padding: 16px; border: 1px solid #dedede; border-radius: 8px; overflow-wrap: anywhere; }
.post-metadata { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 2fr); gap: 20px; padding: 0 22px 20px; }
.post-metadata label { display: block; font-size: 13px; color: var(--md-muted); }
.post-metadata input { display: block; box-sizing: border-box; width: 100%; margin-top: 8px; padding: 10px 12px; border: 1px solid var(--md-border); border-radius: 5px; background: var(--md-panel); color: var(--md-text); font: inherit; }
.post-publication { display: grid; gap: 10px; width: 100%; max-width: 380px; padding: 12px; border: 1px solid var(--md-border); border-radius: 8px; background: #fafafa; }
.publication-consent { display: flex; align-items: flex-start; gap: 8px; font-size: .85rem; line-height: 1.5; }
.publication-consent input { flex: none; accent-color: var(--md-accent); }
.publication-buttons { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.publication-buttons button { min-height: 40px; }
.publication-buttons .publication-primary { background: var(--md-accent); border-color: var(--md-accent); color: white; font-weight: 650; }
.publication-buttons .publication-primary:hover:not(:disabled) { background: #2e5b47; }
@media (max-width: 760px) { .post-metadata { grid-template-columns: minmax(0, 1fr); padding-inline: 16px; } }
@media (max-width: 640px) { .post-editor-page { padding: 20px 16px 40px; } }
</style>
