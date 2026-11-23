<script setup lang="ts">
defineProps<{ publishedInput: string }>()
const emit = defineEmits<{ 'update:publishedInput': [value: string] }>()
</script>

<template>
  <div class="publication-fields">
    <label for="post-publication">게시 일시<input id="post-publication" type="datetime-local" :value="publishedInput" @input="emit('update:publishedInput', ($event.target as HTMLInputElement).value)" aria-describedby="publication-help" /></label>
    <small id="publication-help">기기 현지 시각 기준입니다. 비워두면 기존 일시를 적용하며, 기존 일시가 없으면 저장 시각을 사용합니다. 미래 일시를 지정하고 공개하면 예약됩니다.</small>
  </div>
</template>

<style scoped>
.publication-fields { display: grid; gap: 6px; min-width: 0; }
label { display: grid; gap: 6px; font-size: 13px; font-weight: 600; }
input { box-sizing: border-box; width: 100%; min-width: 0; padding: 8px 10px; border: 1px solid var(--md-border); border-radius: 6px; background: var(--md-panel); color: var(--md-text); font: inherit; }
small { font-size: 12px; line-height: 1.6; color: var(--md-muted); }
</style>
