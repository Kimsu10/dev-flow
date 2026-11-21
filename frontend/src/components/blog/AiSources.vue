<script setup lang="ts">
import type { AiSource } from '../../types/ai'
import { safeSourceUrl } from '../../types/ai'
defineProps<{ sources: AiSource[] }>()
</script>

<template>
  <section v-if="sources.length" class="ai-sources" aria-label="AI 조사 출처">
    <h2>출처</h2>
    <ul><li v-for="source in sources" :key="source.url"><a v-if="safeSourceUrl(source.url)" :href="source.url" target="_blank" rel="noopener noreferrer">{{ source.title || source.url }}</a></li></ul>
  </section>
</template>

<style scoped>
.ai-sources { margin: 2rem 0; padding-top: 1.2rem; border-top: 1px solid #dedede; }
.ai-sources h2 { font-size: 1.2rem; }
.ai-sources li { margin: .7rem 0; overflow-wrap: anywhere; }
.ai-sources a { color: #22684b; text-decoration: underline; text-underline-offset: 3px; }
</style>
