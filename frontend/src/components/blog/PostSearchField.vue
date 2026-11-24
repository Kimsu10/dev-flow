<script setup lang="ts">
import { ref } from 'vue'
withDefaults(defineProps<{ id: string; modelValue: string; hideLabel?: boolean }>(), { hideLabel: false })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const input = ref<HTMLInputElement | null>(null)
defineExpose({ focus: () => input.value?.focus() })
</script>

<template>
  <div class="post-search-field">
    <label :for="id" :class="{ 'visually-hidden': hideLabel }">게시글 검색</label>
    <div class="search-input-wrap">
      <input :id="id" ref="input" :value="modelValue" type="search" name="q" maxlength="100" placeholder="제목, 요약, 본문에서 검색" @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)" />
      <button type="submit" aria-label="글 검색" title="글 검색"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true" focusable="false"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m15.5 15.5 5 5" /></svg></button>
    </div>
  </div>
</template>

<style scoped>
.post-search-field { min-width: 0; width: 100%; }
label:not(.visually-hidden) { display: block; margin-bottom: .6rem; font-weight: 600; }
.search-input-wrap { position: relative; }
input { display: block; box-sizing: border-box; width: 100%; min-width: 0; min-height: 48px; padding: 10px 56px 10px 12px; border: 1px solid #bcbcbc; border-radius: 6px; background: #fff; color: #171717; font: inherit; }
button { position: absolute; inset-inline-end: 2px; top: 50%; transform: translateY(-50%); display: inline-flex; align-items: center; justify-content: center; width: 44px; height: 44px; padding: 0; border: 0; border-radius: 4px; background: transparent; color: #1e4434; }
button:hover { background: #edf3ef; }
svg { width: 21px; height: 21px; }
</style>
