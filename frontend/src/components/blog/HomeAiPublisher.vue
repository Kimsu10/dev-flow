<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../auth'
import type { AiDraft } from '../../types/ai'
import { validAttribution } from '../../types/ai'
import type { Category } from '../../types/blog'
import type { ManagedPost } from '../../api/admin'
const router = useRouter()
const question = ref(''), busy = ref(false), error = ref('')
const categories = ref<Category[]>([]), categoryId = ref<number | null>(null)
const draft = ref<AiDraft | null>(null)
const controller = new AbortController()
onBeforeUnmount(() => controller.abort())
async function loadCategories() {
  error.value = ''
  try { categories.value = await api<Category[]>('/categories', 'GET', undefined, controller.signal) }
  catch { if (!controller.signal.aborted) error.value = '카테고리를 불러오지 못했습니다. 다시 시도해 주세요.' }
}
onMounted(loadCategories)
async function publish() {
  if (busy.value || !question.value.trim() || !categoryId.value) return
  busy.value = true; error.value = ''
  const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(120000)])
  try {
    if (!draft.value || draft.value.question !== question.value.trim()) {
      const result = await api<AiDraft>('/admin/ai-drafts', 'POST', { question: question.value.trim(), publicQuestionAccepted: true }, signal)
      if (!validAttribution({ draftId: result.id, question: result.question, sources: result.sources }) || !result.contentMarkdown?.trim()) throw new Error('출처가 확인된 AI 글을 받지 못했습니다.')
      draft.value = result
    }
    if (controller.signal.aborted) return
    const heading = draft.value.contentMarkdown.match(/^#\s+(.+)$/m)?.[1]
    const title = ((heading || '').replace(/[*`]/g, '').trim() || question.value.trim().split('\n')[0] || 'AI 기술 기록').slice(0, 200)
    const post = await api<ManagedPost>('/admin/posts', 'POST', {
      title, contentMarkdown: draft.value.contentMarkdown, categoryId: categoryId.value,
      aiDraftId: draft.value.id, visibility: 'PUBLIC',
    }, signal)
    if (!controller.signal.aborted) await router.push({ name: 'post-detail', params: { id: post.id } })
  } catch (cause) {
    if (!controller.signal.aborted) error.value = signal.aborted ? '작성 시간이 초과되었습니다. 다시 시도해 주세요.' : cause instanceof Error ? cause.message : 'AI 글을 발행하지 못했습니다.'
  } finally { busy.value = false }
}
</script>

<template>
  <form class="home-ai-publisher" aria-label="AI 글 발행" @submit.prevent="publish">
    <label for="home-ai-question">어떤 주제로 글을 작성할까요?</label>
    <p id="home-ai-notice">입력 내용은 AI 서비스에 전달되며, 발행하면 원래 질문·AI 작성 안내·출처와 함께 즉시 공개됩니다.<br><strong>개인정보나 비밀정보를 입력하지 마세요.</strong> AI 작성 내용에는 오류가 있을 수 있으며 발행 후 수정할 수 있습니다.</p>
    <textarea id="home-ai-question" v-model="question" rows="4" required maxlength="2000" :disabled="busy" aria-describedby="home-ai-notice" placeholder="다루고 싶은 기술, 경험, 궁금한 내용을 여러 문장으로 입력해 주세요." />
    <div class="home-ai-actions">
      <label for="home-ai-category">카테고리</label>
      <select id="home-ai-category" v-model="categoryId" required :disabled="busy"><option :value="null" disabled>카테고리 선택</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select>
      <button v-if="!categories.length" type="button" :disabled="busy" @click="loadCategories">카테고리 다시 불러오기</button>
      <button class="ai-publish-button" type="submit" :disabled="busy || !question.trim() || !categoryId">{{ busy ? '자료 조사 및 발행 중…' : 'AI 글 발행' }}</button>
    </div>
    <p v-if="busy" role="status">자료를 조사하고 출처가 포함된 글을 작성하고 있습니다.</p>
    <p v-if="error" role="alert">{{ error }}</p>
  </form>
</template>

<style scoped>
.home-ai-publisher { margin-top: 28px; padding: 22px; border: 1px solid #d4d4d4; border-radius: 10px; background: #fafcfb; }
.home-ai-publisher > label { font-weight: 600; font-size: 20px; }
textarea { display: block; width: 100%; box-sizing: border-box; margin: 12px 0 16px; padding: 14px; border: 1px solid #cbd4ce; border-radius: 6px; background: white; font: inherit; line-height: 1.7; resize: vertical; }
#home-ai-notice { color: #626b65; font-size: 12px; line-height: 1.7; }
#home-ai-notice strong { color: #333; font-weight: 700; }
.home-ai-actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; font-size: 13px; }
select { min-width: 0; max-width: 100%; padding: 10px; border: 1px solid #cbd4ce; border-radius: 6px; font: inherit; }
.ai-publish-button { margin-left: auto; min-height: 44px; background: #1e4434; color: white; border-color: #1e4434; }
.ai-publish-button:hover:not(:disabled) { background: #2e5b47; }
@media(max-width: 600px) { .home-ai-publisher { padding: 16px; } .ai-publish-button { width: 100%; margin-left: 0; } }
</style>
