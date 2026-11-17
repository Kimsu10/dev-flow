<script setup lang="ts">
import { computed, ref, watch } from 'vue'
const props = defineProps<{ src: string | null; eager?: boolean }>()
const failed = ref(false)
const fallback = '/images/post-placeholder.svg'
watch(() => props.src, () => { failed.value = false })
const source = computed(() => {
  if (!props.src || failed.value) return fallback
  try {
    const url = new URL(props.src, window.location.origin)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : fallback
  } catch { return fallback }
})
</script>

<template>
  <img class="post-thumbnail" :src="source" alt="" width="720" height="480" :loading="eager ? 'eager' : 'lazy'" decoding="async" @error="failed = true">
</template>
