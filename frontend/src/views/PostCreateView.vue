<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import MarkdownEditorLayout from '../components/editor/MarkdownEditorLayout.vue'

const title = ref(''), markdown = ref(''), category = ref(''), tagInput = ref('')
const tags = computed(() => [...new Set(tagInput.value.split(',').map(tag => tag.trim()).filter(Boolean))])
const notice = ref('')
</script>

<template>
  <main>
    <RouterLink class="back-link" to="/">← DevFlow</RouterLink>
    <h1 class="create-post-heading">새 글 작성</h1>
    <MarkdownEditorLayout
      v-model="markdown"
      v-model:title="title"
      placeholder="글을 작성하세요"
      @save="notice = '임시 저장 API는 아직 연결되지 않았습니다.'"
      @publish="notice = '발행 API는 아직 연결되지 않았습니다.'"
    >
      <template #metadata>
        <div class="post-metadata">
          <label for="post-category">카테고리<input id="post-category" v-model="category" name="category" placeholder="카테고리를 입력하세요"></label>
          <div>
            <label for="post-tags">태그<input id="post-tags" v-model="tagInput" name="tags" placeholder="Vue, TypeScript" aria-describedby="post-tags-help"></label>
            <p id="post-tags-help">쉼표로 태그를 구분해 주세요.</p>
            <ul v-if="tags.length" class="post-tags" aria-label="입력한 태그"><li v-for="tag in tags" :key="tag">#{{ tag }}</li></ul>
          </div>
        </div>
      </template>
    </MarkdownEditorLayout>
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
