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
const theme = ref<'dark' | 'light'>('light')
try { if (localStorage.getItem('ksnote-ai-terminal-theme') === 'dark') theme.value = 'dark' } catch { /* Theme switching still works without storage. */ }
function setTheme(value: 'dark' | 'light') {
  theme.value = value
  try { localStorage.setItem('ksnote-ai-terminal-theme', value) } catch { /* Keep the selection for this session. */ }
}
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
  <form class="home-ai-publisher" :data-theme="theme" aria-label="AI 글 발행" :aria-busy="busy" @submit.prevent="publish">
    <div class="terminal-titlebar">
      <div class="terminal-theme-controls" role="group" aria-label="터미널 색상 모드">
        <button class="terminal-theme-button" type="button" :aria-label="theme === 'dark' ? '라이트 모드' : '다크 모드'" :title="theme === 'dark' ? '라이트 모드로 전환' : '다크 모드로 전환'" @click="setTheme(theme === 'dark' ? 'light' : 'dark')">
          <svg v-if="theme === 'dark'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" /></svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" aria-hidden="true"><path d="M20.5 14.4A9 9 0 0 1 9.6 3.5a9 9 0 1 0 10.9 10.9Z" /></svg>
        </button>
      </div>
      <span class="terminal-window-title">ksnote — AI writer</span>
      <span class="terminal-badge">{{ busy ? 'WORKING' : 'READY' }}</span>
    </div>
    <div class="terminal-body">
    <label class="terminal-question-label" for="home-ai-question">어떤 주제로 글을 작성할까요?</label>
    <p id="home-ai-notice">입력 내용은 AI 서비스에 전달되며, 발행하면 원래 질문·AI 작성 안내·출처와 함께 즉시 공개됩니다.<br><strong>개인정보나 비밀정보를 입력하지 마세요.</strong> AI 작성 내용에는 오류가 있을 수 있으며 발행 후 수정할 수 있습니다.</p>
    <div class="terminal-input">
      <span class="terminal-prompt" aria-hidden="true">❯</span>
      <textarea id="home-ai-question" v-model="question" rows="4" required maxlength="2000" :disabled="busy" aria-describedby="home-ai-notice" placeholder="오늘 배운 기술과 경험을 입력해 주세요.&#10;어떤 내용을 담을지 여러 문장으로 알려주셔도 좋아요." />
    </div>
    <div class="terminal-input-meta"><span>{{ question.length.toLocaleString() }} / 2,000</span></div>
    <div class="home-ai-actions">
      <label for="home-ai-category">카테고리</label>
      <select id="home-ai-category" v-model="categoryId" required :disabled="busy"><option :value="null" disabled>카테고리 선택</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select>
      <button v-if="!categories.length" type="button" :disabled="busy" @click="loadCategories">카테고리 다시 불러오기</button>
      <button class="ai-publish-button" type="submit" :disabled="busy || !question.trim() || !categoryId"><span aria-hidden="true">↵ </span>{{ busy ? '자료 조사 및 발행 중…' : 'AI 글 발행' }}</button>
    </div>
    <p v-if="busy" class="terminal-status" role="status">자료를 조사하고 출처가 포함된 글을 작성하고 있습니다.</p>
    <p v-if="error" class="terminal-error" role="alert">{{ error }}</p>
    </div>
  </form>
</template>

