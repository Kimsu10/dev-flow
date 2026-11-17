<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, auth } from '../auth'
import { getManagedPost, type ManagedPost } from '../api/admin'
import type { Category } from '../types/blog'
import MarkdownEditorLayout from '../components/editor/MarkdownEditorLayout.vue'

const title = ref(''), markdown = ref(''), tagInput = ref('')
const tags = computed(() => [...new Set(tagInput.value.split(',').map(tag => tag.trim()).filter(Boolean))])
const notice = ref(''), busy = ref(false), loaded = ref(false)
const route = useRoute(), postId = ref<number | null>(null), categories = ref<Category[]>([])
const categoryId = ref<number | null>(null), summary = ref(''), status = ref('DRAFT')
const storageKey = computed(() => `devflow-editor-${auth.user?.id ?? 'admin'}-${route.params.id ?? 'new'}`)
let timer: ReturnType<typeof setTimeout> | undefined
onMounted(async () => {
  try {
    categories.value = await api<Category[]>('/categories')
    if (route.params.id) {
      const p = await getManagedPost(String(route.params.id))
      postId.value = p.id; title.value = p.title; markdown.value = p.contentMarkdown
      categoryId.value = p.categoryId; summary.value = p.summary; status.value = p.status
    }
    loaded.value = true
  } catch { notice.value = '게시글을 불러오지 못했습니다.' }
})
async function save(publish = false) {
  if (busy.value || !loaded.value) return
  if (!title.value.trim()) { notice.value = '제목을 입력하세요.'; return }
  clearTimeout(timer)
  busy.value = true
  try {
    const p = await api<ManagedPost>(postId.value ? `/admin/posts/${postId.value}` : '/admin/posts', postId.value ? 'PUT' : 'POST', {
      title: title.value, contentMarkdown: markdown.value, summary: summary.value, categoryId: categoryId.value,
    })
    postId.value = p.id
    if (publish) { await api(`/admin/posts/${p.id}/publish`, 'PATCH'); status.value = 'PUBLISHED' }
    localStorage.removeItem(storageKey.value)
    notice.value = publish ? '발행했습니다.' : '서버에 저장했습니다.'
  } catch (error) { notice.value = error instanceof Error ? error.message : '저장에 실패했습니다.' }
  finally { busy.value = false }
}
function clearDraft() { clearTimeout(timer); localStorage.removeItem(storageKey.value); notice.value = '로컬 임시저장을 삭제했습니다.' }
function restoreDraft() {
  try {
    const raw = localStorage.getItem(storageKey.value)
    if (!raw) { notice.value = '복원할 임시저장이 없습니다.'; return }
    const p = JSON.parse(raw)
    title.value = p.title ?? ''; markdown.value = p.markdown ?? ''; categoryId.value = p.categoryId ?? null
    summary.value = p.summary ?? ''; tagInput.value = p.tags ?? ''
    notice.value = '로컬 임시저장을 복원했습니다.'
  } catch { notice.value = '임시저장을 복원하지 못했습니다.' }
}
watch([title, markdown, categoryId, summary, tagInput], () => {
  if (!loaded.value) return
  clearTimeout(timer)
  timer = setTimeout(() => {
    try { localStorage.setItem(storageKey.value, JSON.stringify({ title: title.value, markdown: markdown.value, categoryId: categoryId.value, summary: summary.value, tags: tagInput.value })); notice.value = '브라우저에 임시저장했습니다.' }
    catch { notice.value = '브라우저 임시저장을 사용할 수 없습니다. 서버에 저장하세요.' }
  }, 800)
})
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <main>
    <RouterLink class="back-link" to="/">← DevFlow</RouterLink>
    <h1 class="create-post-heading">{{ postId ? '게시글 검토 및 수정' : '새 글 작성' }}</h1>
    <RouterLink to="/admin">관리자 목록</RouterLink><p>{{ status }} · 로컬 임시저장은 이 브라우저에 남습니다. 공용 기기에서는 사용 후 삭제하세요.</p><button @click="restoreDraft">로컬 임시저장 복원</button><button @click="clearDraft">로컬 임시저장 삭제</button>
    <fieldset :inert="busy || !loaded" :disabled="busy || !loaded" style="border:0;padding:0;margin:16px 0">
    <MarkdownEditorLayout
      v-model="markdown"
      v-model:title="title"
      placeholder="글을 작성하세요"
      @save="save(false)"
      @publish="save(true)"
    >
      <template #metadata>
        <div class="post-metadata">
          <label for="post-category">카테고리<select id="post-category" v-model="categoryId"><option :value="null">미분류</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></label>
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
.post-metadata { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 2fr); gap: 20px; padding: 0 22px 20px; }
.post-metadata label { display: block; font-size: 13px; color: var(--md-muted); }
.post-metadata input { display: block; box-sizing: border-box; width: 100%; margin-top: 8px; padding: 10px 12px; border: 1px solid var(--md-border); border-radius: 5px; background: var(--md-panel); color: var(--md-text); font: inherit; }
.post-metadata p { margin: 6px 0 0; font-size: 12px; color: var(--md-muted); }
.post-tags { display: flex; flex-wrap: wrap; gap: 6px; list-style: none; margin: 8px 0 0; padding: 0; }
.post-tags li { padding: 3px 8px; border-radius: 5px; background: #292d51; color: #c7d2fe; font-size: 12px; }
@media (max-width: 760px) { .post-metadata { grid-template-columns: minmax(0, 1fr); padding-inline: 16px; } }
</style>
