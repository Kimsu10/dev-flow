<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { basicSetup } from 'codemirror'
import { Compartment, EditorState, StateEffect, Transaction } from '@codemirror/state'
import { EditorView, keymap } from '@codemirror/view'
import { indentWithTab } from '@codemirror/commands'
import { markdown } from '@codemirror/lang-markdown'
import { languages } from '@codemirror/language-data'
import { formatSelection } from './format'
import { imageMarkdown, validateImage } from './image'
import type { Format, UploadImage } from './types'
const props = withDefaults(defineProps<{ modelValue: string; uploadImage?: UploadImage; maxImageBytes?: number }>(), { maxImageBytes: 5 * 1024 * 1024 })
const emit = defineEmits<{ 'update:modelValue': [value: string]; scroll: [ratio: number]; uploading: [busy: boolean] }>()
const host = ref<HTMLElement>(), picker = ref<HTMLInputElement>()
const status = ref(''), busy = ref(false)
let view: EditorView | undefined
let disposed = false
function format(kind: Format, heading?: number) {
  if (!view) return
  view.dispatch({ ...formatSelection(view.state, kind, heading), scrollIntoView: true, annotations: Transaction.userEvent.of('input') })
  view.focus()
}
function chooseImage() {
  if (props.uploadImage) picker.value?.click()
  else { format('image'); status.value = '파일 업로드에는 부모의 uploadImage 함수가 필요합니다. 이미지 URL을 입력해 주세요.' }
}
async function upload(files: File[], position?: number) {
  if (busy.value || !view || !files.length) return
  if (!props.uploadImage) { status.value = '이미지 업로드 함수가 연결되지 않았습니다.'; return }
  busy.value = true; emit('uploading', true)
  // Map the captured insertion point through edits made while the request is in flight.
  let anchor = position ?? view.state.selection.main.head
  const track = EditorView.updateListener.of(update => { anchor = update.changes.mapPos(anchor, 1) })
  const compartment = new Compartment()
  view.dispatch({ effects: StateEffect.appendConfig.of(compartment.of(track)) })
  try {
    for (const file of files) {
      await validateImage(file, props.maxImageBytes)
      status.value = `${file.name}: 업로드 중…`
      const result = await props.uploadImage(file, percent => { if (!disposed) status.value = `${file.name}: ${Math.round(Math.max(0, Math.min(100, percent)))}%` })
      if (disposed || !view) return
      const insert = '\n' + imageMarkdown(result.url, result.alt || file.name) + '\n'
      view.dispatch({ changes: { from: anchor, insert }, annotations: Transaction.userEvent.of('input') })
    }
    status.value = '이미지 업로드 완료'
  } catch (error) { if (!disposed) status.value = error instanceof Error ? error.message : '이미지 업로드에 실패했습니다.' }
  finally { if (!disposed) { view?.dispatch({ effects: compartment.reconfigure([]) }); busy.value = false; emit('uploading', false) } }
}
onMounted(() => {
  view = new EditorView({ parent: host.value, state: EditorState.create({ doc: props.modelValue, extensions: [
    basicSetup, markdown({ codeLanguages: languages }), EditorView.lineWrapping, EditorView.theme({}, { dark: true }),
    EditorView.contentAttributes.of({ 'aria-label': 'Markdown 원문' }),
    keymap.of([{ key: 'Mod-b', run: () => { format('bold'); return true } }, { key: 'Mod-i', run: () => { format('italic'); return true } }, indentWithTab]),
    EditorView.updateListener.of(update => { if (update.docChanged) emit('update:modelValue', update.state.doc.toString()) }),
    EditorView.domEventHandlers({
      scroll(_event, editor) { const el = editor.scrollDOM; emit('scroll', el.scrollTop / Math.max(1, el.scrollHeight - el.clientHeight)) },
      paste(event) {
        const files = Array.from(event.clipboardData?.files || []).filter(file => file.type.startsWith('image/'))
        if (!files.length) return false
        event.preventDefault(); void upload(files); return true
      },
      dragover(event) { if (event.dataTransfer?.types.includes('Files')) event.preventDefault() },
      drop(event, editor) {
        const files = Array.from(event.dataTransfer?.files || [])
        if (!files.length) return false
        event.preventDefault(); void upload(files, editor.posAtCoords({ x: event.clientX, y: event.clientY }) ?? undefined); return true
      },
    }),
  ] }) })
})
watch(() => props.modelValue, value => { if (view && value !== view.state.doc.toString()) view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } }) })
onBeforeUnmount(() => { disposed = true; view?.destroy(); view = undefined })
function scrollToRatio(ratio: number) { if (view) view.scrollDOM.scrollTop = ratio * (view.scrollDOM.scrollHeight - view.scrollDOM.clientHeight) }
defineExpose({ format, chooseImage, scrollToRatio })
</script>
<template>
  <div class="md-source"><div ref="host" class="md-codemirror" /><input ref="picker" type="file" hidden multiple accept="image/png,image/jpeg,image/gif,image/webp" @change="upload(Array.from(($event.target as HTMLInputElement).files || [])); ($event.target as HTMLInputElement).value = ''"><p v-if="status" class="md-upload-status" role="status">{{ status }}</p></div>
</template>
