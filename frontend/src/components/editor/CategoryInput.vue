<script setup lang="ts">
import { ref, nextTick, onBeforeUnmount } from 'vue'
import { api } from '../../auth'
import type { Category } from '../../types/blog'
defineProps<{ modelValue: number | null; categories: Category[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: number | null]; added: [category: Category] }>()
const adding = ref(false), name = ref(''), busy = ref(false), error = ref('')
const nameInput = ref<HTMLInputElement>(), categorySelect = ref<HTMLSelectElement>()
async function open() { adding.value = true; error.value = ''; await nextTick(); nameInput.value?.focus() }
function cancel() { if (!busy.value) { adding.value = false; name.value = ''; error.value = ''; categorySelect.value?.focus() } }
const controller = new AbortController()
onBeforeUnmount(() => controller.abort())
async function create() {
  if (busy.value || !name.value.trim()) return
  busy.value = true; error.value = ''
  try {
    const category = await api<Category>('/admin/categories', 'POST', { name: name.value.trim() }, controller.signal)
    if (controller.signal.aborted) return
    emit('added', category); emit('update:modelValue', category.id)
    adding.value = false; name.value = ''
    await nextTick(); categorySelect.value?.focus()
  } catch (cause) { if (!controller.signal.aborted) error.value = cause instanceof Error ? cause.message : '카테고리를 추가하지 못했습니다.' }
  finally { busy.value = false }
}
</script>

<template>
  <div class="category-input" :class="{ 'is-adding': adding }">
    <form class="category-controls" @submit.prevent="create">
      <select id="post-category" ref="categorySelect" aria-label="카테고리" :disabled="busy" :value="modelValue ?? ''" @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null)"><option value="">카테고리</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select>
      <input v-if="adding" id="new-category-name" ref="nameInput" v-model="name" aria-label="카테고리 이름" required maxlength="100" :disabled="busy" @keydown.esc.prevent="cancel" />
      <button v-if="!adding" class="category-add" type="button" aria-label="카테고리 추가" title="카테고리 추가" aria-expanded="false" @click="open">+</button>
      <button v-else class="category-add category-save" type="submit" :disabled="busy || !name.trim()">{{ busy ? '저장 중…' : '추가' }}</button>
    </form>
    <p v-if="error" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.category-input { min-width: 0; width: 280px; max-width: 100%; }
.category-input.is-adding { width: 440px; }
.category-controls { display: flex; align-items: stretch; gap: 8px; }
.category-controls select, .category-controls input, .category-controls .category-add { height: 40px; }
.category-controls input { min-width: 0; width: 0; flex: 1; padding: 8px 10px; border: 1px solid var(--md-border, #d4d4d4); border-radius: 5px; font: inherit; background: var(--md-panel, white); color: var(--md-text, #171717); }
.category-controls .category-add { flex: 0 0 40px; padding: 0; font-size: 24px; line-height: 1; color: #fff; background: #1e4434; border-color: #1e4434; }
.category-controls .category-save { flex-basis: auto; min-width: 52px; padding-inline: 10px; font-size: 13px; white-space: nowrap; }
.category-controls .category-add:hover:not(:disabled) { background: #2e5b47; border-color: #2e5b47; }
select { min-width: 0; max-width: 100%; flex: 1; padding: 10px; font: inherit; background: var(--md-panel, white); color: var(--md-text, #171717); border: 1px solid var(--md-border, #d4d4d4); border-radius: 5px; }
button { font-size: 12px; }
</style>
