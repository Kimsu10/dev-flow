<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import { api } from '../../auth'
import type { AiDraft } from '../../types/ai'
import { validAttribution } from '../../types/ai'
import MarkdownPreview from './MarkdownPreview.vue'
import AiSources from '../blog/AiSources.vue'
const emit = defineEmits<{ apply: [draft: AiDraft] }>()
const open = ref(false), question = ref(''), accepted = ref(false), busy = ref(false), error = ref('')
const draft = ref<AiDraft | null>(null)
let controller: AbortController | undefined
onBeforeUnmount(() => controller?.abort())
async function generate() {
  if (busy.value || !accepted.value || !question.value.trim()) return
  controller = new AbortController()
  const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(120000)])
  busy.value = true; error.value = ''; draft.value = null
  try {
    const result = await api<AiDraft>('/admin/ai-drafts', 'POST', { question: question.value.trim(), publicQuestionAccepted: true }, signal)
    if (controller.signal.aborted) return
    if (!validAttribution({ draftId: result.id, question: result.question, sources: result.sources }) || typeof result.contentMarkdown !== 'string' || !result.contentMarkdown.trim()) throw new Error('출처가 확인된 AI 초안을 받지 못했습니다.')
    draft.value = result
  } catch (cause) { if (!controller.signal.aborted) error.value = signal.aborted ? '조사 시간이 초과되었습니다. 다시 시도하세요.' : cause instanceof Error ? cause.message : 'AI 조사에 실패했습니다.' }
  finally { busy.value = false }
}
</script>

<template>
  <section class="ai-draft-panel" aria-label="AI 글 작성">
    <button type="button" :aria-expanded="open" @click="open = !open">{{ open ? 'AI 초안 패널 닫기' : 'AI 글 작성' }}</button>
    <div v-show="open">
      <p id="ai-disclosure">질문은 AI 서비스에 전달됩니다. 초안을 편집기에 적용하고 글을 발행하면 원래 질문과 AI 작성 안내, 출처 링크가 공개됩니다. 질문에 개인정보나 비밀정보를 입력하지 마세요.</p>
      <form @submit.prevent="generate">
        <label for="ai-question">조사할 질문<textarea id="ai-question" v-model="question" required maxlength="2000" rows="4" :disabled="busy" aria-describedby="ai-disclosure" /></label>
        <label class="ai-consent"><input v-model="accepted" type="checkbox" :disabled="busy" />발행 시 질문이 공개됨을 확인했습니다.</label>
        <button type="submit" :disabled="busy || !accepted || !question.trim()">{{ busy ? '자료 조사 및 초안 작성 중…' : '자료 조사하고 초안 만들기' }}</button>
      </form>
      <p v-if="busy" role="status">실제 자료를 검색하고 있습니다. 완료되기 전까지 본문은 변경되지 않습니다.</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <div v-if="draft" class="ai-draft-result">
        <h2>AI 초안 미리보기</h2><p>원래 질문: {{ draft.question }}</p>
        <div class="md-workspace ai-preview"><MarkdownPreview :source="draft.contentMarkdown" /></div>
        <AiSources :sources="draft.sources" />
        <p>AI 초안에는 오류가 있을 수 있습니다. 출처와 내용을 검토하세요. 적용하면 현재 본문이 이 초안으로 교체됩니다.</p>
        <button type="button" @click="emit('apply', draft)">편집기에 적용</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ai-draft-panel { margin: 16px 0; }
.ai-draft-panel > div { margin-top: 10px; padding: 16px; border: 1px solid #dedede; border-radius: 8px; background: #fafafa; }
.ai-draft-panel form { display: grid; gap: 12px; margin: 16px 0; }
textarea { display: block; box-sizing: border-box; width: 100%; margin-top: 8px; padding: 10px; border: 1px solid #d4d4d4; border-radius: 6px; background: #fff; color: inherit; font: inherit; resize: vertical; }
.ai-consent { display: flex; align-items: center; gap: 8px; }
.ai-preview { height: 420px; overflow: auto; }
.ai-preview :deep(.md-preview) { height: 100%; }
.ai-draft-panel p { overflow-wrap: anywhere; }
</style>
