<script setup lang="ts">
defineProps<{ publishedInput: string; visibility: 'PUBLIC' | 'PRIVATE' }>()
const emit = defineEmits<{ 'update:publishedInput': [value: string]; 'update:visibility': [value: 'PUBLIC' | 'PRIVATE'] }>()
</script>

<template>
  <div class="publication-fields">
    <label for="post-publication">게시 일시<input id="post-publication" type="datetime-local" :value="publishedInput" @input="emit('update:publishedInput', ($event.target as HTMLInputElement).value)" aria-describedby="publication-help" /></label>
    <label for="post-visibility">공개 범위<select id="post-visibility" :value="visibility" @change="emit('update:visibility', ($event.target as HTMLSelectElement).value as 'PUBLIC' | 'PRIVATE')"><option value="PRIVATE">비공개 · 작성자만</option><option value="PUBLIC">공개</option></select></label>
    <small id="publication-help">기기 현지 시각 기준. 비워두면 새 글은 저장 시각, 수정은 기존 일시를 유지합니다. 미래 일시의 공개 글은 해당 시각부터 노출됩니다.</small>
  </div>
</template>

<style scoped>
.publication-fields { display: flex; flex-wrap: wrap; align-items: end; gap: 10px; max-width: 440px; }
label { display: grid; gap: 6px; font-size: 12px; min-width: 0; }
input { min-width: 0; max-width: 100%; padding: 7px 10px; border: 1px solid var(--md-border); border-radius: 5px; background: var(--md-panel); color: var(--md-text); font: inherit; }
small { flex-basis: 100%; line-height: 1.6; color: var(--md-muted); }
</style>