<style scoped>
.home-ai-publisher { --terminal-green: #a3e4b8; --terminal-bg: #141b20; --terminal-panel: #202930; --terminal-input-bg: #10161a; --terminal-text: #e5ebef; --terminal-border: #354048; --terminal-control-border: #4b5963; --terminal-muted: #aebdc7; --terminal-placeholder: #91a0ab; --terminal-warning: #f2d592; --terminal-focus: #a3e4b82b; --terminal-button-text: #102418; --terminal-hover: #c1f2d0; --terminal-disabled-bg: #28333a; --terminal-error-border: #f07874; --terminal-error-bg: #302227; --terminal-error-text: #ffbeb9; --terminal-scheme: dark; margin-top: 28px; border: 1px solid var(--terminal-border); border-radius: 12px; background: var(--terminal-bg); color: var(--terminal-text); overflow: hidden; box-shadow: 0 12px 32px #13251c14; font-family: Consolas, 'SFMono-Regular', 'Courier New', monospace; }
.terminal-titlebar { display: flex; align-items: center; gap: 16px; padding: 14px 20px; border-bottom: 1px solid var(--terminal-border); background: var(--terminal-panel); font-size: 12px; }
.terminal-window-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--terminal-muted); }
.terminal-badge { margin-left: auto; color: var(--terminal-green); font-size: 10px; letter-spacing: .08em; }
.terminal-body { padding: 24px; }
.terminal-question-label { font-weight: 700; font-size: 20px; line-height: 1.5; }
#home-ai-notice { color: var(--terminal-muted); font-size: 12px; line-height: 1.8; margin: 12px 0 20px; overflow-wrap: anywhere; }
#home-ai-notice strong { color: var(--terminal-warning); font-weight: 700; }
.terminal-input { display: flex; align-items: flex-start; gap: 12px; padding: 16px; border: 1px solid var(--terminal-border); border-radius: 6px; background: var(--terminal-input-bg); }
.terminal-input:focus-within { border-color: var(--terminal-green); outline: 2px solid var(--terminal-focus); outline-offset: 2px; }
.terminal-prompt { color: var(--terminal-green); line-height: 1.8; }
textarea { display: block; box-sizing: border-box; width: 100%; min-width: 0; min-height: 132px; margin: 0; padding: 0; border: 0; border-radius: 0; background: transparent; color: var(--terminal-text); caret-color: var(--terminal-green); font: inherit; font-size: 14px; line-height: 1.8; resize: vertical; outline: none; }
textarea::placeholder { color: var(--terminal-placeholder); opacity: 1; }
.terminal-input-meta { display: flex; justify-content: flex-end; gap: 12px; padding: 10px 0 18px; font-size: 11px; color: var(--terminal-muted); }
.home-ai-actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; padding-top: 18px; border-top: 1px solid var(--terminal-border); font-size: 13px; }
select { min-width: 0; max-width: 100%; min-height: 44px; padding: 10px; border: 1px solid var(--terminal-control-border); border-radius: 6px; background: var(--terminal-panel); color: var(--terminal-text); font: inherit; color-scheme: var(--terminal-scheme); }
.home-ai-publisher button { font: inherit; font-size: 13px; min-height: 44px; background: var(--terminal-panel); border-color: var(--terminal-control-border); color: var(--terminal-text); }
.home-ai-publisher .ai-publish-button { margin-left: auto; padding-inline: 18px; background: var(--terminal-green); color: var(--terminal-button-text); border-color: var(--terminal-green); font-weight: 700; }
.home-ai-publisher .ai-publish-button:hover:not(:disabled) { background: var(--terminal-hover); border-color: var(--terminal-hover); }
.home-ai-publisher button:disabled { background: var(--terminal-disabled-bg); border-color: var(--terminal-control-border); color: var(--terminal-muted); cursor: not-allowed; }
.home-ai-publisher button:focus-visible, select:focus-visible { outline: 2px solid var(--terminal-green); outline-offset: 3px; }
.terminal-status { margin: 18px 0 0; color: var(--terminal-muted); font-size: 11px; line-height: 1.7; }
.terminal-status span { color: var(--terminal-green); margin-right: 6px; }
.terminal-error { padding: 12px; margin: 14px 0 0; border-left: 2px solid var(--terminal-error-border); background: var(--terminal-error-bg); color: var(--terminal-error-text); font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.home-ai-publisher[data-theme="light"] { --terminal-green: #215b3c; --terminal-bg: #ffffff; --terminal-panel: #f1f5f3; --terminal-input-bg: #f8faf9; --terminal-text: #1b2b23; --terminal-border: #cbd7d0; --terminal-control-border: #a7bab0; --terminal-muted: #4e6257; --terminal-placeholder: #60756a; --terminal-warning: #784b0b; --terminal-focus: #215b3c29; --terminal-button-text: #ffffff; --terminal-hover: #17452d; --terminal-disabled-bg: #e1e9e4; --terminal-error-border: #b83232; --terminal-error-bg: #fff0f0; --terminal-error-text: #8c2222; --terminal-scheme: light; }
.terminal-theme-controls { display: flex; gap: 4px; flex: none; }
.home-ai-publisher .terminal-theme-button { display: inline-flex; align-items: center; justify-content: center; width: 40px; min-height: 40px; padding: 9px; border: 1px solid transparent; background: transparent; color: var(--terminal-muted); }
.terminal-theme-button svg { width: 20px; height: 20px; flex: none; }
.home-ai-publisher .terminal-theme-button:hover { background: var(--terminal-input-bg); color: var(--terminal-green); }
@media(max-width: 600px) { .terminal-titlebar { padding: 12px 16px; gap: 12px; } .terminal-body { padding: 18px 16px; } .terminal-input { padding: 12px; gap: 8px; } .terminal-input-meta { font-size: 10px; } .home-ai-publisher .ai-publish-button { width: 100%; margin-left: 0; } }
</style>
