<script setup lang="ts">
import type { Format } from './types'
defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ format: [format: Format, heading?: number]; image: [] }>()
function headingChanged(event: Event) {
  const select = event.target as HTMLSelectElement
  emit('format', 'heading', Number(select.value))
  select.value = ''
}
const buttons: { format: Format; label: string; paths: string[] }[] = [
  { format: 'bold', label: 'Bold', paths: ['M6 4h7a4 4 0 0 1 0 8H6z', 'M6 12h8a4 4 0 0 1 0 8H6z'] },
  { format: 'italic', label: 'Italic', paths: ['M10 4h10M4 20h10M15 4 9 20'] },
  { format: 'strike', label: '취소선', paths: ['M17 6c-1-2-9-3-10 1-.5 2 1 3 3 4', 'M14 13c6 3 2 9-5 7-1-.3-2-1-3-2', 'M3 12h18'] },
  { format: 'quote', label: '인용', paths: ['M4 5h6v7H4zM14 5h6v7h-6zM10 12c0 5-2 7-5 7M20 12c0 5-2 7-5 7'] },
  { format: 'code', label: '인라인 코드', paths: ['m8 6-6 6 6 6m8-12 6 6-6 6m-3-15-2 18'] },
  { format: 'codeblock', label: '코드 블록', paths: ['M3 3h18v18H3zM3 8h18M7 5.5h.01M10 5.5h.01m-2 6-3 3 3 3m8-6 3 3-3 3'] },
  { format: 'link', label: '링크', paths: ['m10 13 4-4M8 16l-1 1a4 4 0 0 1-6-6l4-4a4 4 0 0 1 6 0m2 1 1-1a4 4 0 0 1 6 6l-4 4a4 4 0 0 1-6 0'] },
  { format: 'ordered', label: '번호 목록', paths: ['M10 6h11M10 12h11M10 18h11M3 3h1v6M2 9h4M2 15c0-3 5-3 4 0l-4 5h4'] },
  { format: 'unordered', label: '목록', paths: ['M9 6h12M9 12h12M9 18h12M3 6h1M3 12h1M3 18h1'] },
  { format: 'table', label: '표', paths: ['M3 4h18v16H3zM3 10h18M3 15h18M9 4v16M15 4v16'] },
]
</script>
<template>
  <div class="md-toolbar" role="group" aria-label="Markdown 서식">
    <select aria-label="제목 수준" title="제목 수준" :disabled="disabled" @change="headingChanged"><option value="" disabled selected>Heading</option><option v-for="n in 6" :key="n" :value="n">H{{ n }}</option></select>
    <button v-for="item in buttons" :key="item.format" class="md-icon-button" type="button" :aria-label="item.label" :title="item.label" :disabled="disabled" @mousedown.prevent @click="emit('format', item.format)">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path v-for="(path, index) in item.paths" :key="index" :d="path" /></svg>
    </button>
    <button class="md-icon-button" type="button" aria-label="이미지" title="이미지" :disabled="disabled" @mousedown.prevent @click="emit('image')">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="8.5" cy="8.5" r="1.5" /><path d="m21 15-5-5L5 21" /></svg>
    </button>
  </div>
</template>
