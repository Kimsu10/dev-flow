<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import { api } from '../../auth'
import type { Category } from '../../types/blog'
defineProps<{ modelValue: number | null; categories: Category[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: number | null]; added: [category: Category] }>()
const adding = ref(false), name = ref(''), busy = ref(false), error = ref('')
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
  } catch (cause) { if (!controller.signal.aborted) error.value = cause instanceof Error ? cause.message : '카테고리를 추가하지 못했습니다.' }
  finally { busy.value = false }
}
</script>

<template>
  <div class="category-input">
    <label for="post-category">카테고리</label>
    <div class="category-controls"><select id="post-category" :value="modelValue ?? ''" @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null)"><option value="">미분류</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select><button type="button" :aria-expanded="adding" :disabled="busy" @click="adding = !adding">+ 카테고리 추가</button></div>
    <form v-if="adding" class="category-create" @submit.prevent="create">
      <label for="new-category-name">새 카테고리 이름<input id="new-category-name" v-model="name" required maxlength="100" :disabled="busy" /></label>
      <button type="submit" :disabled="busy || !name.trim()">{{ busy ? '저장 중…' : '저장 후 선택' }}</button>
    </form>
    <p v-if="error" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.category-controls { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
select { min-width: 0; max-width: 100%; flex: 1; padding: 10px; font: inherit; background: var(--md-panel, white); color: var(--md-text, #171717); border: 1px solid var(--md-border, #d4d4d4); border-radius: 5px; }
.category-create { display: grid; gap: 8px; margin-top: 12px; }
.category-create input { display: block; width: 100%; box-sizing: border-box; margin-top: 8px; padding: 10px; font: inherit; }
button { font-size: 12px; }
</style>
