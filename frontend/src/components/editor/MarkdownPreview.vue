<script setup lang="ts">
import { computed, ref } from 'vue'
import { renderMarkdown } from './markdown'
const props = defineProps<{ source: string }>()
const html = computed(() => renderMarkdown(props.source))
const root = ref<HTMLElement>()
const emit = defineEmits<{ scroll: [ratio: number] }>()
function onScroll() { const el = root.value!; emit('scroll', el.scrollTop / Math.max(1, el.scrollHeight - el.clientHeight)) }
function scrollToRatio(ratio: number) { const el = root.value; if (el) el.scrollTop = ratio * (el.scrollHeight - el.clientHeight) }
defineExpose({ scrollToRatio })
</script>
<template><div ref="root" class="md-preview" role="region" aria-label="Markdown 미리보기" tabindex="0" @scroll="onScroll"><article v-html="html" /></div></template>
