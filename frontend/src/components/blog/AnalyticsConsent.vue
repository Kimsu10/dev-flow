<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { auth } from '../../auth'
import { analyticsConsent, consentToAnalytics } from '../../analytics/tracking'

defineProps<{ showSettings?: boolean }>()

const eligible = computed(() => auth.ready && (!auth.user || auth.user.role === 'USER'))
const editing = ref(false)
const dialog = ref<HTMLDialogElement | null>(null)

watch(dialog, element => element?.showModal?.(), { flush: 'post' })

function closeDialog(value?: 'yes' | 'no') {
  dialog.value?.close?.()
  if (value) consentToAnalytics(value)
  editing.value = false
}
</script>

<template>
  <button v-if="showSettings && eligible && analyticsConsent !== null" class="analytics-settings"
    type="button" aria-haspopup="dialog" @click="editing = true">방문 통계 설정</button>
  <dialog v-if="eligible && (analyticsConsent === null || editing)" ref="dialog" class="analytics-consent"
    aria-labelledby="analytics-consent-title" aria-describedby="analytics-consent-description"
    @cancel.prevent="closeDialog(analyticsConsent === null ? 'no' : undefined)">
    <h2 id="analytics-consent-title">방문 통계 설정</h2>
    <p id="analytics-consent-description">블로그 개선을 위해 익명 방문 통계를 수집할까요? 통계용 쿠키를 사용하며 IP 주소는 저장하지 않습니다. 거부해도 모든 글을 읽을 수 있습니다.</p>
    <div class="analytics-consent-actions">
      <button type="button" autofocus @click="closeDialog('no')">거부</button>
      <button type="button" @click="closeDialog('yes')">통계 수집 동의</button>
    </div>
  </dialog>
</template>

<style scoped>
.analytics-consent { width: min(440px, calc(100% - 32px)); max-height: calc(100% - 32px); padding: 24px; border: 1px solid #dedede; border-radius: 10px; background: #fff; color: #171717; }
.analytics-consent::backdrop { background: rgb(0 0 0 / 45%); }
.analytics-consent h2 { margin-top: 0; }
.analytics-consent-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.analytics-settings { min-height: 36px; padding: 6px 0; border: 0; background: transparent; color: #525252; }
.analytics-settings:hover { text-decoration: underline; }
</style>
