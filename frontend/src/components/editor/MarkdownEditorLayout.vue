<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import MarkdownEditor from './MarkdownEditor.vue'
import MarkdownToolbar from './MarkdownToolbar.vue'
import MarkdownPreview from './MarkdownPreview.vue'
import type { EditorMode, UploadImage } from './types'
import './editor.css'
withDefaults(defineProps<{ modelValue: string; title?: string; uploadImage?: UploadImage; maxImageBytes?: number }>(), { title: '' })
const emit = defineEmits<{ 'update:modelValue': [value: string]; 'update:title': [value: string]; save: []; publish: [] }>()
const mode = ref<EditorMode>('split'), fullscreen = ref(false), uploading = ref(false)
const editor = ref<InstanceType<typeof MarkdownEditor>>(), preview = ref<InstanceType<typeof MarkdownPreview>>()
let syncing = false, frame = 0
function sync(target: 'editor' | 'preview', ratio: number) {
  if (syncing || mode.value !== 'split') return
  syncing = true
  if (target === 'editor') editor.value?.scrollToRatio(ratio)
  else preview.value?.scrollToRatio(ratio)
  frame = requestAnimationFrame(() => { syncing = false })
}
onBeforeUnmount(() => cancelAnimationFrame(frame))
</script>
<template>
  <section class="md-workspace" :class="{ 'md-fullscreen': fullscreen }" aria-label="게시글 작성 에디터" @keydown.esc="fullscreen = false">
    <header class="md-header">
      <label class="md-title">게시글 제목<input :value="title" placeholder="어떤 이야기를 기록할까요?" @input="emit('update:title', ($event.target as HTMLInputElement).value)"></label>
      <div class="md-actions"><slot name="actions" :uploading="uploading"><button type="button" :disabled="uploading" @click="emit('save')">임시 저장</button><button type="button" :disabled="uploading" @click="emit('publish')">발행</button></slot></div>
    </header>
    <div class="md-view-controls"><div role="group" aria-label="편집 모드"><button v-for="item in ([['editor', 'Editor Only'], ['split', 'Split View'], ['preview', 'Preview Only']] as const)" :key="item[0]" type="button" :aria-pressed="mode === item[0]" @click="mode = item[0]">{{ item[1] }}</button></div><button type="button" :aria-pressed="fullscreen" @click="fullscreen = !fullscreen">{{ fullscreen ? '전체 화면 종료' : '전체 화면' }}</button></div>
    <MarkdownToolbar :disabled="mode === 'preview'" @format="(kind, heading) => editor?.format(kind, heading)" @image="editor?.chooseImage()" />
    <div class="md-panes" :data-mode="mode">
      <div v-show="mode !== 'preview'" class="md-pane"><div class="md-pane-label">MARKDOWN</div><MarkdownEditor ref="editor" :model-value="modelValue" :upload-image="uploadImage" :max-image-bytes="maxImageBytes" @update:model-value="emit('update:modelValue', $event)" @uploading="uploading = $event" @scroll="sync('preview', $event)" /></div>
      <div v-show="mode !== 'editor'" class="md-pane"><div class="md-pane-label">PREVIEW</div><MarkdownPreview ref="preview" :source="modelValue" @scroll="sync('editor', $event)" /></div>
    </div>
    <footer class="md-footer"><span>{{ modelValue.length.toLocaleString() }}자 · Markdown</span><span>Ctrl/⌘ B · I · Z · Shift Z | Tab 들여쓰기 · Esc 전체 화면 종료</span></footer>
  </section>
</template>
